package com.bookstore.order.repository;

import com.bookstore.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repository for OrderItem entity operations.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Find all items for an order.
     */
    List<OrderItem> findByOrderId(UUID orderId);

    /**
     * Find items by product ID.
     */
    List<OrderItem> findByProductId(UUID productId);

    /**
     * Find items by variant ID.
     */
    List<OrderItem> findByVariantId(UUID variantId);

    /**
     * Find items by fulfillment status.
     */
    List<OrderItem> findByFulfillmentStatus(OrderItem.FulfillmentStatus status);

    /**
     * Find unfulfilled items for an order.
     */
    List<OrderItem> findByOrderIdAndFulfillmentStatus(UUID orderId, OrderItem.FulfillmentStatus status);

    /**
     * Count items by order.
     */
    long countByOrderId(UUID orderId);

    /**
     * Count fulfilled items by order.
     */
    long countByOrderIdAndFulfillmentStatus(UUID orderId, OrderItem.FulfillmentStatus status);

    /**
     * Sum total price by order.
     */
    @Query("SELECT COALESCE(SUM(oi.totalPrice), 0) FROM OrderItem oi WHERE oi.order.id = :orderId")
    BigDecimal sumTotalPriceByOrderId(@Param("orderId") UUID orderId);

    /**
     * Sum quantity by product.
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.productId = :productId")
    long sumQuantityByProductId(@Param("productId") UUID productId);

    /**
     * Sum quantity by variant.
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.variantId = :variantId")
    long sumQuantityByVariantId(@Param("variantId") UUID variantId);

    /**
     * Get popular products (most ordered).
     */
    @Query("""
        SELECT
            oi.productId as productId,
            oi.productTitle as productTitle,
            SUM(oi.quantity) as totalQuantity,
            COUNT(DISTINCT oi.order.id) as orderCount,
            SUM(oi.totalPrice) as totalRevenue
        FROM OrderItem oi
        WHERE oi.order.createdAt BETWEEN :startDate AND :endDate
        GROUP BY oi.productId, oi.productTitle
        ORDER BY SUM(oi.quantity) DESC
        """)
    List<Object[]> getPopularProducts(@Param("startDate") java.time.LocalDateTime startDate,
                                    @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get best-selling variants.
     */
    @Query("""
        SELECT
            oi.variantId as variantId,
            oi.sku as sku,
            oi.productTitle as productTitle,
            oi.variantTitle as variantTitle,
            SUM(oi.quantity) as totalQuantity,
            SUM(oi.totalPrice) as totalRevenue
        FROM OrderItem oi
        WHERE oi.order.createdAt BETWEEN :startDate AND :endDate
        GROUP BY oi.variantId, oi.sku, oi.productTitle, oi.variantTitle
        ORDER BY SUM(oi.quantity) DESC
        """)
    List<Object[]> getBestSellingVariants(@Param("startDate") java.time.LocalDateTime startDate,
                                        @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get items that need fulfillment.
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.fulfillmentStatus = 'UNFULFILLED' ORDER BY oi.createdAt ASC")
    List<OrderItem> findItemsNeedingFulfillment();

    /**
     * Get items by order and fulfillment status.
     */
    List<OrderItem> findByOrderIdAndFulfillmentStatusIn(UUID orderId, List<OrderItem.FulfillmentStatus> statuses);

    /**
     * Update fulfillment status for items.
     */
    @Query("UPDATE OrderItem oi SET oi.fulfillmentStatus = :status WHERE oi.id IN :itemIds")
    int updateFulfillmentStatus(@Param("itemIds") List<UUID> itemIds,
                              @Param("status") OrderItem.FulfillmentStatus status);

    /**
     * Update tracking information for items.
     */
    @Query("UPDATE OrderItem oi SET oi.trackingNumber = :trackingNumber, oi.carrier = :carrier, oi.shippedAt = CURRENT_TIMESTAMP WHERE oi.id IN :itemIds")
    int updateTrackingInfo(@Param("itemIds") List<UUID> itemIds,
                         @Param("trackingNumber") String trackingNumber,
                         @Param("carrier") String carrier);

    /**
     * Update delivery information for items.
     */
    @Query("UPDATE OrderItem oi SET oi.deliveredAt = CURRENT_TIMESTAMP WHERE oi.id IN :itemIds")
    int updateDeliveryInfo(@Param("itemIds") List<UUID> itemIds);
}