package com.forecast.product.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "sku is required")
        @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "SKU must be 3-20 uppercase alphanumeric characters")
        String sku,

        @NotBlank(message = "category is required")
        String category,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive")
        BigDecimal price,

        @Min(value = 0, message = "initialStock cannot be negative")
        int initialStock
) {}
