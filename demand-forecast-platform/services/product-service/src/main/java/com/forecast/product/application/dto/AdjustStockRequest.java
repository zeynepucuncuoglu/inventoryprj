package com.forecast.product.application.dto;

import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(
        @NotNull(message = "delta is required")
        Integer delta  // positive = restock, negative = deduct
) {}
