package com.forecast.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderItem is a Value Object inside the Order aggregate.
 * It has no identity of its own — it only exists as part of an Order.
 * You never fetch an OrderItem directly; you always go through Order.
 */
public class OrderItem {

    private final UUID productId;
    private final String sku;
    private final int quantity;
    private final BigDecimal unitPrice;

    public static OrderItem of(UUID productId, String sku, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price must be non-negative");
        }
        return new OrderItem(productId, sku, quantity, unitPrice);
    }

    private OrderItem(UUID productId, String sku, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
