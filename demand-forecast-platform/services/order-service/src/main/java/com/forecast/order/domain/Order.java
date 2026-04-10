package com.forecast.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order is the Aggregate Root.
 *
 * Aggregate Root means:
 * - Order is the single entry point to this cluster of objects
 * - Nobody touches OrderItem directly — they go through Order
 * - All business rules about orders live here
 *
 * Business rules enforced here:
 * - An order must have at least one item
 * - A delivered order cannot be cancelled
 * - A shipped order cannot be cancelled
 * - Total is always computed from items — never stored manually
 */
public class Order {

    private final UUID id;
    private final UUID customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public static Order create(UUID customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        return new Order(
                UUID.randomUUID(),
                customerId,
                new ArrayList<>(items),
                OrderStatus.PENDING,
                Instant.now(),
                Instant.now()
        );
    }

    public static Order reconstitute(UUID id, UUID customerId, List<OrderItem> items,
                                      OrderStatus status, Instant createdAt, Instant updatedAt) {
        return new Order(id, customerId, new ArrayList<>(items), status, createdAt, updatedAt);
    }

    private Order(UUID id, UUID customerId, List<OrderItem> items,
                  OrderStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = items;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Only PENDING orders can be confirmed. Current: " + this.status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void ship() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    "Only CONFIRMED orders can be shipped. Current: " + this.status);
        }
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = Instant.now();
    }

    public void deliver() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new InvalidOrderStateException(
                    "Only SHIPPED orders can be delivered. Current: " + this.status);
        }
        this.status = OrderStatus.DELIVERED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException(
                    "Cannot cancel an order that is already " + this.status);
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order is already cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    // Total is always derived — never accept it as input
    public BigDecimal total() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
