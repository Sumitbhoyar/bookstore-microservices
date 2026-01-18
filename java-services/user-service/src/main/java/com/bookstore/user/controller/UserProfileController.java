package com.bookstore.user.controller;

import com.bookstore.user.domain.UserProfile;
import com.bookstore.user.service.UserProfileService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST Controller for User Profile operations.
 * Provides endpoints for managing user profile information.
 */
@RestController
@RequestMapping("/api/v1/users")
@Timed(value = "user.controller", percentiles = {0.5, 0.95, 0.99})
public class UserProfileController {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

    private final UserProfileService userProfileService;

    @Autowired
    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * GET /api/v1/users/profile/{userId}
     * Get user profile by user ID (from auth service).
     */
    @GetMapping("/profile/{userId}")
    @Timed(value = "user.get-profile", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getUserProfile(@PathVariable UUID userId) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting user profile for user: {}", correlationId, userId);

        return userProfileService.getUserProfileByUserId(userId, correlationId)
            .map(profile -> ResponseEntity.ok(new UserProfileResponse(profile)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/users/{profileId}
     * Get user profile by profile ID.
     */
    @GetMapping("/{profileId}")
    @Timed(value = "user.get-profile-by-id", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getUserProfileById(@PathVariable UUID profileId) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting user profile by ID: {}", correlationId, profileId);

        return userProfileService.getUserProfileById(profileId, correlationId)
            .map(profile -> ResponseEntity.ok(new UserProfileResponse(profile)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/v1/users/{profileId}
     * Update user profile information.
     */
    @PutMapping("/{profileId}")
    @Timed(value = "user.update-profile", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> updateUserProfile(@PathVariable UUID profileId,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Updating user profile: {}", correlationId, profileId);

        try {
            UserProfile updatedProfile = createProfileFromRequest(request);
            UserProfile savedProfile = userProfileService.updateUserProfile(profileId, updatedProfile, correlationId);

            return ResponseEntity.ok(new UserProfileResponse(savedProfile));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Update failed for profile {}: {}", correlationId, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        }
    }

    /**
     * PATCH /api/v1/users/{profileId}/avatar
     * Update user avatar URL.
     */
    @PatchMapping("/{profileId}/avatar")
    @Timed(value = "user.update-avatar", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> updateUserAvatar(@PathVariable UUID profileId,
                                            @Valid @RequestBody UpdateAvatarRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Updating avatar for profile: {}", correlationId, profileId);

        try {
            userProfileService.updateUserAvatar(profileId, request.getAvatarUrl(), correlationId);
            return ResponseEntity.ok(new SuccessResponse("Avatar updated successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Avatar update failed for profile {}: {}", correlationId, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/users
     * Create user profile (called by auth service after registration).
     */
    @PostMapping
    @Timed(value = "user.create-profile", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> createUserProfile(@Valid @RequestBody CreateProfileRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Creating user profile for user: {}", correlationId, request.getUserId());

        try {
            UserProfile profile = userProfileService.createUserProfile(
                request.getUserId(),
                request.getEmail(),
                correlationId
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UserProfileResponse(profile));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Profile creation failed for user {}: {}", correlationId, request.getUserId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("PROFILE_EXISTS", e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/users/{userId}/email
     * Update user email (called by auth service).
     */
    @PutMapping("/{userId}/email")
    @Timed(value = "user.update-email", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> updateUserEmail(@PathVariable UUID userId,
                                           @Valid @RequestBody UpdateEmailRequest request) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Updating email for user: {}", correlationId, userId);

        userProfileService.updateUserEmail(userId, request.getEmail(), correlationId);

        return ResponseEntity.ok(new SuccessResponse("Email updated successfully"));
    }

    /**
     * GET /api/v1/users/search
     * Search user profiles by name or email.
     */
    @GetMapping("/search")
    @Timed(value = "user.search-profiles", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> searchUserProfiles(@RequestParam String query,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Searching user profiles: '{}'", correlationId, query);

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_QUERY", "Search query cannot be empty"));
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<UserProfile> profiles = userProfileService.searchUserProfiles(query, pageable, correlationId);

        return ResponseEntity.ok(new UserProfilePageResponse(profiles));
    }

    /**
     * GET /api/v1/users
     * Get all user profiles with pagination (admin endpoint).
     */
    @GetMapping
    @Timed(value = "user.get-all-profiles", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getAllUserProfiles(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting all user profiles, page: {}", correlationId, page);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<UserProfile> profiles = userProfileService.getAllUserProfiles(pageable, correlationId);

        return ResponseEntity.ok(new UserProfilePageResponse(profiles));
    }

    /**
     * GET /api/v1/users/analytics
     * Get user analytics data.
     */
    @GetMapping("/analytics")
    @Timed(value = "user.get-analytics", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getUserAnalytics() {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting user analytics", correlationId);

        UserProfileService.UserAnalytics analytics = userProfileService.getUserAnalytics(correlationId);

        return ResponseEntity.ok(new UserAnalyticsResponse(analytics));
    }

    /**
     * DELETE /api/v1/users/{profileId}
     * Delete user profile (admin endpoint).
     */
    @DeleteMapping("/{profileId}")
    @Timed(value = "user.delete-profile", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> deleteUserProfile(@PathVariable UUID profileId) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Deleting user profile: {}", correlationId, profileId);

        try {
            userProfileService.deleteUserProfile(profileId, correlationId);
            return ResponseEntity.ok(new SuccessResponse("Profile deleted successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Delete failed for profile {}: {}", correlationId, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        }
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

    private UserProfile createProfileFromRequest(UpdateProfileRequest request) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDisplayName(request.getDisplayName());
        profile.setPhone(request.getPhone());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setGender(request.getGender());
        profile.setBio(request.getBio());
        return profile;
    }

    // Request/Response DTOs

    public static class CreateProfileRequest {
        private UUID userId;
        private String email;

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class UpdateProfileRequest {
        private String firstName;
        private String lastName;
        private String displayName;
        private String phone;
        private java.time.LocalDate dateOfBirth;
        private UserProfile.Gender gender;
        private String bio;

        // Getters and setters
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public java.time.LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(java.time.LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public UserProfile.Gender getGender() { return gender; }
        public void setGender(UserProfile.Gender gender) { this.gender = gender; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
    }

    public static class UpdateAvatarRequest {
        private String avatarUrl;

        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    public static class UpdateEmailRequest {
        private String email;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class UserProfileResponse {
        private UUID id;
        private UUID userId;
        private String email;
        private String firstName;
        private String lastName;
        private String displayName;
        private String phone;
        private java.time.LocalDate dateOfBirth;
        private UserProfile.Gender gender;
        private String avatarUrl;
        private String bio;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private boolean complete;

        public UserProfileResponse(UserProfile profile) {
            this.id = profile.getId();
            this.userId = profile.getUserId();
            this.email = profile.getEmail();
            this.firstName = profile.getFirstName();
            this.lastName = profile.getLastName();
            this.displayName = profile.getDisplayName();
            this.phone = profile.getPhone();
            this.dateOfBirth = profile.getDateOfBirth();
            this.gender = profile.getGender();
            this.avatarUrl = profile.getAvatarUrl();
            this.bio = profile.getBio();
            this.createdAt = profile.getCreatedAt();
            this.updatedAt = profile.getUpdatedAt();
            this.complete = profile.isComplete();
        }

        // Getters
        public UUID getId() { return id; }
        public UUID getUserId() { return userId; }
        public String getEmail() { return email; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getDisplayName() { return displayName; }
        public String getPhone() { return phone; }
        public java.time.LocalDate getDateOfBirth() { return dateOfBirth; }
        public UserProfile.Gender getGender() { return gender; }
        public String getAvatarUrl() { return avatarUrl; }
        public String getBio() { return bio; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public boolean isComplete() { return complete; }
    }

    public static class UserProfilePageResponse {
        private java.util.List<UserProfileResponse> content;
        private int pageNumber;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public UserProfilePageResponse(Page<UserProfile> page) {
            this.content = page.getContent().stream()
                    .map(UserProfileResponse::new)
                    .toList();
            this.pageNumber = page.getNumber();
            this.pageSize = page.getSize();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.first = page.isFirst();
            this.last = page.isLast();
        }

        // Getters
        public java.util.List<UserProfileResponse> getContent() { return content; }
        public int getPageNumber() { return pageNumber; }
        public int getPageSize() { return pageSize; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isFirst() { return first; }
        public boolean isLast() { return last; }
    }

    public static class UserAnalyticsResponse {
        private long totalUsers;
        private long usersLast24Hours;
        private long usersLast7Days;
        private long usersLast30Days;

        public UserAnalyticsResponse(UserProfileService.UserAnalytics analytics) {
            this.totalUsers = analytics.getTotalUsers();
            this.usersLast24Hours = analytics.getUsersLast24Hours();
            this.usersLast7Days = analytics.getUsersLast7Days();
            this.usersLast30Days = analytics.getUsersLast30Days();
        }

        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getUsersLast24Hours() { return usersLast24Hours; }
        public long getUsersLast7Days() { return usersLast7Days; }
        public long getUsersLast30Days() { return usersLast30Days; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}