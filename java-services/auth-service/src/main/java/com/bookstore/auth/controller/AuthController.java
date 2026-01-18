package com.bookstore.auth.controller;

import com.bookstore.auth.service.AuthService;
import com.bookstore.auth.service.JwtService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Authentication operations.
 * Handles login, logout, registration, and token management.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Timed(value = "auth.controller", percentiles = {0.5, 0.95, 0.99})
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;

    @Autowired
    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * POST /api/v1/auth/login
     * Authenticate user and return JWT tokens.
     */
    @PostMapping("/login")
    @Timed(value = "auth.login", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Login attempt for email: {}", correlationId, request.getEmail());

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthService.AuthResult result = authService.authenticate(
            request.getEmail(),
            request.getPassword(),
            ipAddress,
            userAgent,
            correlationId
        );

        if (!result.isSuccess()) {
            logger.warn("[{}] Login failed for email: {} - {}", correlationId, request.getEmail(), result.getErrorMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("AUTHENTICATION_FAILED", result.getErrorMessage()));
        }

        logger.info("[{}] Login successful for user: {}", correlationId, result.getUser().getId());

        return ResponseEntity.ok(new AuthResponse(
            result.getAccessToken(),
            result.getRefreshToken(),
            jwtService.extractExpiration(result.getAccessToken()),
            result.getUser()
        ));
    }

    /**
     * POST /api/v1/auth/register
     * Register new user account.
     */
    @PostMapping("/register")
    @Timed(value = "auth.register", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Registration attempt for email: {}", correlationId, request.getEmail());

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthService.UserRegistrationResult result = authService.registerUser(
            request.getEmail(),
            request.getPassword(),
            ipAddress,
            userAgent,
            correlationId
        );

        if (!result.isSuccess()) {
            logger.warn("[{}] Registration failed for email: {} - {}", correlationId, request.getEmail(), result.getErrorMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("REGISTRATION_FAILED", result.getErrorMessage()));
        }

        logger.info("[{}] Registration successful for user: {}", correlationId, result.getUser().getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(result.getUser()));
    }

    /**
     * POST /api/v1/auth/refresh
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    @Timed(value = "auth.refresh", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Token refresh attempt", correlationId);

        AuthService.AuthResult result = authService.refreshToken(request.getRefreshToken(), correlationId);

        if (!result.isSuccess()) {
            logger.warn("[{}] Token refresh failed: {}", correlationId, result.getErrorMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("REFRESH_FAILED", result.getErrorMessage()));
        }

        logger.info("[{}] Token refresh successful for user: {}", correlationId, result.getUser().getId());

        return ResponseEntity.ok(new AuthResponse(
            result.getAccessToken(),
            result.getRefreshToken(),
            jwtService.extractExpiration(result.getAccessToken()),
            result.getUser()
        ));
    }

    /**
     * POST /api/v1/auth/logout
     * Logout user from all sessions.
     */
    @PostMapping("/logout")
    @Timed(value = "auth.logout", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        String correlationId = getCorrelationId();

        try {
            String token = extractTokenFromHeader(authHeader);
            UUID userId = jwtService.extractUserId(token);

            authService.logout(userId, correlationId);

            logger.info("[{}] Logout successful for user: {}", correlationId, userId);

            return ResponseEntity.ok(new SuccessResponse("Logged out successfully"));
        } catch (Exception e) {
            logger.warn("[{}] Logout failed: {}", correlationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("LOGOUT_FAILED", "Invalid token"));
        }
    }

    /**
     * POST /api/v1/auth/validate
     * Validate JWT token and return user information.
     */
    @PostMapping("/validate")
    @Timed(value = "auth.validate", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> validate(@Valid @RequestBody TokenValidationRequest request) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Token validation request", correlationId);

        AuthService.TokenValidationResult result = authService.validateToken(request.getToken(), correlationId);

        if (!result.isValid()) {
            logger.warn("[{}] Token validation failed: {}", correlationId, result.getErrorMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("TOKEN_INVALID", result.getErrorMessage()));
        }

        logger.debug("[{}] Token validation successful for user: {}", correlationId, result.getUser().getId());

        return ResponseEntity.ok(new TokenValidationResponse(result.getUser()));
    }

    // Helper methods

    private String getCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }
        return correlationId;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }

    // Request/Response DTOs

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RegisterRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class TokenValidationRequest {
        private String token;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private java.time.LocalDateTime expiresAt;
        private UserResponse user;

        public AuthResponse(String accessToken, String refreshToken, java.time.LocalDateTime expiresAt, com.bookstore.auth.domain.User user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
            this.user = new UserResponse(user);
        }

        // Getters
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public java.time.LocalDateTime getExpiresAt() { return expiresAt; }
        public UserResponse getUser() { return user; }
    }

    public static class UserResponse {
        private UUID id;
        private String email;
        private String status;
        private boolean emailVerified;

        public UserResponse(com.bookstore.auth.domain.User user) {
            this.id = user.getId();
            this.email = user.getEmail();
            this.status = user.getStatus().toString();
            this.emailVerified = user.getEmailVerified() != null ? user.getEmailVerified() : false;
        }

        // Getters
        public UUID getId() { return id; }
        public String getEmail() { return email; }
        public String getStatus() { return status; }
        public boolean isEmailVerified() { return emailVerified; }
    }

    public static class TokenValidationResponse {
        private boolean valid;
        private UserResponse user;

        public TokenValidationResponse(com.bookstore.auth.domain.User user) {
            this.valid = true;
            this.user = new UserResponse(user);
        }

        // Getters
        public boolean isValid() { return valid; }
        public UserResponse getUser() { return user; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        // Getters
        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        // Getters
        public String getMessage() { return message; }
    }
}