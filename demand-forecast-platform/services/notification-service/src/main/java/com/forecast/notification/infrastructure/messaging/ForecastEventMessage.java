package com.forecast.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Inbound shape from forecast.completed topic
public record ForecastEventMessage(
        String eventType,
        UUID jobId,
        UUID productId,
        String sku,
        String status,
        BigDecimal totalPredictedDemand,
        BigDecimal mae,
        String errorMessage,
        Instant occurredAt
) {}
