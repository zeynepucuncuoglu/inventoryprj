package com.forecast.forecastsvc.domain;

/**
 * ForecastJob lifecycle:
 * PENDING → RUNNING → COMPLETED
 * PENDING → RUNNING → FAILED
 */
public enum ForecastStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
