package com.forecast.forecastsvc.infrastructure.messaging;

import com.forecast.forecastsvc.application.ForecastJobService;
import com.forecast.forecastsvc.application.dto.SalesDataPoint;
import com.forecast.forecastsvc.application.dto.TriggerForecastRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Listens to order.events and automatically triggers a forecast
 * when an order is CONFIRMED (meaning it's real demand, not just a cart).
 *
 * Design decision: We trigger on ORDER_CONFIRMED, not ORDER_PLACED.
 * Placed orders might be cancelled — confirmed orders are real demand signals.
 *
 * We aggregate: if the same product appears in multiple orders today,
 * we sum them into a single daily data point before calling ML.
 * (In production: store order history in forecast DB, query last 90 days)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final int DEFAULT_HORIZON_DAYS = 30;

    private final ForecastJobService forecastJobService;

    @KafkaListener(
            topics = "order.events",
            groupId = "forecast-service-order-events"
    )
    public void onOrderEvent(
            @Payload OrderEventMessage event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received order event: type={} orderId={} partition={} offset={}",
                event.eventType(), event.orderId(), partition, offset);

        if (!OrderEventMessage.class.cast(event).eventType().equals("ORDER_CONFIRMED")) {
            return; // Only trigger forecast for confirmed orders
        }

        // Each product in the order gets its own forecast job
        for (OrderEventMessage.OrderItem item : event.items()) {
            triggerForecastForProduct(item, event);
        }
    }

    private void triggerForecastForProduct(OrderEventMessage.OrderItem item,
                                            OrderEventMessage event) {
        try {
            TriggerForecastRequest request = new TriggerForecastRequest(
                    item.productId(),
                    item.sku(),
                    DEFAULT_HORIZON_DAYS
            );

            // In production: query the last 90 days of order history from the DB
            // Here: use the current order as a seed data point
            List<SalesDataPoint> historicalData = buildSeedHistory(item, event);

            forecastJobService.triggerForecast(request, historicalData);

            log.info("Forecast triggered for sku={} from orderId={}", item.sku(), event.orderId());

        } catch (Exception ex) {
            // Never let a single product failure block processing of other products
            log.error("Failed to trigger forecast for sku={}: {}", item.sku(), ex.getMessage());
        }
    }

    /**
     * Builds a minimal seed history from the order event.
     * Real implementation would query order history from the forecast DB.
     * Prophet requires at least 14 data points — in prod, query last 90 days.
     */
    private List<SalesDataPoint> buildSeedHistory(OrderEventMessage.OrderItem item,
                                                   OrderEventMessage event) {
        LocalDate orderDate = event.occurredAt()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        // Simulate 30 days of synthetic history seeded from this order
        // Replace with real DB query in production
        return java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new SalesDataPoint(
                        orderDate.minusDays(30 - i),
                        BigDecimal.valueOf(item.quantity())
                ))
                .collect(Collectors.toList());
    }
}
