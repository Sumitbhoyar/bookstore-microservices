package com.bookstore.order.controller;

import com.bookstore.order.domain.Order;
import com.bookstore.order.service.OrderService;
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

import java.util.UUID;

/**
 * REST Controller for Order operations.
 * Provides endpoints for order management, checkout, and order lifecycle.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Timed(value = "order.controller", percentiles = {0.5, 0.95, 0.99})
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /api/v1/orders
     * Get orders for authenticated user.
     */
    @GetMapping
    @Timed(value = "order.get-orders", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getOrders(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestHeader("X-User-Id") UUID userId) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting orders for user: {} (page: {}, size: {})",
                    correlationId, userId, page, size);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Order> orders = orderService.getOrdersForUser(userId, pageable, correlationId);

        return ResponseEntity.ok(new OrderPageResponse(orders));
    }

    /**
     * GET /api/v1/orders/{orderId}
     * Get order by ID.
     */
    @GetMapping("/{orderId}")
    @Timed(value = "order.get-order", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getOrder(@PathVariable UUID orderId,
                                    @RequestHeader("X-User-Id") UUID userId) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting order: {} for user: {}", correlationId, orderId, userId);

        return orderService.getOrderById(orderId, correlationId)
            .filter(order -> order.getUserId().equals(userId)) // Ensure user owns the order
            .map(order -> ResponseEntity.ok(new OrderResponse(order)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/orders/number/{orderNumber}
     * Get order by order number.
     */
    @GetMapping("/number/{orderNumber}")
    @Timed(value = "order.get-order-by-number", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getOrderByNumber(@PathVariable String orderNumber,
                                            @RequestHeader("X-User-Id") UUID userId) {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting order by number: {} for user: {}", correlationId, orderNumber, userId);

        return orderService.getOrderByNumber(orderNumber, correlationId)
            .filter(order -> order.getUserId().equals(userId)) // Ensure user owns the order
            .map(order -> ResponseEntity.ok(new OrderResponse(order)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/v1/orders
     * Create a new order (checkout).
     */
    @PostMapping
    @Timed(value = "order.create-order", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                       @RequestHeader("X-User-Id") UUID userId,
                                       @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Creating order for user: {}", correlationId, userId);

        try {
            // Create order items from request
            java.util.List<com.bookstore.order.domain.OrderItem> items =
                request.getItems().stream()
                    .map(item -> new com.bookstore.order.domain.OrderItem(
                        item.getProductId(),
                        item.getVariantId(),
                        item.getSku(),
                        item.getProductTitle(),
                        item.getQuantity(),
                        item.getUnitPrice() != null ? java.math.BigDecimal.valueOf(item.getUnitPrice()) :
                                                     java.math.BigDecimal.ZERO
                    ))
                    .toList();

            // Create order details
            Order orderDetails = new Order();
            orderDetails.setShippingMethod(request.getShippingMethod());
            orderDetails.setPaymentMethod(request.getPaymentMethod());
            orderDetails.setNotes(request.getNotes());

            Order order = orderService.createOrder(userId, items, orderDetails, ipAddress, userAgent, correlationId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrderResponse(order));

        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Order creation failed for user {}: {}", correlationId, userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("[{}] Order creation failed for user {}: {}", correlationId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Order creation failed"));
        }
    }

    /**
     * POST /api/v1/orders/{orderId}/confirm
     * Confirm order and initiate payment.
     */
    @PostMapping("/{orderId}/confirm")
    @Timed(value = "order.confirm-order", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> confirmOrder(@PathVariable UUID orderId,
                                        @RequestHeader("X-User-Id") UUID userId) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Confirming order: {} for user: {}", correlationId, orderId, userId);

        try {
            Order order = orderService.confirmOrder(orderId, correlationId);

            // Verify user owns the order
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("FORBIDDEN", "Access denied"));
            }

            return ResponseEntity.ok(new OrderResponse(order));

        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Order confirmation failed: {}", correlationId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("[{}] Order confirmation failed: {}", correlationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Order confirmation failed"));
        }
    }

    /**
     * POST /api/v1/orders/{orderId}/cancel
     * Cancel order.
     */
    @PostMapping("/{orderId}/cancel")
    @Timed(value = "order.cancel-order", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> cancelOrder(@PathVariable UUID orderId,
                                       @Valid @RequestBody CancelOrderRequest request,
                                       @RequestHeader("X-User-Id") UUID userId) {
        String correlationId = getCorrelationId();

        logger.info("[{}] Cancelling order: {} for user: {}", correlationId, orderId, userId);

        try {
            Order order = orderService.getOrderById(orderId, correlationId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            // Verify user owns the order
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("FORBIDDEN", "Access denied"));
            }

            orderService.cancelOrder(orderId, request.getReason(), correlationId);

            return ResponseEntity.ok(new SuccessResponse("Order cancelled successfully"));

        } catch (IllegalArgumentException e) {
            logger.warn("[{}] Order cancellation failed: {}", correlationId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("[{}] Order cancellation failed: {}", correlationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Order cancellation failed"));
        }
    }

    /**
     * GET /api/v1/orders/analytics
     * Get order analytics (admin endpoint).
     */
    @GetMapping("/analytics")
    @Timed(value = "order.get-analytics", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<?> getOrderAnalytics() {
        String correlationId = getCorrelationId();

        logger.debug("[{}] Getting order analytics", correlationId);

        OrderService.OrderAnalytics analytics = orderService.getOrderAnalytics(correlationId);

        return ResponseEntity.ok(new OrderAnalyticsResponse(analytics));
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

    // Request/Response DTOs

    public static class CreateOrderRequest {
        private java.util.List<OrderItemRequest> items;
        private String shippingMethod;
        private String paymentMethod;
        private String notes;

        // Getters and setters
        public java.util.List<OrderItemRequest> getItems() { return items; }
        public void setItems(java.util.List<OrderItemRequest> items) { this.items = items; }
        public String getShippingMethod() { return shippingMethod; }
        public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class OrderItemRequest {
        private UUID productId;
        private UUID variantId;
        private String sku;
        private String productTitle;
        private Integer quantity;
        private Double unitPrice;

        // Getters and setters
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public UUID getVariantId() { return variantId; }
        public void setVariantId(UUID variantId) { this.variantId = variantId; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    }

    public static class CancelOrderRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class OrderResponse {
        private UUID id;
        private String orderNumber;
        private String status;
        private String paymentStatus;
        private String fulfillmentStatus;
        private java.math.BigDecimal subtotal;
        private java.math.BigDecimal taxAmount;
        private java.math.BigDecimal shippingAmount;
        private java.math.BigDecimal discountAmount;
        private java.math.BigDecimal totalAmount;
        private String currency;
        private String paymentMethod;
        private String paymentReference;
        private String shippingMethod;
        private java.time.LocalDate estimatedDeliveryDate;
        private java.time.LocalDate actualDeliveryDate;
        private String trackingNumber;
        private String carrier;
        private String notes;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.util.List<OrderItemResponse> items;

        public OrderResponse(Order order) {
            this.id = order.getId();
            this.orderNumber = order.getOrderNumber();
            this.status = order.getStatus().toString();
            this.paymentStatus = order.getPaymentStatus().toString();
            this.fulfillmentStatus = order.getFulfillmentStatus().toString();
            this.subtotal = order.getSubtotal();
            this.taxAmount = order.getTaxAmount();
            this.shippingAmount = order.getShippingAmount();
            this.discountAmount = order.getDiscountAmount();
            this.totalAmount = order.getTotalAmount();
            this.currency = order.getCurrency();
            this.paymentMethod = order.getPaymentMethod();
            this.paymentReference = order.getPaymentReference();
            this.shippingMethod = order.getShippingMethod();
            this.estimatedDeliveryDate = order.getEstimatedDeliveryDate();
            this.actualDeliveryDate = order.getActualDeliveryDate();
            this.trackingNumber = order.getTrackingNumber();
            this.carrier = order.getCarrier();
            this.notes = order.getNotes();
            this.createdAt = order.getCreatedAt();
            this.updatedAt = order.getUpdatedAt();
            this.items = order.getItems().stream()
                    .map(OrderItemResponse::new)
                    .toList();
        }

        // Getters
        public UUID getId() { return id; }
        public String getOrderNumber() { return orderNumber; }
        public String getStatus() { return status; }
        public String getPaymentStatus() { return paymentStatus; }
        public String getFulfillmentStatus() { return fulfillmentStatus; }
        public java.math.BigDecimal getSubtotal() { return subtotal; }
        public java.math.BigDecimal getTaxAmount() { return taxAmount; }
        public java.math.BigDecimal getShippingAmount() { return shippingAmount; }
        public java.math.BigDecimal getDiscountAmount() { return discountAmount; }
        public java.math.BigDecimal getTotalAmount() { return totalAmount; }
        public String getCurrency() { return currency; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getPaymentReference() { return paymentReference; }
        public String getShippingMethod() { return shippingMethod; }
        public java.time.LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
        public java.time.LocalDate getActualDeliveryDate() { return actualDeliveryDate; }
        public String getTrackingNumber() { return trackingNumber; }
        public String getCarrier() { return carrier; }
        public String getNotes() { return notes; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public java.util.List<OrderItemResponse> getItems() { return items; }
    }

    public static class OrderItemResponse {
        private UUID id;
        private UUID productId;
        private UUID variantId;
        private String sku;
        private String productTitle;
        private String variantTitle;
        private Integer quantity;
        private java.math.BigDecimal unitPrice;
        private java.math.BigDecimal totalPrice;
        private java.math.BigDecimal taxAmount;
        private java.math.BigDecimal discountAmount;
        private String fulfillmentStatus;
        private String trackingNumber;
        private String carrier;
        private java.time.LocalDateTime shippedAt;
        private java.time.LocalDateTime deliveredAt;

        public OrderItemResponse(com.bookstore.order.domain.OrderItem item) {
            this.id = item.getId();
            this.productId = item.getProductId();
            this.variantId = item.getVariantId();
            this.sku = item.getSku();
            this.productTitle = item.getProductTitle();
            this.variantTitle = item.getVariantTitle();
            this.quantity = item.getQuantity();
            this.unitPrice = item.getUnitPrice();
            this.totalPrice = item.getTotalPrice();
            this.taxAmount = item.getTaxAmount();
            this.discountAmount = item.getDiscountAmount();
            this.fulfillmentStatus = item.getFulfillmentStatus().toString();
            this.trackingNumber = item.getTrackingNumber();
            this.carrier = item.getCarrier();
            this.shippedAt = item.getShippedAt();
            this.deliveredAt = item.getDeliveredAt();
        }

        // Getters
        public UUID getId() { return id; }
        public UUID getProductId() { return productId; }
        public UUID getVariantId() { return variantId; }
        public String getSku() { return sku; }
        public String getProductTitle() { return productTitle; }
        public String getVariantTitle() { return variantTitle; }
        public Integer getQuantity() { return quantity; }
        public java.math.BigDecimal getUnitPrice() { return unitPrice; }
        public java.math.BigDecimal getTotalPrice() { return totalPrice; }
        public java.math.BigDecimal getTaxAmount() { return taxAmount; }
        public java.math.BigDecimal getDiscountAmount() { return discountAmount; }
        public String getFulfillmentStatus() { return fulfillmentStatus; }
        public String getTrackingNumber() { return trackingNumber; }
        public String getCarrier() { return carrier; }
        public java.time.LocalDateTime getShippedAt() { return shippedAt; }
        public java.time.LocalDateTime getDeliveredAt() { return deliveredAt; }
    }

    public static class OrderPageResponse {
        private java.util.List<OrderResponse> content;
        private int pageNumber;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public OrderPageResponse(Page<Order> page) {
            this.content = page.getContent().stream()
                    .map(OrderResponse::new)
                    .toList();
            this.pageNumber = page.getNumber();
            this.pageSize = page.getSize();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.first = page.isFirst();
            this.last = page.isLast();
        }

        // Getters
        public java.util.List<OrderResponse> getContent() { return content; }
        public int getPageNumber() { return pageNumber; }
        public int getPageSize() { return pageSize; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isFirst() { return first; }
        public boolean isLast() { return last; }
    }

    public static class OrderAnalyticsResponse {
        private long totalOrders;
        private long paidOrders;
        private long shippedOrders;
        private long deliveredOrders;
        private long cancelledOrders;
        private java.math.BigDecimal totalRevenue;

        public OrderAnalyticsResponse(OrderService.OrderAnalytics analytics) {
            this.totalOrders = analytics.getTotalOrders();
            this.paidOrders = analytics.getPaidOrders();
            this.shippedOrders = analytics.getShippedOrders();
            this.deliveredOrders = analytics.getDeliveredOrders();
            this.cancelledOrders = analytics.getCancelledOrders();
            this.totalRevenue = analytics.getTotalRevenue();
        }

        // Getters
        public long getTotalOrders() { return totalOrders; }
        public long getPaidOrders() { return paidOrders; }
        public long getShippedOrders() { return shippedOrders; }
        public long getDeliveredOrders() { return deliveredOrders; }
        public long getCancelledOrders() { return cancelledOrders; }
        public java.math.BigDecimal getTotalRevenue() { return totalRevenue; }
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