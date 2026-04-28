package com.forecast.order.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "A single line item within an order")
public record OrderItemRequest(

        @Schema(description = "UUID of the product", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        @NotNull UUID productId,

        @Schema(description = "Product SKU", example = "WH-1000XM5")
        @NotBlank String sku,

        @Schema(description = "Quantity ordered (minimum 1)", example = "2")
        @Min(1) int quantity,

        @Schema(description = "Unit price at time of order", example = "299.99")
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice
) {}
