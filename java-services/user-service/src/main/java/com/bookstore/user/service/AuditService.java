package com.bookstore.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Audit Service for logging user-related events.
 * Integrates with structured logging for observability.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOGGER");

    /**
     * Log user profile creation.
     */
    public void logUserProfileCreated(UUID userId, String email, String correlationId) {
        auditLogger.info("[{}] USER_PROFILE_CREATED userId={} email={}",
                        correlationId, userId, email);
    }

    /**
     * Log user profile update.
     */
    public void logUserProfileUpdated(UUID profileId, String correlationId) {
        auditLogger.info("[{}] USER_PROFILE_UPDATED profileId={}", correlationId, profileId);
    }

    /**
     * Log user profile deletion.
     */
    public void logUserProfileDeleted(UUID profileId, String correlationId) {
        auditLogger.warn("[{}] USER_PROFILE_DELETED profileId={}", correlationId, profileId);
    }

    /**
     * Log user avatar update.
     */
    public void logUserAvatarUpdated(UUID profileId, String correlationId) {
        auditLogger.info("[{}] USER_AVATAR_UPDATED profileId={}", correlationId, profileId);
    }

    /**
     * Log user email update.
     */
    public void logUserEmailUpdated(UUID userId, String newEmail, String correlationId) {
        auditLogger.info("[{}] USER_EMAIL_UPDATED userId={} newEmail={}",
                        correlationId, userId, newEmail);
    }

    /**
     * Log address creation.
     */
    public void logAddressCreated(UUID userId, String addressType, String correlationId) {
        auditLogger.info("[{}] USER_ADDRESS_CREATED userId={} type={}",
                        correlationId, userId, addressType);
    }

    /**
     * Log address update.
     */
    public void logAddressUpdated(UUID addressId, String correlationId) {
        auditLogger.info("[{}] USER_ADDRESS_UPDATED addressId={}", correlationId, addressId);
    }

    /**
     * Log address deletion.
     */
    public void logAddressDeleted(UUID userId, String addressType, String correlationId) {
        auditLogger.info("[{}] USER_ADDRESS_DELETED userId={} type={}",
                        correlationId, userId, addressType);
    }

    /**
     * Log default address change.
     */
    public void logDefaultAddressSet(UUID userId, String addressType, String correlationId) {
        auditLogger.info("[{}] USER_DEFAULT_ADDRESS_SET userId={} type={}",
                        correlationId, userId, addressType);
    }

    /**
     * Log preference update.
     */
    public void logPreferenceUpdated(UUID userId, String preferenceKey, String correlationId) {
        auditLogger.info("[{}] USER_PREFERENCE_UPDATED userId={} key={}",
                        correlationId, userId, preferenceKey);
    }

    /**
     * Log wishlist change.
     */
    public void logWishlistUpdated(UUID userId, String action, String correlationId) {
        auditLogger.info("[{}] USER_WISHLIST_UPDATED userId={} action={}",
                        correlationId, userId, action);
    }

    /**
     * Log reading history event.
     */
    public void logReadingHistoryEvent(UUID userId, String action, String correlationId) {
        auditLogger.info("[{}] USER_READING_HISTORY userId={} action={}",
                        correlationId, userId, action);
    }

    /**
     * Log security event.
     */
    public void logSecurityEvent(String eventType, String description, UUID userId,
                               String ipAddress, String correlationId) {
        auditLogger.warn("[{}] USER_SECURITY_EVENT type={} description={} userId={} ip={}",
                        correlationId, eventType, description, userId, ipAddress);
    }
}