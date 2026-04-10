package com.forecast.order.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Listens to product.events published by Product Service.
 *
 * Why does Order Service care about product events?
 * - If a product is deleted, we might want to flag related open orders
 * - If stock drops to zero, we might want to alert on pending orders
 *
 * For now: logs and provides the hook for future business rules.
 * In a real system, you'd inject OrderRepository here and act on events.
 *
 * Key design decision: @KafkaListener runs in its own thread pool.
 * Never block it with slow operations — hand off to async processing.
 */
@Slf4j
@Component
public class ProductEventConsumer {

    @KafkaListener(
            topics = "product.events",
            groupId = "order-service-product-events",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductEvent(
            @Payload ProductEventMessage event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received product event: type={} productId={} sku={} partition={} offset={}",
                event.eventType(), event.productId(), event.sku(), partition, offset);

        switch (event.eventType()) {
            case "STOCK_ADJUSTED" -> handleStockAdjusted(event);
            case "PRODUCT_DELETED" -> handleProductDeleted(event);
            default -> log.debug("No handler for product event type: {}", event.eventType());
        }
    }

    private void handleStockAdjusted(ProductEventMessage event) {
        if (event.stockQuantity() == 0) {
            log.warn("Product out of stock: sku={} productId={}", event.sku(), event.productId());
            // Future: query pending orders for this product, send alert
        }
    }

    private void handleProductDeleted(ProductEventMessage event) {
        log.warn("Product deleted: sku={} productId={} — check open orders",
                event.sku(), event.productId());
        // Future: find PENDING/CONFIRMED orders containing this product, flag them
    }
}
