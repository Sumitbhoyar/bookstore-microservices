package com.bookstore.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Audit Service for logging security and authentication events.
 * Integrates with database audit log and structured logging.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOGGER");

    /**
     * Log successful login event.
     */
    public void logSuccessfulLogin(UUID userId, String ipAddress, String userAgent, String correlationId) {
        auditLogger.info("[{}] LOGIN_SUCCESS userId={} ip={} userAgent={}",
                        correlationId, userId, ipAddress, userAgent);
    }

    /**
     * Log failed login attempt.
     */
    public void logFailedLogin(String email, String reason, String ipAddress, String userAgent, String correlationId) {
        auditLogger.warn("[{}] LOGIN_FAILED email={} reason={} ip={} userAgent={}",
                        correlationId, email, reason, ipAddress, userAgent);
    }

    /**
     * Log logout event.
     */
    public void logLogout(UUID userId, String correlationId) {
        auditLogger.info("[{}] LOGOUT userId={}", correlationId, userId);
    }

    /**
     * Log token validation event.
     */
    public void logTokenValidation(String tokenHash, boolean valid, String correlationId) {
        if (valid) {
            auditLogger.debug("[{}] TOKEN_VALID tokenHash={}", correlationId, tokenHash);
        } else {
            auditLogger.warn("[{}] TOKEN_INVALID tokenHash={}", correlationId, tokenHash);
        }
    }

    /**
     * Log user registration.
     */
    public void logUserRegistration(UUID userId, String email, String ipAddress, String userAgent, String correlationId) {
        auditLogger.info("[{}] USER_REGISTERED userId={} email={} ip={} userAgent={}",
                        correlationId, userId, email, ipAddress, userAgent);
    }

    /**
     * Log registration failure.
     */
    public void logRegistrationFailure(String email, String reason, String ipAddress, String userAgent, String correlationId) {
        auditLogger.warn("[{}] REGISTRATION_FAILED email={} reason={} ip={} userAgent={}",
                        correlationId, email, reason, ipAddress, userAgent);
    }

    /**
     * Log account lockout.
     */
    public void logAccountLockout(UUID userId, String email, String correlationId) {
        auditLogger.warn("[{}] ACCOUNT_LOCKED userId={} email={}", correlationId, userId, email);
    }

    /**
     * Log account unlock.
     */
    public void logAccountUnlock(UUID userId, String email, String correlationId) {
        auditLogger.info("[{}] ACCOUNT_UNLOCKED userId={} email={}", correlationId, userId, email);
    }

    /**
     * Log password change.
     */
    public void logPasswordChange(UUID userId, String correlationId) {
        auditLogger.info("[{}] PASSWORD_CHANGED userId={}", correlationId, userId);
    }

    /**
     * Log security event (generic).
     */
    public void logSecurityEvent(String eventType, String description, UUID userId,
                               String ipAddress, String correlationId) {
        auditLogger.info("[{}] SECURITY_EVENT type={} description={} userId={} ip={}",
                        correlationId, eventType, description, userId, ipAddress);
    }

    /**
     * Log authorization failure.
     */
    public void logAuthorizationFailure(UUID userId, String resource, String action,
                                      String ipAddress, String correlationId) {
        auditLogger.warn("[{}] AUTHZ_FAILED userId={} resource={} action={} ip={}",
                        correlationId, userId, resource, action, ipAddress);
    }
}