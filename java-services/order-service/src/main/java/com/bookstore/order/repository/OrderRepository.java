package com.bookstore.order.repository;

import com.bookstore.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Order entity operations.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Find order by order number.
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find orders by user ID.
     */
    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find orders by status.
     */
    Page<Order> findByStatusOrderByCreatedAtDesc(Order.OrderStatus status, Pageable pageable);

    /**
     * Find orders by payment status.
     */
    Page<Order> findByPaymentStatusOrderByCreatedAtDesc(Order.PaymentStatus paymentStatus, Pageable pageable);

    /**
     * Find orders by user and status.
     */
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, Order.OrderStatus status, Pageable pageable);

    /**
     * Find orders created within date range.
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    Page<Order> findByCreatedDateRange(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate,
                                     Pageable pageable);

    /**
     * Find orders by user within date range.
     */
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    Page<Order> findByUserIdAndCreatedDateRange(@Param("userId") UUID userId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate,
                                              Pageable pageable);

    /**
     * Count orders by status.
     */
    long countByStatus(Order.OrderStatus status);

    /**
     * Count orders by payment status.
     */
    long countByPaymentStatus(Order.PaymentStatus paymentStatus);

    /**
     * Count orders by user.
     */
    long countByUserId(UUID userId);

    /**
     * Sum total amount for orders in date range.
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate AND o.paymentStatus = 'PAID'")
    BigDecimal sumTotalAmountByDateRange(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Sum total amount by user.
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.userId = :userId AND o.paymentStatus = 'PAID'")
    BigDecimal sumTotalAmountByUser(@Param("userId") UUID userId);

    /**
     * Find orders that need payment processing (confirmed but not paid).
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'CONFIRMED' AND o.paymentStatus = 'PENDING' AND o.createdAt < :cutoffTime")
    List<Order> findOrdersNeedingPaymentProcessing(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find orders that should be auto-cancelled (unpaid after timeout).
     */
    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING', 'CONFIRMED') AND o.paymentStatus = 'PENDING' AND o.createdAt < :cutoffTime")
    List<Order> findOrdersToAutoCancel(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find orders that are overdue for delivery.
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'SHIPPED' AND o.estimatedDeliveryDate < CURRENT_DATE AND o.actualDeliveryDate IS NULL")
    List<Order> findOverdueOrders();

    /**
     * Find recent orders for dashboard.
     */
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    Page<Order> findRecentOrders(Pageable pageable);

    /**
     * Get order statistics.
     */
    @Query("""
        SELECT
            COUNT(o) as totalOrders,
            SUM(CASE WHEN o.paymentStatus = 'PAID' THEN 1 ELSE 0 END) as paidOrders,
            SUM(CASE WHEN o.status = 'DELIVERED' THEN 1 ELSE 0 END) as deliveredOrders,
            SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelledOrders,
            AVG(o.totalAmount) as averageOrderValue,
            SUM(o.totalAmount) as totalRevenue
        FROM Order o
        WHERE o.createdAt BETWEEN :startDate AND :endDate
        """)
    Object[] getOrderStatistics(@Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate);

    /**
     * Get revenue by day for the last N days.
     */
    @Query("""
        SELECT
            DATE(o.createdAt) as date,
            SUM(o.totalAmount) as revenue,
            COUNT(o) as orderCount
        FROM Order o
        WHERE o.paymentStatus = 'PAID' AND o.createdAt >= :startDate
        GROUP BY DATE(o.createdAt)
        ORDER BY DATE(o.createdAt) DESC
        """)
    List<Object[]> getRevenueByDay(@Param("startDate") LocalDateTime startDate);
}