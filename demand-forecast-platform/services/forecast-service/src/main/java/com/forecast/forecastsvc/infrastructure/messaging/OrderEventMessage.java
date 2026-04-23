package com.forecast.forecastsvc.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Inbound shape from order.events — must match OrderEvent in order-service
public record OrderEventMessage(
        String eventType,
        UUID orderId,
        UUID customerId,
        List<OrderItem> items,
        BigDecimal total,
        String status,
        Instant occurredAt
) {
    public record OrderItem(
            UUID productId,
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
