package com.forecast.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound event published to order.events topic.
 * Consumers: forecast-service, notification-service.
 *
 * Include enough data so consumers don't need to call back.
 * This is the "fat event" pattern — avoids chatty inter-service calls.
 */
public record OrderEvent(
        String eventType,
        UUID orderId,
        UUID customerId,
        List<OrderEventItem> items,
        BigDecimal total,
        String status,
        Instant occurredAt
) {
    public static final String ORDER_PLACED    = "ORDER_PLACED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_SHIPPED   = "ORDER_SHIPPED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    public record OrderEventItem(
            UUID productId,
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
