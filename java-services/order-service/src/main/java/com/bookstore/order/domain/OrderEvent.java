package com.bookstore.order.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order Event entity for tracking order lifecycle events.
 */
@Entity
@Table(name = "order_events", indexes = {
    @Index(name = "idx_order_events_order_id", columnList = "order_id"),
    @Index(name = "idx_order_events_event_type", columnList = "event_type"),
    @Index(name = "idx_order_events_created_at", columnList = "created_at")
})
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Size(max = 255)
    @Column(name = "actor_id")
    private String actorId;

    @Size(max = 100)
    @Column(name = "actor_type")
    private String actorType;

    @Size(max = 45)
    @Column(name = "ip_address")
    private String ipAddress;

    @Size(max = 500)
    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderEvent() {}

    public OrderEvent(Order order, EventType eventType, String description) {
        this.order = order;
        this.eventType = eventType;
        this.description = description;
    }

    public OrderEvent(Order order, EventType eventType, String description, String actorId, String actorType) {
        this.order = order;
        this.eventType = eventType;
        this.description = description;
        this.actorId = actorId;
        this.actorType = actorType;
    }

    public OrderEvent(Order order, String eventType, String description, String oldStatus, String newStatus, String createdBy) {
        this.order = order;
        // Convert string to EventType enum if possible, otherwise use a default
        try {
            this.eventType = EventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            this.eventType = EventType.ORDER_CREATED; // Default fallback
        }
        this.description = description;
        this.metadata = String.format("{\"oldStatus\":\"%s\",\"newStatus\":\"%s\"}", oldStatus, newStatus);
        this.actorId = createdBy;
        this.actorType = "USER";
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public enum EventType {
        ORDER_CREATED,
        ORDER_CONFIRMED,
        PAYMENT_INITIATED,
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        ORDER_PROCESSING,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        ORDER_CANCELLED,
        RETURN_REQUESTED,
        RETURN_APPROVED,
        RETURN_REJECTED,
        RETURN_RECEIVED,
        REFUND_PROCESSED,
        ORDER_COMPLETED,
        ORDER_FAILED
    }
}