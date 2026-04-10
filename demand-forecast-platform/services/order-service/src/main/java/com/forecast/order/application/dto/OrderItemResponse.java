package com.forecast.order.application.dto;

import com.forecast.order.domain.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.subtotal()
        );
    }
}
