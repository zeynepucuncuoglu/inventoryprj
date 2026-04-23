package com.forecast.forecastsvc.infrastructure.persistence;

import com.forecast.forecastsvc.domain.ForecastStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "forecast_jobs", indexes = {
        @Index(name = "idx_forecast_jobs_product_id", columnList = "product_id"),
        @Index(name = "idx_forecast_jobs_status", columnList = "status"),
        @Index(name = "idx_forecast_jobs_product_status", columnList = "product_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastJobEntity {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false)
    private int horizonDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ForecastStatus status;

    // Result fields — null until COMPLETED
    @Column(length = 50)
    private String modelUsed;

    @Column(precision = 10, scale = 4)
    private BigDecimal mae;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "forecast_points",
            joinColumns = @JoinColumn(name = "job_id")
    )
    @OrderBy("forecast_date ASC")
    @Builder.Default
    private List<ForecastPointEmbeddable> forecastPoints = new ArrayList<>();

    @Column(length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
