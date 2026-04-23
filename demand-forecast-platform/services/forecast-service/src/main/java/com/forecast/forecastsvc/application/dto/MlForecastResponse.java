package com.forecast.forecastsvc.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Received FROM the Python ML service
public record MlForecastResponse(
        String product_id,
        String sku,
        String model_used,
        List<MlForecastPoint> forecast,
        BigDecimal mae
) {
    public record MlForecastPoint(
            LocalDate date,
            BigDecimal predicted_quantity,
            BigDecimal lower_bound,
            BigDecimal upper_bound
    ) {}
}
