package com.forecast.forecastsvc.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastPointEmbeddable {

    @Column(name = "forecast_date", nullable = false)
    private LocalDate date;

    @Column(name = "predicted_quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedQuantity;

    @Column(name = "lower_bound", nullable = false, precision = 12, scale = 4)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", nullable = false, precision = 12, scale = 4)
    private BigDecimal upperBound;
}
