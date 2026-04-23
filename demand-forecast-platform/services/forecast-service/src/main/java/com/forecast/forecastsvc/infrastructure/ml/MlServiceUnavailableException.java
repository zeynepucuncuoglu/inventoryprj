package com.forecast.forecastsvc.infrastructure.ml;

public class MlServiceUnavailableException extends RuntimeException {
    public MlServiceUnavailableException(String message) {
        super(message);
    }
}
