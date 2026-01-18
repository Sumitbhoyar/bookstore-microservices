package com.bookstore.auth.service;

import com.bookstore.auth.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-for-jwt-tokens-change-in-production-very-long-key", 3600000L);
        testUser = createTestUser();
    }

    private User createTestUser() {
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");

        assertNotNull(token);
        assertTrue(token.length() > 0);
        assertTrue(token.startsWith("eyJ")); // JWT tokens start with "eyJ"
    }

    @Test
    void testGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser, "test-correlation-id");

        assertNotNull(refreshToken);
        assertTrue(refreshToken.length() > 0);
        assertTrue(refreshToken.startsWith("eyJ"));
    }

    @Test
    void testExtractUsername() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        String username = jwtService.extractUsername(token);

        assertEquals(testUser.getEmail(), username);
    }

    @Test
    void testExtractUserId() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        java.util.UUID userId = jwtService.extractUserId(token);

        assertEquals(testUser.getId(), userId);
    }

    @Test
    void testExtractExpiration() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        java.time.LocalDateTime expiration = jwtService.extractExpiration(token);

        assertNotNull(expiration);
        // Should expire in about 1 hour (3600000ms) from now
        java.time.LocalDateTime expectedExpiration = java.time.LocalDateTime.now().plusHours(1);
        assertTrue(expiration.isAfter(java.time.LocalDateTime.now()));
        assertTrue(expiration.isBefore(expectedExpiration.plusMinutes(1)));
    }

    @Test
    void testValidateToken_ValidToken() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        boolean isValid = jwtService.validateToken(token, testUser);

        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidUser() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");

        User differentUser = new User();
        differentUser.setId(java.util.UUID.randomUUID());
        differentUser.setEmail("different@example.com");

        boolean isValid = jwtService.validateToken(token, differentUser);
        assertFalse(isValid);
    }

    @Test
    void testValidateTokenFormat_ValidToken() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        boolean isValid = jwtService.validateTokenFormat(token);

        assertTrue(isValid);
    }

    @Test
    void testValidateTokenFormat_InvalidToken() {
        String invalidToken = "invalid.jwt.token";
        boolean isValid = jwtService.validateTokenFormat(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void testIsTokenExpired_ValidToken() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        boolean isExpired = jwtService.isTokenExpired(token);

        assertFalse(isExpired);
    }

    @Test
    void testHashToken() {
        String token = jwtService.generateToken(testUser, "test-correlation-id");
        String hash1 = jwtService.hashToken(token);
        String hash2 = jwtService.hashToken(token);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1, hash2); // Same token should produce same hash
        assertTrue(hash1.length() > 0);
    }
}