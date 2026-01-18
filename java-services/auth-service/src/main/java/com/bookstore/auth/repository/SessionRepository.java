package com.bookstore.auth.repository;

import com.bookstore.auth.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Session entity operations.
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * Find session by token hash.
     */
    Optional<Session> findByTokenHash(String tokenHash);

    /**
     * Check if token hash exists and is valid.
     */
    @Query("SELECT COUNT(s) > 0 FROM Session s WHERE s.tokenHash = :tokenHash AND s.revoked = false AND s.expiresAt > :now")
    boolean isValidToken(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /**
     * Find all active sessions for a user.
     */
    @Query("SELECT s FROM Session s WHERE s.user.id = :userId AND s.revoked = false AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    Iterable<Session> findActiveSessionsByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Revoke session by token hash.
     */
    @Modifying
    @Query("UPDATE Session s SET s.revoked = true, s.revokedAt = CURRENT_TIMESTAMP WHERE s.tokenHash = :tokenHash")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Revoke all sessions for a user.
     */
    @Modifying
    @Query("UPDATE Session s SET s.revoked = true, s.revokedAt = CURRENT_TIMESTAMP WHERE s.user.id = :userId AND s.revoked = false")
    int revokeAllSessionsByUserId(@Param("userId") UUID userId);

    /**
     * Clean up expired sessions.
     */
    @Modifying
    @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);

    /**
     * Count active sessions for a user.
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.user.id = :userId AND s.revoked = false AND s.expiresAt > :now")
    long countActiveSessionsByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Find sessions that need cleanup (expired or revoked for too long).
     */
    @Query("SELECT s FROM Session s WHERE (s.expiresAt < :expiredBefore) OR (s.revoked = true AND s.revokedAt < :revokedBefore)")
    Iterable<Session> findSessionsForCleanup(@Param("expiredBefore") LocalDateTime expiredBefore, @Param("revokedBefore") LocalDateTime revokedBefore);
}