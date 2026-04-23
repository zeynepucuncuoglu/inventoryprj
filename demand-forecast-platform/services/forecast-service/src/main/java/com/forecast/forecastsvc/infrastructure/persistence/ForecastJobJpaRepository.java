package com.forecast.forecastsvc.infrastructure.persistence;

import com.forecast.forecastsvc.domain.ForecastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ForecastJobJpaRepository extends JpaRepository<ForecastJobEntity, UUID> {

    List<ForecastJobEntity> findByProductId(UUID productId);

    List<ForecastJobEntity> findByStatus(ForecastStatus status);

    @Query("""
            SELECT j FROM ForecastJobEntity j
            WHERE j.productId = :productId
              AND j.status = 'COMPLETED'
            ORDER BY j.updatedAt DESC
            LIMIT 1
            """)
    Optional<ForecastJobEntity> findLatestCompletedByProductId(UUID productId);
}
