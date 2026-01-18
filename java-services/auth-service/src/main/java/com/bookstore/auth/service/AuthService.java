package com.bookstore.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.auth.domain.Session;
import com.bookstore.auth.domain.User;
import com.bookstore.auth.repository.SessionRepository;
import com.bookstore.auth.repository.UserRepository;

/**
 * Authentication Service handling login, logout, token management, and user operations.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Authenticate user with email and password.
     * This method integrates with AWS Cognito for authentication.
     */
    public AuthResult authenticate(String email, String password, String ipAddress,
                                 String userAgent, String correlationId) {
        logger.info("[{}] Attempting authentication for email: {}", correlationId, email);

        // Find user
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            auditService.logFailedLogin(email, "User not found", ipAddress, userAgent, correlationId);
            return AuthResult.failure("Invalid credentials");
        }

        User user = userOpt.get();

        // Check if account is active
        if (!user.isActive()) {
            auditService.logFailedLogin(email, "Account inactive", ipAddress, userAgent, correlationId);
            return AuthResult.failure("Account is inactive");
        }

        // Check if account is locked
        if (user.isLocked()) {
            auditService.logFailedLogin(email, "Account locked", ipAddress, userAgent, correlationId);
            return AuthResult.failure("Account is temporarily locked");
        }

        // In a real implementation, this would call AWS Cognito
        // For now, we'll simulate authentication
        boolean authenticated = simulateCognitoAuthentication(email, password);

        if (!authenticated) {
            handleFailedLogin(user, ipAddress, userAgent, correlationId);
            return AuthResult.failure("Invalid credentials");
        }

        // Successful authentication
        handleSuccessfulLogin(user, ipAddress, userAgent, correlationId);
        return createAuthResult(user, correlationId);
    }

    /**
     * Validate JWT token and return user information.
     */
    public TokenValidationResult validateToken(String token, String correlationId) {
        logger.debug("[{}] Validating token", correlationId);

        if (!jwtService.validateTokenFormat(token)) {
            return TokenValidationResult.invalid("Invalid token format or signature");
        }

        String tokenHash = jwtService.hashToken(token);
        Optional<Session> sessionOpt = sessionRepository.findByTokenHash(tokenHash);

        if (sessionOpt.isEmpty()) {
            return TokenValidationResult.invalid("Session not found");
        }

        Session session = sessionOpt.get();
        if (!session.isValid()) {
            return TokenValidationResult.invalid("Session expired or revoked");
        }

        return TokenValidationResult.valid(session.getUser());
    }

    /**
     * Refresh access token using refresh token.
     */
    public AuthResult refreshToken(String refreshToken, String correlationId) {
        logger.info("[{}] Refreshing token", correlationId);

        // Validate refresh token format
        if (!jwtService.validateTokenFormat(refreshToken)) {
            return AuthResult.failure("Invalid refresh token");
        }

        // Extract user ID from refresh token
        UUID userId = jwtService.extractUserId(refreshToken);
        if (userId == null) {
            return AuthResult.failure("Invalid refresh token");
        }

        // Find user
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return AuthResult.failure("User not found");
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return AuthResult.failure("Account is inactive");
        }

        // Invalidate old session and create new one
        revokeUserSessions(user.getId(), correlationId);
        return createAuthResult(user, correlationId);
    }

    /**
     * Logout user by revoking all their sessions.
     */
    public void logout(UUID userId, String correlationId) {
        logger.info("[{}] Logging out user: {}", correlationId, userId);
        revokeUserSessions(userId, correlationId);
        auditService.logLogout(userId, correlationId);
    }

    /**
     * Logout from specific session.
     */
    public void logoutFromSession(String token, String correlationId) {
        String tokenHash = jwtService.hashToken(token);
        sessionRepository.revokeByTokenHash(tokenHash);
        logger.info("[{}] Revoked specific session", correlationId);
    }

    /**
     * Register new user.
     * This would integrate with AWS Cognito user pools.
     */
    public UserRegistrationResult registerUser(String email, String password,
                                             String ipAddress, String userAgent,
                                             String correlationId) {
        logger.info("[{}] Registering new user: {}", correlationId, email);

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            auditService.logRegistrationFailure(email, "Email already exists", ipAddress, userAgent, correlationId);
            return UserRegistrationResult.failure("Email already registered");
        }

        // Create user in Cognito (simulated)
        String cognitoSub = simulateCognitoUserCreation(email, password);

        // Create user record
        User user = new User(email, cognitoSub);
        user.setEmailVerified(false);
        user = userRepository.save(user);

        auditService.logUserRegistration(user.getId(), email, ipAddress, userAgent, correlationId);

        return UserRegistrationResult.success(user);
    }

    // Private helper methods

    private void handleFailedLogin(User user, String ipAddress, String userAgent, String correlationId) {
        user.recordLoginAttempt();
        if (user.getLoginAttempts() >= 5) {
            user.lockAccount();
        }
        userRepository.save(user);
        auditService.logFailedLogin(user.getEmail(), "Invalid password", ipAddress, userAgent, correlationId);
    }

    private void handleSuccessfulLogin(User user, String ipAddress, String userAgent, String correlationId) {
        user.recordSuccessfulLogin();
        userRepository.save(user);
        auditService.logSuccessfulLogin(user.getId(), ipAddress, userAgent, correlationId);
    }

    private AuthResult createAuthResult(User user, String correlationId) {
        String accessToken = jwtService.generateToken(user, correlationId);
        String refreshToken = jwtService.generateRefreshToken(user, correlationId);

        // Store session
        String tokenHash = jwtService.hashToken(accessToken);
        LocalDateTime expiresAt = jwtService.extractExpiration(accessToken);

        Session session = new Session(user, tokenHash, expiresAt);
        sessionRepository.save(session);

        return AuthResult.success(accessToken, refreshToken, user);
    }

    private void revokeUserSessions(UUID userId, String correlationId) {
        int revokedCount = sessionRepository.revokeAllSessionsByUserId(userId);
        logger.info("[{}] Revoked {} sessions for user: {}", correlationId, revokedCount, userId);
    }

    // Simulated external service calls (would be real AWS SDK calls)
    private boolean simulateCognitoAuthentication(String email, String password) {
        // Simulate authentication logic
        return password != null && password.length() >= 8;
    }

    private String simulateCognitoUserCreation(String email, String password) {
        // Simulate Cognito sub generation
        return "cognito-sub-" + UUID.randomUUID().toString();
    }

    // Result classes

    public static class AuthResult {
        private final boolean success;
        private final String accessToken;
        private final String refreshToken;
        private final User user;
        private final String errorMessage;

        private AuthResult(boolean success, String accessToken, String refreshToken, User user, String errorMessage) {
            this.success = success;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static AuthResult success(String accessToken, String refreshToken, User user) {
            return new AuthResult(true, accessToken, refreshToken, user, null);
        }

        public static AuthResult failure(String errorMessage) {
            return new AuthResult(false, null, null, null, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public User getUser() { return user; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class TokenValidationResult {
        private final boolean valid;
        private final User user;
        private final String errorMessage;

        private TokenValidationResult(boolean valid, User user, String errorMessage) {
            this.valid = valid;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static TokenValidationResult valid(User user) {
            return new TokenValidationResult(true, user, null);
        }

        public static TokenValidationResult invalid(String errorMessage) {
            return new TokenValidationResult(false, null, errorMessage);
        }

        // Getters
        public boolean isValid() { return valid; }
        public User getUser() { return user; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class UserRegistrationResult {
        private final boolean success;
        private final User user;
        private final String errorMessage;

        private UserRegistrationResult(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static UserRegistrationResult success(User user) {
            return new UserRegistrationResult(true, user, null);
        }

        public static UserRegistrationResult failure(String errorMessage) {
            return new UserRegistrationResult(false, null, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public User getUser() { return user; }
        public String getErrorMessage() { return errorMessage; }
    }
}