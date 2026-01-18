package com.bookstore.user.service;

import com.bookstore.user.domain.UserProfile;
import com.bookstore.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserProfileService userProfileService;

    private UserProfile testProfile;
    private UUID userId;
    private UUID profileId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        profileId = UUID.randomUUID();
        testProfile = createTestProfile();
    }

    private UserProfile createTestProfile() {
        UserProfile profile = new UserProfile(userId, "test@example.com");
        profile.setId(profileId);
        profile.setFirstName("John");
        profile.setLastName("Doe");
        profile.setDisplayName("Johnny");
        profile.setPhone("+1234567890");
        profile.setBio("Test user bio");
        return profile;
    }

    @Test
    void testCreateUserProfile_Success() {
        // Arrange
        String email = "newuser@example.com";
        String correlationId = "test-correlation-id";

        when(userProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile savedProfile = invocation.getArgument(0);
            savedProfile.setId(profileId); // Set the ID that would be generated
            return savedProfile;
        });

        // Act
        UserProfile result = userProfileService.createUserProfile(userId, email, correlationId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(email, result.getEmail());

        verify(userProfileRepository).save(any(UserProfile.class));
        verify(auditService).logUserProfileCreated(userId, email, correlationId);
    }

    @Test
    void testCreateUserProfile_ProfileExists() {
        // Arrange
        String email = "existing@example.com";
        String correlationId = "test-correlation-id";

        when(userProfileRepository.existsByUserId(userId)).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userProfileService.createUserProfile(userId, email, correlationId));

        assertEquals("User profile already exists for user: " + userId, exception.getMessage());
    }

    @Test
    void testGetUserProfileByUserId() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(testProfile));

        // Act
        Optional<UserProfile> result = userProfileService.getUserProfileByUserId(userId, correlationId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProfile, result.get());
    }

    @Test
    void testGetUserProfileById() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(userProfileRepository.findById(profileId)).thenReturn(Optional.of(testProfile));

        // Act
        Optional<UserProfile> result = userProfileService.getUserProfileById(profileId, correlationId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProfile, result.get());
    }

    @Test
    void testUpdateUserProfile_Success() {
        // Arrange
        String correlationId = "test-correlation-id";
        UserProfile updatedProfile = new UserProfile();
        updatedProfile.setFirstName("Jane");
        updatedProfile.setLastName("Smith");
        updatedProfile.setDisplayName("Janie");
        updatedProfile.setPhone("+0987654321");
        updatedProfile.setBio("Updated bio");

        when(userProfileRepository.findById(profileId)).thenReturn(Optional.of(testProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(testProfile);

        // Act
        UserProfile result = userProfileService.updateUserProfile(profileId, updatedProfile, correlationId);

        // Assert
        assertNotNull(result);
        verify(userProfileRepository).save(testProfile);
        verify(auditService).logUserProfileUpdated(profileId, correlationId);
    }

    @Test
    void testUpdateUserProfile_NotFound() {
        // Arrange
        String correlationId = "test-correlation-id";
        UserProfile updatedProfile = new UserProfile();

        when(userProfileRepository.findById(profileId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userProfileService.updateUserProfile(profileId, updatedProfile, correlationId));

        assertEquals("User profile not found: " + profileId, exception.getMessage());
    }

    @Test
    void testUpdateUserAvatar() {
        // Arrange
        String avatarUrl = "https://example.com/avatar.jpg";
        String correlationId = "test-correlation-id";

        when(userProfileRepository.updateAvatar(profileId, avatarUrl)).thenReturn(1);

        // Act
        userProfileService.updateUserAvatar(profileId, avatarUrl, correlationId);

        // Assert
        verify(userProfileRepository).updateAvatar(profileId, avatarUrl);
        verify(auditService).logUserAvatarUpdated(profileId, correlationId);
    }

    @Test
    void testUpdateUserEmail() {
        // Arrange
        String newEmail = "newemail@example.com";
        String correlationId = "test-correlation-id";

        when(userProfileRepository.updateEmail(userId, newEmail)).thenReturn(1);

        // Act
        userProfileService.updateUserEmail(userId, newEmail, correlationId);

        // Assert
        verify(userProfileRepository).updateEmail(userId, newEmail);
        verify(auditService).logUserEmailUpdated(userId, newEmail, correlationId);
    }

    @Test
    void testSearchUserProfiles() {
        // Arrange
        String query = "John";
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<UserProfile> expectedPage = new PageImpl<>(Arrays.asList(testProfile), pageable, 1);
        when(userProfileRepository.searchByNameOrEmail(query, pageable)).thenReturn(expectedPage);

        // Act
        Page<UserProfile> result = userProfileService.searchUserProfiles(query, pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(userProfileRepository).searchByNameOrEmail(query, pageable);
    }

    @Test
    void testGetAllUserProfiles() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String correlationId = "test-correlation-id";

        Page<UserProfile> expectedPage = new PageImpl<>(Arrays.asList(testProfile), pageable, 1);
        when(userProfileRepository.findAll(pageable)).thenReturn(expectedPage);

        // Act
        Page<UserProfile> result = userProfileService.getAllUserProfiles(pageable, correlationId);

        // Assert
        assertEquals(expectedPage, result);
        verify(userProfileRepository).findAll(pageable);
    }

    @Test
    void testGetUserAnalytics() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(userProfileRepository.countTotalUsers()).thenReturn(100L);
        when(userProfileRepository.countUsersCreatedBetween(any(), any())).thenReturn(10L);

        // Act
        UserProfileService.UserAnalytics analytics = userProfileService.getUserAnalytics(correlationId);

        // Assert
        assertEquals(100L, analytics.getTotalUsers());
        assertEquals(10L, analytics.getUsersLast24Hours());
        assertEquals(10L, analytics.getUsersLast7Days());
        assertEquals(10L, analytics.getUsersLast30Days());
    }

    @Test
    void testDeleteUserProfile_Success() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(userProfileRepository.existsById(profileId)).thenReturn(true);

        // Act
        userProfileService.deleteUserProfile(profileId, correlationId);

        // Assert
        verify(userProfileRepository).deleteById(profileId);
        verify(auditService).logUserProfileDeleted(profileId, correlationId);
    }

    @Test
    void testDeleteUserProfile_NotFound() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(userProfileRepository.existsById(profileId)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userProfileService.deleteUserProfile(profileId, correlationId));

        assertEquals("User profile not found: " + profileId, exception.getMessage());
    }
}