package com.bookstore.auth.controller;

import com.bookstore.auth.domain.User;
import com.bookstore.auth.service.AuthService;
import com.bookstore.auth.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthController authController;

    @Test
    void testLogin_Success() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        User testUser = createTestUser();
        AuthService.AuthResult authResult = AuthService.AuthResult.success("access-token", "refresh-token", testUser);

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(authService.authenticate(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(authResult);
        when(jwtService.extractExpiration(anyString())).thenReturn(LocalDateTime.now().plusHours(1));

        // Act
        ResponseEntity<?> response = authController.login(loginRequest, httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // Additional assertions can be added for the response body
    }

    @Test
    void testLogin_Failure() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("wrongpassword");

        AuthService.AuthResult authResult = AuthService.AuthResult.failure("Invalid credentials");

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(authService.authenticate(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(authResult);

        // Act
        ResponseEntity<?> response = authController.login(loginRequest, httpRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testRegister_Success() {
        // Arrange
        AuthController.RegisterRequest registerRequest = new AuthController.RegisterRequest();
        registerRequest.setEmail("newuser@example.com");
        registerRequest.setPassword("password123");

        User testUser = createTestUser();
        AuthService.UserRegistrationResult registrationResult = AuthService.UserRegistrationResult.success(testUser);

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(authService.registerUser(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(registrationResult);

        // Act
        ResponseEntity<?> response = authController.register(registerRequest, httpRequest);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testLogout() {
        // Arrange
        User testUser = createTestUser();
        when(jwtService.extractUserId(anyString())).thenReturn(testUser.getId());

        // Act
        ResponseEntity<?> response = authController.logout("Bearer jwt-token");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    private User createTestUser() {
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }
}