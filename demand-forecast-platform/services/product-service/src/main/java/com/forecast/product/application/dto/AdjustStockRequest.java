package com.forecast.product.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Stock adjustment — positive to restock, negative to deduct")
public record AdjustStockRequest(

        @Schema(description = "Units to add (positive) or remove (negative). Cannot result in stock < 0.", example = "-10")
        @NotNull(message = "delta is required")
        Integer delta
) {}
