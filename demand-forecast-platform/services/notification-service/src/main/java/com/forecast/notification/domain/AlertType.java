package com.forecast.notification.domain;

public enum AlertType {
    LOW_STOCK,           // stock quantity dropped below threshold
    DEMAND_SURGE,        // forecast predicts demand >> current stock
    FORECAST_FAILED,     // ML inference failed — forecasting unavailable
    LOW_DEMAND_FORECAST  // predicted demand is very low — potential overstock
}
