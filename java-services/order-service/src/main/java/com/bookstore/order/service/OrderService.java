package com.bookstore.order.service;

import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.repository.OrderRepository;
import com.bookstore.order.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing orders and order lifecycle.
 */
@Service
@Transactional
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;
    private final EventPublisherService eventPublisher;
    private final AuditService auditService;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       PaymentService paymentService,
                       InventoryService inventoryService,
                       ShippingService shippingService,
                       EventPublisherService eventPublisher,
                       AuditService auditService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.shippingService = shippingService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    /**
     * Create a new order from cart/checkout data.
     */
    public Order createOrder(UUID userId, List<OrderItem> items, Order orderDetails,
                           String ipAddress, String userAgent, String correlationId) {
        logger.info("[{}] Creating order for user: {}", correlationId, userId);

        // Validate order data
        validateOrderData(items, orderDetails);

        // Create order
        Order order = new Order(userId, items);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(Order.OrderStatus.PENDING);
        order.setIpAddress(ipAddress);
        order.setUserAgent(userAgent);

        // Copy shipping/billing details
        if (orderDetails != null) {
            order.setShippingMethod(orderDetails.getShippingMethod());
            order.setPaymentMethod(orderDetails.getPaymentMethod());
            order.setNotes(orderDetails.getNotes());
        }

        // Reserve inventory
        inventoryService.reserveInventory(items, order.getId(), correlationId);

        // Calculate totals and save
        order.calculateTotals();
        Order savedOrder = orderRepository.save(order);

        // Save order items
        for (OrderItem item : items) {
            item.setOrder(savedOrder);
            orderItemRepository.save(item);
        }

        auditService.logOrderCreated(savedOrder.getId(), userId, correlationId);

        // Publish event
        eventPublisher.publishOrderCreatedEvent(savedOrder, correlationId);

        logger.info("[{}] Created order: {} for user: {}", correlationId, savedOrder.getOrderNumber(), userId);
        return savedOrder;
    }

    /**
     * Confirm order and initiate payment.
     */
    public Order confirmOrder(UUID orderId, String correlationId) {
        logger.info("[{}] Confirming order: {}", correlationId, orderId);

        Order order = getOrderById(orderId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new IllegalArgumentException("Order cannot be confirmed: " + order.getStatus());
        }

        // Update status
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.addEvent("ORDER_CONFIRMED", "Order confirmed and payment initiated", "PENDING", "CONFIRMED", "SYSTEM");

        Order savedOrder = orderRepository.save(order);

        auditService.logOrderConfirmed(orderId, correlationId);

        // Process payment
        paymentService.processPayment(savedOrder, correlationId);

        logger.info("[{}] Confirmed order: {}", correlationId, orderId);
        return savedOrder;
    }

    /**
     * Handle successful payment.
     */
    public void handlePaymentSuccess(UUID orderId, String paymentReference, String correlationId) {
        logger.info("[{}] Processing payment success for order: {}", correlationId, orderId);

        Order order = getOrderById(orderId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setPaymentStatus(Order.PaymentStatus.PAID);
        order.setPaymentReference(paymentReference);
        order.setPaidAt(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.PAID);
        order.addEvent("PAYMENT_SUCCESS", "Payment processed successfully", null, null, "PAYMENT_SYSTEM");

        Order savedOrder = orderRepository.save(order);

        // Convert inventory reservations
        inventoryService.convertReservations(orderId, correlationId);

        auditService.logPaymentSuccess(orderId, paymentReference, correlationId);

        // Publish event
        eventPublisher.publishPaymentSuccessEvent(savedOrder, correlationId);

        // Start fulfillment process
        shippingService.initiateFulfillment(savedOrder, correlationId);

        logger.info("[{}] Payment success processed for order: {}", correlationId, orderId);
    }

    /**
     * Handle failed payment.
     */
    public void handlePaymentFailure(UUID orderId, String reason, String correlationId) {
        logger.warn("[{}] Processing payment failure for order: {} - {}", correlationId, orderId, reason);

        Order order = getOrderById(orderId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setPaymentStatus(Order.PaymentStatus.FAILED);
        order.addEvent("PAYMENT_FAILED", "Payment failed: " + reason, null, null, "PAYMENT_SYSTEM");

        Order savedOrder = orderRepository.save(order);

        // Release inventory reservations
        inventoryService.releaseReservations(orderId, correlationId);

        auditService.logPaymentFailure(orderId, reason, correlationId);

        // Publish event
        eventPublisher.publishPaymentFailureEvent(savedOrder, reason, correlationId);

        logger.warn("[{}] Payment failure processed for order: {}", correlationId, orderId);
    }

    /**
     * Ship order items.
     */
    public void shipOrderItems(UUID orderId, List<UUID> itemIds, String trackingNumber,
                             String carrier, String correlationId) {
        logger.info("[{}] Shipping order items for order: {}", correlationId, orderId);

        Order order = getOrderById(orderId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Update items
        orderItemRepository.updateTrackingInfo(itemIds, trackingNumber, carrier);

        // Check if order is fully shipped
        long totalItems = orderItemRepository.countByOrderId(orderId);
        long fulfilledItems = orderItemRepository.countByOrderIdAndFulfillmentStatus(orderId, OrderItem.FulfillmentStatus.FULFILLED);

        if (fulfilledItems == totalItems) {
            order.setFulfillmentStatus(Order.FulfillmentStatus.FULFILLED);
            order.setStatus(Order.OrderStatus.SHIPPED);
            order.setShippedAt(LocalDateTime.now());
            order.setTrackingNumber(trackingNumber);
            order.setCarrier(carrier);
            order.addEvent("ORDER_SHIPPED", "Order shipped with tracking: " + trackingNumber, null, null, "SHIPPING_SYSTEM");
        } else {
            order.setFulfillmentStatus(Order.FulfillmentStatus.PARTIALLY_FULFILLED);
        }

        Order savedOrder = orderRepository.save(order);

        auditService.logOrderShipped(orderId, trackingNumber, correlationId);

        // Publish event
        eventPublisher.publishOrderShippedEvent(savedOrder, correlationId);

        logger.info("[{}] Shipped order items for order: {}", correlationId, orderId);
    }

    /**
     * Mark order as delivered.
     */
    public void markOrderDelivered(UUID orderId, String correlationId) {
        logger.info("[{}] Marking order as delivered: {}", correlationId, orderId);

        Order order = getOrderById(orderId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != Order.OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("Order must be shipped before marking as delivered");
        }

        order.setStatus(Order.OrderStatus.DELIVERED);
        order.setFulfillmentStatus(Order.FulfillmentStatus.FULFILLED);
        order.setActualDeliveryDate(java.time.LocalDate.now());
        order.setDeliveredAt(LocalDateTime.now());
        order.addEvent("ORDER_DELIVERED", "Order delivered to customer", null, null, "SHIPPING_SYSTEM");

        Order savedOrder = orderRepository.save(order);

        auditService.logOrderDelivered(orderId, correlationId);

        // Publish event
        eventPublisher.publishOrderDeliveredEvent(savedOrder, correlationId);

        logger.info("[{}] Marked order as delivered: {}", correlationId, orderId);
    }

    /**
     * Cancel order.
     */
    public void cancelOrder(UUID orderId, String reason, String correlationId) {
        logger.info("[{}] Cancelling order: {} - {}", correlationId, orderId, reason);

        Order order = getOrderById(orderId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!order.canBeCancelled()) {
            throw new IllegalArgumentException("Order cannot be cancelled: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.addEvent("ORDER_CANCELLED", "Order cancelled: " + reason, order.getStatus().toString(), "CANCELLED", "SYSTEM");

        Order savedOrder = orderRepository.save(order);

        // Release inventory reservations
        inventoryService.releaseReservations(orderId, correlationId);

        auditService.logOrderCancelled(orderId, reason, correlationId);

        // Publish event
        eventPublisher.publishOrderCancelledEvent(savedOrder, reason, correlationId);

        logger.info("[{}] Cancelled order: {}", correlationId, orderId);
    }

    /**
     * Get order by ID.
     */
    public Optional<Order> getOrderById(UUID orderId, String correlationId) {
        logger.debug("[{}] Getting order: {}", correlationId, orderId);
        return orderRepository.findById(orderId);
    }

    /**
     * Get order by order number.
     */
    public Optional<Order> getOrderByNumber(String orderNumber, String correlationId) {
        logger.debug("[{}] Getting order by number: {}", correlationId, orderNumber);
        return orderRepository.findByOrderNumber(orderNumber);
    }

    /**
     * Get orders for user.
     */
    public Page<Order> getOrdersForUser(UUID userId, Pageable pageable, String correlationId) {
        logger.debug("[{}] Getting orders for user: {} (page: {})", correlationId, userId, pageable.getPageNumber());
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get order analytics.
     */
    public OrderAnalytics getOrderAnalytics(String correlationId) {
        logger.debug("[{}] Getting order analytics", correlationId);

        long totalOrders = orderRepository.count();
        long paidOrders = orderRepository.countByPaymentStatus(Order.PaymentStatus.PAID);
        long shippedOrders = orderRepository.countByStatus(Order.OrderStatus.SHIPPED);
        long deliveredOrders = orderRepository.countByStatus(Order.OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(Order.OrderStatus.CANCELLED);

        BigDecimal totalRevenue = orderRepository.sumTotalAmountByDateRange(
            LocalDateTime.now().minusDays(30), LocalDateTime.now());

        return new OrderAnalytics(totalOrders, paidOrders, shippedOrders, deliveredOrders,
                                cancelledOrders, totalRevenue);
    }

    // Private helper methods

    private void validateOrderData(List<OrderItem> items, Order orderDetails) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        if (items.size() > 50) { // Configurable limit
            throw new IllegalArgumentException("Order cannot contain more than 50 items");
        }

        BigDecimal total = items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.valueOf(10000)) > 0) { // Configurable limit
            throw new IllegalArgumentException("Order total cannot exceed $10,000");
        }
    }

    private String generateOrderNumber() {
        // This would typically call a database function
        // For now, we'll use a simple implementation
        return "ORD-" + System.currentTimeMillis();
    }

    /**
     * Result classes
     */
    public static class OrderAnalytics {
        private final long totalOrders;
        private final long paidOrders;
        private final long shippedOrders;
        private final long deliveredOrders;
        private final long cancelledOrders;
        private final BigDecimal totalRevenue;

        public OrderAnalytics(long totalOrders, long paidOrders, long shippedOrders,
                            long deliveredOrders, long cancelledOrders, BigDecimal totalRevenue) {
            this.totalOrders = totalOrders;
            this.paidOrders = paidOrders;
            this.shippedOrders = shippedOrders;
            this.deliveredOrders = deliveredOrders;
            this.cancelledOrders = cancelledOrders;
            this.totalRevenue = totalRevenue;
        }

        // Getters
        public long getTotalOrders() { return totalOrders; }
        public long getPaidOrders() { return paidOrders; }
        public long getShippedOrders() { return shippedOrders; }
        public long getDeliveredOrders() { return deliveredOrders; }
        public long getCancelledOrders() { return cancelledOrders; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }
}