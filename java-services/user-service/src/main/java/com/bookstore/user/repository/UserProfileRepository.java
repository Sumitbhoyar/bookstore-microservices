package com.bookstore.user.repository;

import com.bookstore.user.domain.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserProfile entity operations.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * Find user profile by user ID (from auth service).
     */
    Optional<UserProfile> findByUserId(UUID userId);

    /**
     * Find user profile by email.
     */
    Optional<UserProfile> findByEmail(String email);

    /**
     * Check if user ID exists.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Find user profiles with pagination.
     */
    Page<UserProfile> findAll(Pageable pageable);

    /**
     * Find user profiles created within date range.
     */
    @Query("SELECT u FROM UserProfile u WHERE u.createdAt BETWEEN :startDate AND :endDate ORDER BY u.createdAt DESC")
    Page<UserProfile> findByCreatedDateRange(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate,
                                           Pageable pageable);

    /**
     * Update user profile information.
     */
    @Modifying
    @Query("""
        UPDATE UserProfile u SET
            u.firstName = :firstName,
            u.lastName = :lastName,
            u.displayName = :displayName,
            u.phone = :phone,
            u.dateOfBirth = :dateOfBirth,
            u.gender = :gender,
            u.bio = :bio,
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.id = :id
        """)
    int updateProfile(@Param("id") UUID id,
                     @Param("firstName") String firstName,
                     @Param("lastName") String lastName,
                     @Param("displayName") String displayName,
                     @Param("phone") String phone,
                     @Param("dateOfBirth") java.time.LocalDate dateOfBirth,
                     @Param("gender") UserProfile.Gender gender,
                     @Param("bio") String bio);

    /**
     * Update user avatar.
     */
    @Modifying
    @Query("UPDATE UserProfile u SET u.avatarUrl = :avatarUrl, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int updateAvatar(@Param("id") UUID id, @Param("avatarUrl") String avatarUrl);

    /**
     * Update user email (when changed in auth service).
     */
    @Modifying
    @Query("UPDATE UserProfile u SET u.email = :email, u.updatedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    int updateEmail(@Param("userId") UUID userId, @Param("email") String email);

    /**
     * Search user profiles by name or email.
     */
    @Query("""
        SELECT u FROM UserProfile u WHERE
            LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY u.createdAt DESC
        """)
    Page<UserProfile> searchByNameOrEmail(@Param("query") String query, Pageable pageable);

    /**
     * Count total user profiles.
     */
    @Query("SELECT COUNT(u) FROM UserProfile u")
    long countTotalUsers();

    /**
     * Count users created in date range.
     */
    @Query("SELECT COUNT(u) FROM UserProfile u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    long countUsersCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    /**
     * Find users with incomplete profiles.
     */
    @Query("SELECT u FROM UserProfile u WHERE u.firstName IS NULL OR u.lastName IS NULL OR u.phone IS NULL")
    Page<UserProfile> findIncompleteProfiles(Pageable pageable);
}