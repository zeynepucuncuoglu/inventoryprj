package com.forecast.forecastsvc.application.dto;

import java.util.List;

// Sent TO the Python ML service
public record MlForecastRequest(
        String product_id,
        String sku,
        int horizon_days,
        List<SalesDataPoint> historical_data,
        String model
) {}
