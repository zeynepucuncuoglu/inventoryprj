package com.forecast.forecastsvc.application.dto;

import com.forecast.forecastsvc.domain.ForecastJob;
import com.forecast.forecastsvc.domain.ForecastPoint;
import com.forecast.forecastsvc.domain.ForecastStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ForecastJobResponse(
        UUID id,
        UUID productId,
        String sku,
        int horizonDays,
        ForecastStatus status,
        ForecastResultDto result,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public record ForecastResultDto(
            String modelUsed,
            BigDecimal mae,
            BigDecimal totalPredictedDemand,
            List<ForecastPointDto> points
    ) {}

    public record ForecastPointDto(
            LocalDate date,
            BigDecimal predictedQuantity,
            BigDecimal lowerBound,
            BigDecimal upperBound
    ) {}

    public static ForecastJobResponse from(ForecastJob job) {
        ForecastResultDto resultDto = null;
        if (job.getResult() != null) {
            List<ForecastPointDto> points = job.getResult().points().stream()
                    .map(p -> new ForecastPointDto(
                            p.date(), p.predictedQuantity(), p.lowerBound(), p.upperBound()))
                    .toList();
            resultDto = new ForecastResultDto(
                    job.getResult().modelUsed(),
                    job.getResult().mae(),
                    job.getResult().totalPredictedDemand(),
                    points
            );
        }

        return new ForecastJobResponse(
                job.getId(), job.getProductId(), job.getSku(),
                job.getHorizonDays(), job.getStatus(),
                resultDto, job.getErrorMessage(),
                job.getCreatedAt(), job.getUpdatedAt()
        );
    }
}
