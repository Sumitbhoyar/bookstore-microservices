package com.bookstore.order.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.repository.OrderItemRepository;
import com.bookstore.order.repository.OrderRepository;
import com.bookstore.order.service.AuditService;
import com.bookstore.order.service.EventPublisherService;
import com.bookstore.order.service.InventoryService;
import com.bookstore.order.service.PaymentService;
import com.bookstore.order.service.ShippingService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ShippingService shippingService;

    @Mock
    private EventPublisherService eventPublisher;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderItem testOrderItem;
    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testOrderItem = createTestOrderItem();
        testOrder = createTestOrder();
    }

    private Order createTestOrder() {
        Order order = new Order(userId, Arrays.asList(testOrderItem));
        order.setId(orderId);
        order.setOrderNumber("ORD-001");
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPaymentStatus(Order.PaymentStatus.PENDING);
        order.setTotalAmount(BigDecimal.valueOf(29.99));
        return order;
    }

    private OrderItem createTestOrderItem() {
        OrderItem item = new OrderItem(
            UUID.randomUUID(), // productId
            UUID.randomUUID(), // variantId
            "SKU001",
            "Test Product",
            1,
            BigDecimal.valueOf(29.99)
        );
        item.setId(UUID.randomUUID());
        return item;
    }

    @Test
    void testCreateOrder_Success() {
        // Arrange
        java.util.List<OrderItem> items = Arrays.asList(testOrderItem);
        Order orderDetails = new Order();
        orderDetails.setShippingMethod("Standard");
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(orderId);
            return saved;
        });

        // Act
        Order result = orderService.createOrder(userId, items, orderDetails, ipAddress, userAgent, correlationId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("Standard", result.getShippingMethod());

        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).save(testOrderItem);
        verify(inventoryService).reserveInventory(items, null, correlationId);
        verify(eventPublisher).publishOrderCreatedEvent(result, correlationId);
        verify(auditService).logOrderCreated(orderId, userId, correlationId);
    }

    @Test
    void testCreateOrder_ValidationFailure() {
        // Arrange
        java.util.List<OrderItem> emptyItems = Arrays.asList();
        Order orderDetails = new Order();
        String ipAddress = "127.0.0.1";
        String userAgent = "Test Browser";
        String correlationId = "test-correlation-id";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> orderService.createOrder(userId, emptyItems, orderDetails, ipAddress, userAgent, correlationId));

        assertEquals("Order must contain at least one item", exception.getMessage());
    }

    @Test
    void testConfirmOrder_Success() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.confirmOrder(orderId, correlationId);

        // Assert
        assertEquals(Order.OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(Order.PaymentStatus.PENDING, result.getPaymentStatus());

        verify(orderRepository).save(testOrder);
        verify(paymentService).processPayment(testOrder, correlationId);
        verify(auditService).logOrderConfirmed(orderId, correlationId);
    }

    @Test
    void testConfirmOrder_OrderNotFound() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> orderService.confirmOrder(orderId, correlationId));

        assertEquals("Order not found: " + orderId, exception.getMessage());
    }

    @Test
    void testConfirmOrder_InvalidStatus() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> orderService.confirmOrder(orderId, correlationId));

        assertEquals("Order cannot be confirmed: CONFIRMED", exception.getMessage());
    }

    @Test
    void testHandlePaymentSuccess() {
        // Arrange
        String paymentReference = "pay_123456";
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.handlePaymentSuccess(orderId, paymentReference, correlationId);

        // Assert
        assertEquals(Order.PaymentStatus.PAID, testOrder.getPaymentStatus());
        assertEquals(Order.OrderStatus.PAID, testOrder.getStatus());
        assertNotNull(testOrder.getPaidAt());
        assertEquals(paymentReference, testOrder.getPaymentReference());

        verify(orderRepository).save(testOrder);
        verify(inventoryService).convertReservations(orderId, correlationId);
        verify(shippingService).initiateFulfillment(testOrder, correlationId);
        verify(eventPublisher).publishPaymentSuccessEvent(testOrder, correlationId);
        verify(auditService).logPaymentSuccess(orderId, paymentReference, correlationId);
    }

    @Test
    void testHandlePaymentFailure() {
        // Arrange
        String reason = "Card declined";
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.handlePaymentFailure(orderId, reason, correlationId);

        // Assert
        assertEquals(Order.PaymentStatus.FAILED, testOrder.getPaymentStatus());

        verify(orderRepository).save(testOrder);
        verify(inventoryService).releaseReservations(orderId, correlationId);
        verify(eventPublisher).publishPaymentFailureEvent(testOrder, reason, correlationId);
        verify(auditService).logPaymentFailure(orderId, reason, correlationId);
    }

    @Test
    void testShipOrderItems_Success() {
        // Arrange
        java.util.List<UUID> itemIds = Arrays.asList(testOrderItem.getId());
        String trackingNumber = "TRK123456";
        String carrier = "UPS";
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.countByOrderId(orderId)).thenReturn(1L);
        when(orderItemRepository.countByOrderIdAndFulfillmentStatus(orderId, OrderItem.FulfillmentStatus.FULFILLED)).thenReturn(1L);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.shipOrderItems(orderId, itemIds, trackingNumber, carrier, correlationId);

        // Assert
        verify(orderItemRepository).updateTrackingInfo(itemIds, trackingNumber, carrier);
        verify(orderRepository).save(testOrder);
        verify(eventPublisher).publishOrderShippedEvent(testOrder, correlationId);
        verify(auditService).logOrderShipped(orderId, trackingNumber, correlationId);
    }

    @Test
    void testMarkOrderDelivered_Success() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.SHIPPED);
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.markOrderDelivered(orderId, correlationId);

        // Assert
        assertEquals(Order.OrderStatus.DELIVERED, testOrder.getStatus());
        assertEquals(Order.FulfillmentStatus.FULFILLED, testOrder.getFulfillmentStatus());
        assertNotNull(testOrder.getDeliveredAt());

        verify(orderRepository).save(testOrder);
        verify(eventPublisher).publishOrderDeliveredEvent(testOrder, correlationId);
        verify(auditService).logOrderDelivered(orderId, correlationId);
    }

    @Test
    void testMarkOrderDelivered_InvalidStatus() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PENDING);
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> orderService.markOrderDelivered(orderId, correlationId));

        assertEquals("Order must be shipped before marking as delivered", exception.getMessage());
    }

    @Test
    void testCancelOrder_Success() {
        // Arrange
        String reason = "Customer request";
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.cancelOrder(orderId, reason, correlationId);

        // Assert
        assertEquals(Order.OrderStatus.CANCELLED, testOrder.getStatus());
        assertNotNull(testOrder.getCancelledAt());

        verify(orderRepository).save(testOrder);
        verify(inventoryService).releaseReservations(orderId, correlationId);
        verify(eventPublisher).publishOrderCancelledEvent(testOrder, reason, correlationId);
        verify(auditService).logOrderCancelled(orderId, reason, correlationId);
    }

    @Test
    void testCancelOrder_InvalidStatus() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        String reason = "Too late";
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> orderService.cancelOrder(orderId, reason, correlationId));

        assertEquals("Order cannot be cancelled: DELIVERED", exception.getMessage());
    }

    @Test
    void testGetOrderById() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        // Act
        Optional<Order> result = orderService.getOrderById(orderId, correlationId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testOrder, result.get());
    }

    @Test
    void testGetOrderByNumber() {
        // Arrange
        String orderNumber = "ORD-001";
        String correlationId = "test-correlation-id";

        when(orderRepository.findByOrderNumber(orderNumber)).thenReturn(Optional.of(testOrder));

        // Act
        Optional<Order> result = orderService.getOrderByNumber(orderNumber, correlationId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testOrder, result.get());
    }

    @Test
    void testGetOrderAnalytics() {
        // Arrange
        String correlationId = "test-correlation-id";

        when(orderRepository.count()).thenReturn(100L);
        when(orderRepository.countByPaymentStatus(Order.PaymentStatus.PAID)).thenReturn(80L);
        when(orderRepository.countByStatus(Order.OrderStatus.SHIPPED)).thenReturn(70L);
        when(orderRepository.countByStatus(Order.OrderStatus.DELIVERED)).thenReturn(65L);
        when(orderRepository.countByStatus(Order.OrderStatus.CANCELLED)).thenReturn(5L);
        when(orderRepository.sumTotalAmountByDateRange(any(), any())).thenReturn(BigDecimal.valueOf(5000.00));

        // Act
        OrderService.OrderAnalytics analytics = orderService.getOrderAnalytics(correlationId);

        // Assert
        assertEquals(100L, analytics.getTotalOrders());
        assertEquals(80L, analytics.getPaidOrders());
        assertEquals(70L, analytics.getShippedOrders());
        assertEquals(65L, analytics.getDeliveredOrders());
        assertEquals(5L, analytics.getCancelledOrders());
        assertEquals(BigDecimal.valueOf(5000.00), analytics.getTotalRevenue());
    }
}