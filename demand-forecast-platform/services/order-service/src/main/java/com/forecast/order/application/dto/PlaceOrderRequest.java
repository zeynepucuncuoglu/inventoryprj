package com.forecast.order.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for placing a new order")
public record PlaceOrderRequest(

        @Schema(description = "UUID of the customer placing the order", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        @NotNull UUID customerId,

        @Schema(description = "One or more order items (minimum 1 required)")
        @NotEmpty(message = "Order must have at least one item")
        @Valid
        List<OrderItemRequest> items
) {}
