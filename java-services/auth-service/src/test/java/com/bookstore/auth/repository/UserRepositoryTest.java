package com.bookstore.auth.repository;

import com.bookstore.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByEmail() {
        // Arrange
        User user = createTestUser("test@example.com");
        entityManager.persist(user);
        entityManager.flush();

        // Act
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByEmail_NotFound() {
        // Act
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByCognitoSub() {
        // Arrange
        User user = createTestUser("test@example.com");
        user.setCognitoSub("cognito-sub-123");
        entityManager.persist(user);
        entityManager.flush();

        // Act
        Optional<User> found = userRepository.findByCognitoSub("cognito-sub-123");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("cognito-sub-123", found.get().getCognitoSub());
    }

    @Test
    void testExistsByEmail() {
        // Arrange
        User user = createTestUser("test@example.com");
        entityManager.persist(user);
        entityManager.flush();

        // Act & Assert
        assertTrue(userRepository.existsByEmail("test@example.com"));
        assertFalse(userRepository.existsByEmail("nonexistent@example.com"));
    }

    @Test
    void testExistsByCognitoSub() {
        // Arrange
        User user = createTestUser("test@example.com");
        user.setCognitoSub("cognito-sub-123");
        entityManager.persist(user);
        entityManager.flush();

        // Act & Assert
        assertTrue(userRepository.existsByCognitoSub("cognito-sub-123"));
        assertFalse(userRepository.existsByCognitoSub("nonexistent-sub"));
    }

    @Test
    void testFindByStatus() {
        // Arrange
        User activeUser = createTestUser("active@example.com");
        activeUser.setStatus(User.UserStatus.ACTIVE);

        User inactiveUser = createTestUser("inactive@example.com");
        inactiveUser.setStatus(User.UserStatus.INACTIVE);

        entityManager.persist(activeUser);
        entityManager.persist(inactiveUser);
        entityManager.flush();

        // Act
        Iterable<User> activeUsers = userRepository.findByStatus(User.UserStatus.ACTIVE);

        // Assert
        assertEquals(1, ((java.util.List<User>) activeUsers).size());
        assertEquals("active@example.com", ((java.util.List<User>) activeUsers).get(0).getEmail());
    }

    @Test
    void testUpdateStatus() {
        // Arrange
        User user = createTestUser("test@example.com");
        entityManager.persist(user);
        entityManager.flush();

        // Act
        int updated = userRepository.updateStatus(user.getId(), User.UserStatus.INACTIVE);

        // Assert
        assertEquals(1, updated);
        entityManager.refresh(user);
        assertEquals(User.UserStatus.INACTIVE, user.getStatus());
    }

    @Test
    void testUpdateEmailVerified() {
        // Arrange
        User user = createTestUser("test@example.com");
        user.setEmailVerified(false);
        entityManager.persist(user);
        entityManager.flush();

        // Act
        int updated = userRepository.updateEmailVerified(user.getId(), true);

        // Assert
        assertEquals(1, updated);
        entityManager.refresh(user);
        assertTrue(user.getEmailVerified());
    }

    @Test
    void testUpdateLastLogin() {
        // Arrange
        User user = createTestUser("test@example.com");
        entityManager.persist(user);
        entityManager.flush();

        LocalDateTime loginTime = LocalDateTime.now();

        // Act
        int updated = userRepository.updateLastLogin(user.getId(), loginTime);

        // Assert
        assertEquals(1, updated);
        entityManager.refresh(user);
        // Compare with some tolerance for database precision
        assertTrue(user.getLastLoginAt().isAfter(loginTime.minusSeconds(1)));
        assertTrue(user.getLastLoginAt().isBefore(loginTime.plusSeconds(1)));
    }

    @Test
    void testIncrementLoginAttempts() {
        // Arrange
        User user = createTestUser("test@example.com");
        user.setLoginAttempts(4); // Set to 4 so that incrementing to 5 triggers lock
        entityManager.persist(user);
        entityManager.flush();

        LocalDateTime lockTime = LocalDateTime.now().plusMinutes(30);

        // Act
        int updated = userRepository.incrementLoginAttempts(user.getId(), lockTime);

        // Assert
        assertEquals(1, updated);
        entityManager.refresh(user);
        assertEquals(5, user.getLoginAttempts());
        // Compare with some tolerance for database precision
        assertTrue(user.getLockedUntil().isAfter(lockTime.minusSeconds(1)));
        assertTrue(user.getLockedUntil().isBefore(lockTime.plusSeconds(1)));
    }

    @Test
    void testResetLoginAttempts() {
        // Arrange
        User user = createTestUser("test@example.com");
        user.setLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        entityManager.persist(user);
        entityManager.flush();

        // Act
        int updated = userRepository.resetLoginAttempts(user.getId());

        // Assert
        assertEquals(1, updated);
        entityManager.refresh(user);
        assertEquals(0, user.getLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void testFindExpiredLockouts() {
        // Arrange
        User lockedUser = createTestUser("locked@example.com");
        lockedUser.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // Expired
        entityManager.persist(lockedUser);

        User activeUser = createTestUser("active@example.com");
        activeUser.setLockedUntil(LocalDateTime.now().plusMinutes(30)); // Not expired
        entityManager.persist(activeUser);

        entityManager.flush();

        // Act
        Iterable<User> expiredLockouts = userRepository.findExpiredLockouts(LocalDateTime.now());

        // Assert
        assertEquals(1, ((java.util.List<User>) expiredLockouts).size());
        assertEquals("locked@example.com", ((java.util.List<User>) expiredLockouts).get(0).getEmail());
    }

    @Test
    void testUnlockExpiredAccounts() {
        // Arrange
        User lockedUser = createTestUser("locked@example.com");
        lockedUser.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        lockedUser.setLoginAttempts(5);
        entityManager.persist(lockedUser);
        entityManager.flush();

        // Act
        int updated = userRepository.unlockExpiredAccounts(LocalDateTime.now());

        // Assert
        assertEquals(1, updated);
        entityManager.refresh(lockedUser);
        assertNull(lockedUser.getLockedUntil());
        assertEquals(0, lockedUser.getLoginAttempts());
    }

    private User createTestUser(String email) {
        User user = new User();
        // Don't set ID manually - let JPA generate it
        user.setEmail(email);
        user.setCognitoSub("cognito-sub-" + java.util.UUID.randomUUID());
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }
}