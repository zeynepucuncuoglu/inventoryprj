package com.forecast.forecastsvc.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Value Object — a single day's forecast prediction.
 * No identity, no repository. Lives inside ForecastResult.
 */
public record ForecastPoint(
        LocalDate date,
        BigDecimal predictedQuantity,
        BigDecimal lowerBound,
        BigDecimal upperBound
) {
    public ForecastPoint {
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("lowerBound cannot exceed upperBound");
        }
        if (predictedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("predictedQuantity cannot be negative");
        }
    }
}
