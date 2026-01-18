package com.bookstore.auth.repository;

import com.bookstore.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by Cognito sub identifier.
     */
    Optional<User> findByCognitoSub(String cognitoSub);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Check if Cognito sub exists.
     */
    boolean existsByCognitoSub(String cognitoSub);

    /**
     * Find users by status.
     */
    Iterable<User> findByStatus(User.UserStatus status);

    /**
     * Update user status.
     */
    @Modifying
    @Query("UPDATE User u SET u.status = :status, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") User.UserStatus status);

    /**
     * Update email verification status.
     */
    @Modifying
    @Query("UPDATE User u SET u.emailVerified = :verified, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int updateEmailVerified(@Param("id") UUID id, @Param("verified") boolean verified);

    /**
     * Update last login timestamp.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int updateLastLogin(@Param("id") UUID id, @Param("loginTime") LocalDateTime loginTime);

    /**
     * Increment login attempts and set lockout if threshold exceeded.
     */
    @Modifying
    @Query("""
        UPDATE User u SET
            u.loginAttempts = u.loginAttempts + 1,
            u.lockedUntil = CASE WHEN u.loginAttempts + 1 >= 5 THEN :lockTime ELSE u.lockedUntil END,
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.id = :id
        """)
    int incrementLoginAttempts(@Param("id") UUID id, @Param("lockTime") LocalDateTime lockTime);

    /**
     * Reset login attempts and clear lockout.
     */
    @Modifying
    @Query("UPDATE User u SET u.loginAttempts = 0, u.lockedUntil = null, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int resetLoginAttempts(@Param("id") UUID id);

    /**
     * Find users with expired lockouts to unlock.
     */
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil < :now")
    Iterable<User> findExpiredLockouts(@Param("now") LocalDateTime now);

    /**
     * Unlock accounts with expired lockouts.
     */
    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = null, u.loginAttempts = 0, u.updatedAt = CURRENT_TIMESTAMP WHERE u.lockedUntil < :now")
    int unlockExpiredAccounts(@Param("now") LocalDateTime now);
}