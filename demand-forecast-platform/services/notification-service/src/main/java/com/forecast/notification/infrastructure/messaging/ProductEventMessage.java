package com.forecast.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Inbound shape from product.events topic
public record ProductEventMessage(
        String eventType,
        UUID productId,
        String sku,
        String category,
        int stockQuantity,
        Integer stockDelta,
        BigDecimal price,
        Instant occurredAt
) {}
