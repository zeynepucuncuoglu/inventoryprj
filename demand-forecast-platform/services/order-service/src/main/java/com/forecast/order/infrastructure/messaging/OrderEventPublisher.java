package com.forecast.order.infrastructure.messaging;

import com.forecast.order.domain.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    static final String TOPIC = "order.events";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderPlaced(Order order) {
        send(OrderEvent.ORDER_PLACED, order);
    }

    public void publishOrderConfirmed(Order order) {
        send(OrderEvent.ORDER_CONFIRMED, order);
    }

    public void publishOrderShipped(Order order) {
        send(OrderEvent.ORDER_SHIPPED, order);
    }

    public void publishOrderCancelled(Order order) {
        send(OrderEvent.ORDER_CANCELLED, order);
    }

    private void send(String eventType, Order order) {
        OrderEvent event = new OrderEvent(
                eventType,
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream()
                        .map(i -> new OrderEvent.OrderEventItem(
                                i.getProductId(), i.getSku(),
                                i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                order.total(),
                order.getStatus().name(),
                Instant.now()
        );

        // Key = orderId — all events for the same order go to the same partition
        kafkaTemplate.send(TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for orderId={}: {}",
                                eventType, order.getId(), ex.getMessage());
                    } else {
                        log.debug("Published {} for orderId={}", eventType, order.getId());
                    }
                });
    }
}
