package com.forecast.order.application;

import com.forecast.order.application.dto.*;
import com.forecast.order.domain.*;
import com.forecast.order.infrastructure.messaging.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> OrderItem.of(i.productId(), i.sku(), i.quantity(), i.unitPrice()))
                .toList();

        // Domain enforces: must have at least one item
        Order order = Order.create(request.customerId(), items);

        Order saved = orderRepository.save(order);
        log.info("Order placed: id={} customerId={} total={}",
                saved.getId(), saved.getCustomerId(), saved.total());

        eventPublisher.publishOrderPlaced(saved);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(UUID customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public OrderResponse confirmOrder(UUID id) {
        Order order = findOrThrow(id);
        order.confirm();  // domain enforces state machine
        Order saved = orderRepository.save(order);
        log.info("Order confirmed: id={}", id);
        eventPublisher.publishOrderConfirmed(saved);
        return OrderResponse.from(saved);
    }

    @Transactional
    public OrderResponse shipOrder(UUID id) {
        Order order = findOrThrow(id);
        order.ship();
        Order saved = orderRepository.save(order);
        log.info("Order shipped: id={}", id);
        eventPublisher.publishOrderShipped(saved);
        return OrderResponse.from(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = findOrThrow(id);
        order.cancel();  // domain: can't cancel SHIPPED or DELIVERED
        Order saved = orderRepository.save(order);
        log.info("Order cancelled: id={}", id);
        eventPublisher.publishOrderCancelled(saved);
        return OrderResponse.from(saved);
    }

    private Order findOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
