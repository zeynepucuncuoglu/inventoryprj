package com.forecast.forecastsvc.infrastructure.persistence;

import com.forecast.forecastsvc.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ForecastJobRepositoryAdapter implements ForecastJobRepository {

    private final ForecastJobJpaRepository jpa;

    @Override
    public ForecastJob save(ForecastJob job) {
        if (job.getId() != null) {
            return jpa.findById(job.getId())
                    .map(existing -> {
                        updateEntity(existing, job);
                        return toDomain(jpa.save(existing));
                    })
                    .orElseGet(() -> toDomain(jpa.save(toEntity(job))));
        }
        return toDomain(jpa.save(toEntity(job)));
    }

    private void updateEntity(ForecastJobEntity existing, ForecastJob job) {
        existing.setStatus(job.getStatus());
        existing.setErrorMessage(job.getErrorMessage());
        if (job.getResult() != null) {
            existing.setModelUsed(job.getResult().modelUsed());
            existing.setMae(job.getResult().mae());
            existing.setForecastPoints(
                    job.getResult().points().stream()
                            .map(p -> ForecastPointEmbeddable.builder()
                                    .date(p.date())
                                    .predictedQuantity(p.predictedQuantity())
                                    .lowerBound(p.lowerBound())
                                    .upperBound(p.upperBound())
                                    .build())
                            .collect(java.util.stream.Collectors.toList())
            );
        }
    }

    @Override
    public Optional<ForecastJob> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<ForecastJob> findByProductId(UUID productId) {
        return jpa.findByProductId(productId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ForecastJob> findLatestCompletedByProductId(UUID productId) {
        return jpa.findLatestCompletedByProductId(productId).map(this::toDomain);
    }

    @Override
    public List<ForecastJob> findByStatus(ForecastStatus status) {
        return jpa.findByStatus(status).stream().map(this::toDomain).toList();
    }

    private ForecastJobEntity toEntity(ForecastJob job) {
        ForecastJobEntity entity = ForecastJobEntity.builder()
                .id(job.getId())
                .productId(job.getProductId())
                .sku(job.getSku())
                .horizonDays(job.getHorizonDays())
                .status(job.getStatus())
                .errorMessage(job.getErrorMessage())
                .build();

        if (job.getResult() != null) {
            entity.setModelUsed(job.getResult().modelUsed());
            entity.setMae(job.getResult().mae());
            entity.setForecastPoints(
                    job.getResult().points().stream()
                            .map(p -> ForecastPointEmbeddable.builder()
                                    .date(p.date())
                                    .predictedQuantity(p.predictedQuantity())
                                    .lowerBound(p.lowerBound())
                                    .upperBound(p.upperBound())
                                    .build())
                            .collect(java.util.stream.Collectors.toList())
            );
        }
        return entity;
    }

    private ForecastJob toDomain(ForecastJobEntity entity) {
        ForecastResult result = null;
        if (entity.getModelUsed() != null && entity.getForecastPoints() != null) {
            List<ForecastPoint> points = entity.getForecastPoints().stream()
                    .map(p -> new ForecastPoint(
                            p.getDate(), p.getPredictedQuantity(),
                            p.getLowerBound(), p.getUpperBound()))
                    .toList();
            result = new ForecastResult(entity.getModelUsed(), points, entity.getMae());
        }

        return ForecastJob.reconstitute(
                entity.getId(), entity.getProductId(), entity.getSku(),
                entity.getHorizonDays(), entity.getStatus(),
                result, entity.getErrorMessage(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
