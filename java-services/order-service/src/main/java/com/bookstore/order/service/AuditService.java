package com.bookstore.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Audit Service for logging order-related events.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOGGER");

    /**
     * Log order creation.
     */
    public void logOrderCreated(UUID orderId, UUID userId, String correlationId) {
        auditLogger.info("[{}] ORDER_CREATED orderId={} userId={}", correlationId, orderId, userId);
    }

    /**
     * Log order confirmation.
     */
    public void logOrderConfirmed(UUID orderId, String correlationId) {
        auditLogger.info("[{}] ORDER_CONFIRMED orderId={}", correlationId, orderId);
    }

    /**
     * Log payment success.
     */
    public void logPaymentSuccess(UUID orderId, String paymentReference, String correlationId) {
        auditLogger.info("[{}] PAYMENT_SUCCESS orderId={} paymentRef={}", correlationId, orderId, paymentReference);
    }

    /**
     * Log payment failure.
     */
    public void logPaymentFailure(UUID orderId, String reason, String correlationId) {
        auditLogger.warn("[{}] PAYMENT_FAILED orderId={} reason={}", correlationId, orderId, reason);
    }

    /**
     * Log order shipped.
     */
    public void logOrderShipped(UUID orderId, String trackingNumber, String correlationId) {
        auditLogger.info("[{}] ORDER_SHIPPED orderId={} tracking={}", correlationId, orderId, trackingNumber);
    }

    /**
     * Log order delivered.
     */
    public void logOrderDelivered(UUID orderId, String correlationId) {
        auditLogger.info("[{}] ORDER_DELIVERED orderId={}", correlationId, orderId);
    }

    /**
     * Log order cancelled.
     */
    public void logOrderCancelled(UUID orderId, String reason, String correlationId) {
        auditLogger.warn("[{}] ORDER_CANCELLED orderId={} reason={}", correlationId, orderId, reason);
    }

    /**
     * Log inventory update.
     */
    public void logInventoryUpdate(UUID orderId, String action, String correlationId) {
        auditLogger.info("[{}] INVENTORY_UPDATE orderId={} action={}", correlationId, orderId, action);
    }

    /**
     * Log shipping event.
     */
    public void logShippingEvent(UUID orderId, String event, String correlationId) {
        auditLogger.info("[{}] SHIPPING_EVENT orderId={} event={}", correlationId, orderId, event);
    }

    /**
     * Log return request.
     */
    public void logReturnRequested(UUID orderId, String reason, String correlationId) {
        auditLogger.info("[{}] RETURN_REQUESTED orderId={} reason={}", correlationId, orderId, reason);
    }

    /**
     * Log return processed.
     */
    public void logReturnProcessed(UUID returnId, String status, String correlationId) {
        auditLogger.info("[{}] RETURN_PROCESSED returnId={} status={}", correlationId, returnId, status);
    }

    /**
     * Log refund processed.
     */
    public void logRefundProcessed(UUID orderId, BigDecimal amount, String correlationId) {
        auditLogger.info("[{}] REFUND_PROCESSED orderId={} amount={}", correlationId, orderId, amount);
    }

    /**
     * Log security event.
     */
    public void logSecurityEvent(String eventType, String description, UUID orderId,
                               String ipAddress, String correlationId) {
        auditLogger.warn("[{}] ORDER_SECURITY_EVENT type={} description={} orderId={} ip={}",
                        correlationId, eventType, description, orderId, ipAddress);
    }

    /**
     * Log business metric.
     */
    public void logBusinessMetric(String metric, String value, String correlationId) {
        auditLogger.info("[{}] BUSINESS_METRIC name={} value={}", correlationId, metric, value);
    }

    /**
     * Log admin action.
     */
    public void logAdminAction(String action, UUID orderId, String adminId, String correlationId) {
        auditLogger.info("[{}] ADMIN_ACTION type={} orderId={} adminId={}", correlationId, action, orderId, adminId);
    }
}