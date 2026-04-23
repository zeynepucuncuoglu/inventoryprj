package com.forecast.forecastsvc.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Published to forecast.completed topic
public record ForecastCompletedEvent(
        String eventType,
        UUID jobId,
        UUID productId,
        String sku,
        String status,         // COMPLETED or FAILED
        BigDecimal totalPredictedDemand,
        BigDecimal mae,
        String errorMessage,   // null if COMPLETED
        Instant occurredAt
) {
    public static final String FORECAST_COMPLETED = "FORECAST_COMPLETED";
    public static final String FORECAST_FAILED    = "FORECAST_FAILED";
}
