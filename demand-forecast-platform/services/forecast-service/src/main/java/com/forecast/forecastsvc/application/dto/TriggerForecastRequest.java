package com.forecast.forecastsvc.application.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record TriggerForecastRequest(
        @NotNull UUID productId,
        @NotBlank String sku,
        @Min(1) @Max(365) int horizonDays
) {}
