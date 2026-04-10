package com.forecast.product.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event schema published to Kafka.
 * This is part of your public API contract — consumers depend on this shape.
 * Never remove fields; add new optional fields with defaults instead.
 */
public record ProductEvent(
        String eventType,      // PRODUCT_CREATED | STOCK_ADJUSTED | PRODUCT_DELETED
        UUID productId,
        String sku,
        String category,
        int stockQuantity,
        Integer stockDelta,    // null unless STOCK_ADJUSTED
        BigDecimal price,
        Instant occurredAt
) {
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String STOCK_ADJUSTED = "STOCK_ADJUSTED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";
}
