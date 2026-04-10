package com.forecast.order.infrastructure.persistence;

import com.forecast.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
    List<OrderEntity> findByCustomerId(UUID customerId);
    List<OrderEntity> findByStatus(OrderStatus status);
}
