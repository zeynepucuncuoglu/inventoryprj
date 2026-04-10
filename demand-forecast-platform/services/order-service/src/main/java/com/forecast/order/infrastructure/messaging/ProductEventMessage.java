package com.forecast.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound message shape from product.events topic.
 * Must match ProductEvent published by Product Service.
 * Keep in sync manually or use a shared schema registry (Avro/Protobuf) in production.
 */
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
