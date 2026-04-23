package com.forecast.forecastsvc.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesDataPoint(
        LocalDate date,
        BigDecimal quantity
) {}
