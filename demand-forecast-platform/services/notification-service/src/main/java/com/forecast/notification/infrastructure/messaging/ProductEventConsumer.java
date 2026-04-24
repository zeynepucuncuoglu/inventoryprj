package com.forecast.notification.infrastructure.messaging;

import com.forecast.notification.application.AlertEvaluator;
import com.forecast.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Listens to product.events and fires LOW_STOCK alerts
 * whenever stock is adjusted downward past the threshold.
 *
 * We only act on STOCK_ADJUSTED events — not CREATE or DELETE.
 * A newly created product with zero stock is intentional.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final AlertEvaluator alertEvaluator;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "product.events",
            groupId = "notification-service-product-events"
    )
    public void onProductEvent(
            @Payload ProductEventMessage event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.debug("Received product event: type={} sku={} stock={} partition={} offset={}",
                event.eventType(), event.sku(), event.stockQuantity(), partition, offset);

        if (!"STOCK_ADJUSTED".equals(event.eventType())) {
            return;
        }

        // Only alert on downward adjustments
        boolean isDecrease = event.stockDelta() != null && event.stockDelta() < 0;
        if (!isDecrease) {
            return;
        }

        alertEvaluator.evaluateStockEvent(
                event.productId().toString(),
                event.sku(),
                event.stockQuantity()
        ).ifPresent(notificationService::dispatch);
    }
}
