package com.forecast.product.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(description = "Request body for creating a new product")
public record CreateProductRequest(

        @Schema(description = "Display name", example = "Wireless Headphones")
        @NotBlank(message = "name is required")
        String name,

        @Schema(description = "Unique stock-keeping unit (3-20 uppercase alphanumeric)", example = "WH-1000XM5")
        @NotBlank(message = "sku is required")
        @Pattern(regexp = "^[A-Z0-9\\-]{3,20}$", message = "SKU must be 3-20 uppercase alphanumeric characters")
        String sku,

        @Schema(description = "Product category", example = "Electronics")
        @NotBlank(message = "category is required")
        String category,

        @Schema(description = "Unit price (must be > 0)", example = "299.99")
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive")
        BigDecimal price,

        @Schema(description = "Initial stock quantity (0 or more)", example = "150")
        @Min(value = 0, message = "initialStock cannot be negative")
        int initialStock
) {}
