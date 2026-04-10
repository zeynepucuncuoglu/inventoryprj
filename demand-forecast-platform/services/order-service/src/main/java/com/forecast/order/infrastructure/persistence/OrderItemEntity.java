package com.forecast.order.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderItemEntity is mapped as @Embeddable — no separate table, no separate ID.
 * It lives inside the orders table as columns.
 * This matches the domain model: OrderItem has no identity of its own.
 *
 * Alternative: @OneToMany with a separate order_items table — used when
 * you need to query items independently. For this use case, @ElementCollection
 * with @CollectionTable is cleaner.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
