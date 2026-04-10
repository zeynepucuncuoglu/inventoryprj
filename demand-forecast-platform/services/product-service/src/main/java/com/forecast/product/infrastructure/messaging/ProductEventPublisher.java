package com.forecast.product.infrastructure.messaging;

import com.forecast.product.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    static final String TOPIC = "product.events";

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    public void publishProductCreated(Product product) {
        ProductEvent event = new ProductEvent(
                ProductEvent.PRODUCT_CREATED,
                product.getId(),
                product.getSku(),
                product.getCategory(),
                product.getStockQuantity(),
                null,
                product.getPrice(),
                Instant.now()
        );
        send(product.getId().toString(), event);
    }

    public void publishStockAdjusted(Product product, int delta) {
        ProductEvent event = new ProductEvent(
                ProductEvent.STOCK_ADJUSTED,
                product.getId(),
                product.getSku(),
                product.getCategory(),
                product.getStockQuantity(),
                delta,
                product.getPrice(),
                Instant.now()
        );
        send(product.getId().toString(), event);
    }

    /**
     * Key = productId ensures all events for the same product land
     * on the same partition, preserving event ordering per product.
     */
    private void send(String key, ProductEvent event) {
        CompletableFuture<SendResult<String, ProductEvent>> future =
                kafkaTemplate.send(TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event type={} productId={}: {}",
                        event.eventType(), event.productId(), ex.getMessage());
            } else {
                log.debug("Published event type={} productId={} offset={}",
                        event.eventType(), event.productId(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
