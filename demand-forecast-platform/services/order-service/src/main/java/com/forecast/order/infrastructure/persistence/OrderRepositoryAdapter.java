package com.forecast.order.infrastructure.persistence;

import com.forecast.order.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpa;

    @Override
    public Order save(Order order) {
        if (order.getId() != null) {
            return jpa.findById(order.getId())
                    .map(existing -> {
                        existing.setStatus(order.getStatus());
                        existing.setItems(toItemEntities(order.getItems()));
                        return toDomain(jpa.save(existing));
                    })
                    .orElseGet(() -> toDomain(jpa.save(toEntity(order))));
        }
        return toDomain(jpa.save(toEntity(order)));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(UUID customerId) {
        return jpa.findByCustomerId(customerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return jpa.findByStatus(status).stream().map(this::toDomain).toList();
    }

    private List<OrderItemEntity> toItemEntities(List<OrderItem> items) {
        List<OrderItemEntity> result = new java.util.ArrayList<>();
        for (OrderItem item : items) {
            result.add(new OrderItemEntity(
                    item.getProductId(),
                    item.getSku(),
                    item.getQuantity(),
                    item.getUnitPrice()
            ));
        }
        return result;
    }

    private OrderEntity toEntity(Order order) {
        List<OrderItemEntity> itemEntities = toItemEntities(order.getItems());

        return OrderEntity.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .items(new java.util.ArrayList<>(itemEntities))
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(e -> OrderItem.of(e.getProductId(), e.getSku(),
                        e.getQuantity(), e.getUnitPrice()))
                .toList();

        return Order.reconstitute(
                entity.getId(),
                entity.getCustomerId(),
                items,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
