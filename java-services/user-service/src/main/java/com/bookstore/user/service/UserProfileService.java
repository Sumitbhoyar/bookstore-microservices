package com.bookstore.user.service;

import com.bookstore.user.domain.UserProfile;
import com.bookstore.user.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user profiles.
 */
@Service
@Transactional
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository userProfileRepository;
    private final AuditService auditService;

    @Autowired
    public UserProfileService(UserProfileRepository userProfileRepository, AuditService auditService) {
        this.userProfileRepository = userProfileRepository;
        this.auditService = auditService;
    }

    /**
     * Create a new user profile when user registers.
     */
    public UserProfile createUserProfile(UUID userId, String email, String correlationId) {
        logger.info("[{}] Creating user profile for user: {}", correlationId, userId);

        if (userProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User profile already exists for user: " + userId);
        }

        UserProfile profile = new UserProfile(userId, email);
        profile = userProfileRepository.save(profile);

        auditService.logUserProfileCreated(userId, email, correlationId);

        logger.info("[{}] Created user profile: {}", correlationId, profile.getId());
        return profile;
    }

    /**
     * Get user profile by user ID.
     */
    public Optional<UserProfile> getUserProfileByUserId(UUID userId, String correlationId) {
        logger.debug("[{}] Getting user profile for user: {}", correlationId, userId);
        return userProfileRepository.findByUserId(userId);
    }

    /**
     * Get user profile by profile ID.
     */
    public Optional<UserProfile> getUserProfileById(UUID profileId, String correlationId) {
        logger.debug("[{}] Getting user profile: {}", correlationId, profileId);
        return userProfileRepository.findById(profileId);
    }

    /**
     * Update user profile information.
     */
    public UserProfile updateUserProfile(UUID profileId, UserProfile updatedProfile, String correlationId) {
        logger.info("[{}] Updating user profile: {}", correlationId, profileId);

        UserProfile existingProfile = userProfileRepository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("User profile not found: " + profileId));

        // Update fields
        existingProfile.setFirstName(updatedProfile.getFirstName());
        existingProfile.setLastName(updatedProfile.getLastName());
        existingProfile.setDisplayName(updatedProfile.getDisplayName());
        existingProfile.setPhone(updatedProfile.getPhone());
        existingProfile.setDateOfBirth(updatedProfile.getDateOfBirth());
        existingProfile.setGender(updatedProfile.getGender());
        existingProfile.setBio(updatedProfile.getBio());

        UserProfile savedProfile = userProfileRepository.save(existingProfile);

        auditService.logUserProfileUpdated(profileId, correlationId);

        logger.info("[{}] Updated user profile: {}", correlationId, profileId);
        return savedProfile;
    }

    /**
     * Update user avatar.
     */
    public void updateUserAvatar(UUID profileId, String avatarUrl, String correlationId) {
        logger.info("[{}] Updating avatar for user profile: {}", correlationId, profileId);

        int updated = userProfileRepository.updateAvatar(profileId, avatarUrl);
        if (updated == 0) {
            throw new IllegalArgumentException("User profile not found: " + profileId);
        }

        auditService.logUserAvatarUpdated(profileId, correlationId);
    }

    /**
     * Update user email (called when auth service updates email).
     */
    public void updateUserEmail(UUID userId, String newEmail, String correlationId) {
        logger.info("[{}] Updating email for user: {} to {}", correlationId, userId, newEmail);

        int updated = userProfileRepository.updateEmail(userId, newEmail);
        if (updated == 0) {
            logger.warn("[{}] User profile not found for user: {}", correlationId, userId);
        } else {
            auditService.logUserEmailUpdated(userId, newEmail, correlationId);
        }
    }

    /**
     * Search user profiles by name or email.
     */
    public Page<UserProfile> searchUserProfiles(String query, Pageable pageable, String correlationId) {
        logger.debug("[{}] Searching user profiles with query: '{}'", correlationId, query);
        return userProfileRepository.searchByNameOrEmail(query, pageable);
    }

    /**
     * Get all user profiles with pagination.
     */
    public Page<UserProfile> getAllUserProfiles(Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting all user profiles, page: {}", correlationId, pageable.getPageNumber());
        return userProfileRepository.findAll(pageable);
    }

    /**
     * Get user profiles created within date range.
     */
    public Page<UserProfile> getUserProfilesByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                       Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting user profiles by date range: {} to {}", correlationId, startDate, endDate);
        return userProfileRepository.findByCreatedDateRange(startDate, endDate, pageable);
    }

    /**
     * Get user analytics.
     */
    public UserAnalytics getUserAnalytics(String correlationId) {
        logger.debug("[{}] Getting user analytics", correlationId);

        long totalUsers = userProfileRepository.countTotalUsers();
        long usersLast24Hours = userProfileRepository.countUsersCreatedBetween(
            LocalDateTime.now().minusDays(1), LocalDateTime.now());
        long usersLast7Days = userProfileRepository.countUsersCreatedBetween(
            LocalDateTime.now().minusDays(7), LocalDateTime.now());
        long usersLast30Days = userProfileRepository.countUsersCreatedBetween(
            LocalDateTime.now().minusDays(30), LocalDateTime.now());

        return new UserAnalytics(totalUsers, usersLast24Hours, usersLast7Days, usersLast30Days);
    }

    /**
     * Delete user profile.
     */
    public void deleteUserProfile(UUID profileId, String correlationId) {
        logger.info("[{}] Deleting user profile: {}", correlationId, profileId);

        if (!userProfileRepository.existsById(profileId)) {
            throw new IllegalArgumentException("User profile not found: " + profileId);
        }

        userProfileRepository.deleteById(profileId);
        auditService.logUserProfileDeleted(profileId, correlationId);
    }

    /**
     * Result classes
     */
    public static class UserAnalytics {
        private final long totalUsers;
        private final long usersLast24Hours;
        private final long usersLast7Days;
        private final long usersLast30Days;

        public UserAnalytics(long totalUsers, long usersLast24Hours, long usersLast7Days, long usersLast30Days) {
            this.totalUsers = totalUsers;
            this.usersLast24Hours = usersLast24Hours;
            this.usersLast7Days = usersLast7Days;
            this.usersLast30Days = usersLast30Days;
        }

        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getUsersLast24Hours() { return usersLast24Hours; }
        public long getUsersLast7Days() { return usersLast7Days; }
        public long getUsersLast30Days() { return usersLast30Days; }
    }
}