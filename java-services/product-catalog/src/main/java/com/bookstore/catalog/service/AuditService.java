package com.bookstore.catalog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Audit Service for logging product catalog events.
 * Integrates with structured logging for observability.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOGGER");

    /**
     * Log product creation.
     */
    public void logProductCreated(UUID productId, String sku, String correlationId) {
        auditLogger.info("[{}] PRODUCT_CREATED productId={} sku={}",
                        correlationId, productId, sku);
    }

    /**
     * Log product update.
     */
    public void logProductUpdated(UUID productId, String correlationId) {
        auditLogger.info("[{}] PRODUCT_UPDATED productId={}", correlationId, productId);
    }

    /**
     * Log product deletion.
     */
    public void logProductDeleted(UUID productId, String sku, String correlationId) {
        auditLogger.warn("[{}] PRODUCT_DELETED productId={} sku={}",
                        correlationId, productId, sku);
    }

    /**
     * Log variant creation.
     */
    public void logVariantCreated(UUID variantId, String sku, String correlationId) {
        auditLogger.info("[{}] VARIANT_CREATED variantId={} sku={}",
                        correlationId, variantId, sku);
    }

    /**
     * Log variant update.
     */
    public void logVariantUpdated(UUID variantId, String correlationId) {
        auditLogger.info("[{}] VARIANT_UPDATED variantId={}", correlationId, variantId);
    }

    /**
     * Log variant deletion.
     */
    public void logVariantDeleted(UUID variantId, String sku, String correlationId) {
        auditLogger.warn("[{}] VARIANT_DELETED variantId={} sku={}",
                        correlationId, variantId, sku);
    }

    /**
     * Log inventory update.
     */
    public void logInventoryUpdated(UUID variantId, int previousQuantity, int newQuantity, String reason, String correlationId) {
        auditLogger.info("[{}] INVENTORY_UPDATED variantId={} previous={} new={} reason={}",
                        correlationId, variantId, previousQuantity, newQuantity, reason);
    }

    /**
     * Log price change.
     */
    public void logPriceChanged(UUID variantId, BigDecimal oldPrice, BigDecimal newPrice, String correlationId) {
        auditLogger.info("[{}] PRICE_CHANGED variantId={} oldPrice={} newPrice={}",
                        correlationId, variantId, oldPrice, newPrice);
    }

    /**
     * Log category creation.
     */
    public void logCategoryCreated(UUID categoryId, String name, String correlationId) {
        auditLogger.info("[{}] CATEGORY_CREATED categoryId={} name={}",
                        correlationId, categoryId, name);
    }

    /**
     * Log category update.
     */
    public void logCategoryUpdated(UUID categoryId, String correlationId) {
        auditLogger.info("[{}] CATEGORY_UPDATED categoryId={}", correlationId, categoryId);
    }

    /**
     * Log review creation.
     */
    public void logReviewCreated(UUID reviewId, UUID productId, UUID userId, int rating, String correlationId) {
        auditLogger.info("[{}] REVIEW_CREATED reviewId={} productId={} userId={} rating={}",
                        correlationId, reviewId, productId, userId, rating);
    }

    /**
     * Log review moderation.
     */
    public void logReviewModerated(UUID reviewId, String action, String correlationId) {
        auditLogger.info("[{}] REVIEW_MODERATED reviewId={} action={}",
                        correlationId, reviewId, action);
    }

    /**
     * Log search query.
     */
    public void logSearchQuery(String query, int resultCount, String correlationId) {
        auditLogger.debug("[{}] SEARCH_QUERY query='{}' results={}",
                         correlationId, query, resultCount);
    }

    /**
     * Log low inventory alert.
     */
    public void logLowInventoryAlert(UUID variantId, String sku, int quantity, String correlationId) {
        auditLogger.warn("[{}] LOW_INVENTORY_ALERT variantId={} sku={} quantity={}",
                        correlationId, variantId, sku, quantity);
    }

    /**
     * Log out of stock alert.
     */
    public void logOutOfStockAlert(UUID variantId, String sku, String correlationId) {
        auditLogger.warn("[{}] OUT_OF_STOCK_ALERT variantId={} sku={}",
                        correlationId, variantId, sku);
    }

    /**
     * Log security event.
     */
    public void logSecurityEvent(String eventType, String description, UUID productId,
                               String ipAddress, String correlationId) {
        auditLogger.warn("[{}] PRODUCT_SECURITY_EVENT type={} description={} productId={} ip={}",
                        correlationId, eventType, description, productId, ipAddress);
    }

    /**
     * Log bulk operation.
     */
    public void logBulkOperation(String operation, int count, String correlationId) {
        auditLogger.info("[{}] BULK_OPERATION type={} count={}",
                        correlationId, operation, count);
    }

    /**
     * Log import/export operation.
     */
    public void logDataImport(String source, int recordsProcessed, String correlationId) {
        auditLogger.info("[{}] DATA_IMPORT source={} records={}",
                        correlationId, source, recordsProcessed);
    }
}