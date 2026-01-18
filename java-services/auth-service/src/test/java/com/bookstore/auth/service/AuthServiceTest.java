package com.bookstore.auth.service;

import com.bookstore.auth.domain.Session;
import com.bookstore.auth.domain.User;
import com.bookstore.auth.repository.SessionRepository;
import com.bookstore.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser();
    }

    private User createTestUser() {
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setCognitoSub("cognito-sub-123");
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void testAuthenticate_Success() {
        // Arrange
        String email = "test@example.com";
        String password = "password123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(User.class), eq(correlationId))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class), eq(correlationId))).thenReturn("refresh-token");

        // Act
        AuthService.AuthResult result = authService.authenticate(email, password, ipAddress, userAgent, correlationId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        assertEquals(testUser, result.getUser());

        verify(auditService).logSuccessfulLogin(testUser.getId(), ipAddress, userAgent, correlationId);
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void testAuthenticate_UserNotFound() {
        // Arrange
        String email = "nonexistent@example.com";
        String password = "password123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act
        AuthService.AuthResult result = authService.authenticate(email, password, ipAddress, userAgent, correlationId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Invalid credentials", result.getErrorMessage());

        verify(auditService).logFailedLogin(email, "User not found", ipAddress, userAgent, correlationId);
    }

    @Test
    void testAuthenticate_AccountInactive() {
        // Arrange
        testUser.setStatus(User.UserStatus.INACTIVE);
        String email = "test@example.com";
        String password = "password123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // Act
        AuthService.AuthResult result = authService.authenticate(email, password, ipAddress, userAgent, correlationId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Account is inactive", result.getErrorMessage());

        verify(auditService).logFailedLogin(email, "Account inactive", ipAddress, userAgent, correlationId);
    }

    @Test
    void testAuthenticate_AccountLocked() {
        // Arrange
        testUser.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(30));
        String email = "test@example.com";
        String password = "password123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // Act
        AuthService.AuthResult result = authService.authenticate(email, password, ipAddress, userAgent, correlationId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Account is temporarily locked", result.getErrorMessage());

        verify(auditService).logFailedLogin(email, "Account locked", ipAddress, userAgent, correlationId);
    }

    @Test
    void testValidateToken_ValidToken() {
        // Arrange
        String token = "valid-jwt-token";
        String correlationId = "test-correlation-id";

        String tokenHash = "token-hash";
        Session session = new Session(testUser, tokenHash, java.time.LocalDateTime.now().plusHours(1));

        when(jwtService.validateTokenFormat(token)).thenReturn(true);
        when(jwtService.hashToken(token)).thenReturn(tokenHash);
        when(sessionRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(session));

        // Act
        AuthService.TokenValidationResult result = authService.validateToken(token, correlationId);

        // Assert
        assertTrue(result.isValid());
        assertEquals(testUser, result.getUser());
    }

    @Test
    void testValidateToken_InvalidToken() {
        // Arrange
        String token = "invalid-jwt-token";
        String correlationId = "test-correlation-id";

        when(jwtService.validateTokenFormat(token)).thenReturn(false);

        // Act
        AuthService.TokenValidationResult result = authService.validateToken(token, correlationId);

        // Assert
        assertFalse(result.isValid());
        assertEquals("Invalid token format or signature", result.getErrorMessage());
    }

    @Test
    void testRefreshToken_Success() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        String correlationId = "test-correlation-id";

        when(jwtService.validateTokenFormat(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(User.class), eq(correlationId))).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(any(User.class), eq(correlationId))).thenReturn("new-refresh-token");

        // Act
        AuthService.AuthResult result = authService.refreshToken(refreshToken, correlationId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("new-access-token", result.getAccessToken());
        assertEquals("new-refresh-token", result.getRefreshToken());
    }

    @Test
    void testRefreshToken_InvalidToken() {
        // Arrange
        String refreshToken = "invalid-refresh-token";
        String correlationId = "test-correlation-id";

        when(jwtService.validateTokenFormat(refreshToken)).thenReturn(false);

        // Act
        AuthService.AuthResult result = authService.refreshToken(refreshToken, correlationId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Invalid refresh token", result.getErrorMessage());
    }

    @Test
    void testRegisterUser_Success() {
        // Arrange
        String email = "newuser@example.com";
        String password = "password123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        AuthService.UserRegistrationResult result = authService.registerUser(email, password, ipAddress, userAgent, correlationId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(testUser, result.getUser());

        verify(userRepository).save(any(User.class));
        verify(auditService).logUserRegistration(any(java.util.UUID.class), eq(email), eq(ipAddress), eq(userAgent), eq(correlationId));
    }

    @Test
    void testRegisterUser_EmailExists() {
        // Arrange
        String email = "existing@example.com";
        String password = "password123";
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(userRepository.existsByEmail(email)).thenReturn(true);

        // Act
        AuthService.UserRegistrationResult result = authService.registerUser(email, password, ipAddress, userAgent, correlationId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Email already registered", result.getErrorMessage());

        verify(auditService).logRegistrationFailure(email, "Email already exists", ipAddress, userAgent, correlationId);
    }

    @Test
    void testLogout() {
        // Arrange
        java.util.UUID userId = testUser.getId();
        String correlationId = "test-correlation-id";

        // Act
        authService.logout(userId, correlationId);

        // Assert
        verify(sessionRepository).revokeAllSessionsByUserId(userId);
        verify(auditService).logLogout(userId, correlationId);
    }
}