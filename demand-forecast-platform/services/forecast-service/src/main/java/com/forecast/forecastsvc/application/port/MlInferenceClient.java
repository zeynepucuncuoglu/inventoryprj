package com.forecast.forecastsvc.application.port;

import com.forecast.forecastsvc.application.dto.MlForecastRequest;
import com.forecast.forecastsvc.application.dto.MlForecastResponse;

/**
 * Port — defines what the application needs from the ML service.
 * The infrastructure layer provides the real HTTP implementation.
 * In tests, we swap this with a mock — no real HTTP calls needed.
 */
public interface MlInferenceClient {
    MlForecastResponse requestForecast(MlForecastRequest request);
}
