package com.forecast.forecastsvc.application;

import java.util.UUID;

public class ForecastJobNotFoundException extends RuntimeException {
    public ForecastJobNotFoundException(UUID id) {
        super("Forecast job not found: " + id);
    }
}
