package com.forecast.forecastsvc.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForecastJobRepository {
    ForecastJob save(ForecastJob job);
    Optional<ForecastJob> findById(UUID id);
    List<ForecastJob> findByProductId(UUID productId);
    Optional<ForecastJob> findLatestCompletedByProductId(UUID productId);
    List<ForecastJob> findByStatus(ForecastStatus status);
}
