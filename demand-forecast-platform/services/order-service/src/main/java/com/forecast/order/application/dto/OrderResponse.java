package com.forecast.order.application.dto;

import com.forecast.order.domain.Order;
import com.forecast.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        List<OrderItemResponse> items,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getStatus(),
                order.total(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
