package com.bookstore.auth;

import com.bookstore.auth.domain.User;
import com.bookstore.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@Testcontainers
@Transactional
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testCompleteAuthFlow() throws Exception {
        // Step 1: Register a new user
        String registerRequest = """
            {
                "email": "integration-test@example.com",
                "password": "TestPassword123!"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("integration-test@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify user was created in database
        User createdUser = userRepository.findByEmail("integration-test@example.com").orElse(null);
        assert createdUser != null;
        assert createdUser.getEmail().equals("integration-test@example.com");
        assert createdUser.getStatus() == User.UserStatus.ACTIVE;

        // Step 2: Try to login (simulated - in real implementation would need Cognito)
        String loginRequest = """
            {
                "email": "integration-test@example.com",
                "password": "TestPassword123!"
            }
            """;

        // This would normally return tokens, but our mock authentication
        // will return success for valid format passwords
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("integration-test@example.com"));
    }

    @Test
    void testRegisterDuplicateEmail() throws Exception {
        // First registration
        String registerRequest = """
            {
                "email": "duplicate@example.com",
                "password": "TestPassword123!"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest))
                .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("REGISTRATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void testTokenValidation() throws Exception {
        // Register user
        String registerRequest = """
            {
                "email": "token-test@example.com",
                "password": "TestPassword123!"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest))
                .andExpect(status().isCreated());

        // Login to get tokens
        String loginRequest = """
            {
                "email": "token-test@example.com",
                "password": "TestPassword123!"
            }
            """;

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Extract access token from response (simplified)
        // In a real test, you'd parse the JSON response

        // Test token validation (would need actual token)
        String validateRequest = """
            {
                "token": "invalid-test-token"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validateRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOKEN_INVALID"));
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testInfoEndpoint() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("auth-service"));
    }
}