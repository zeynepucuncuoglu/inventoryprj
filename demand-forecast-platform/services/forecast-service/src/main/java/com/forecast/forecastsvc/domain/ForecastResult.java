package com.forecast.forecastsvc.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Value Object — the completed forecast result.
 * Immutable once created. Owned by ForecastJob.
 */
public record ForecastResult(
        String modelUsed,
        List<ForecastPoint> points,
        BigDecimal mae   // Mean Absolute Error — model accuracy indicator
) {
    public ForecastResult {
        points = Collections.unmodifiableList(points);
    }

    public BigDecimal totalPredictedDemand() {
        return points.stream()
                .map(ForecastPoint::predictedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
