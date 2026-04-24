package com.forecast.notification.infrastructure.messaging;

import com.forecast.notification.application.AlertEvaluator;
import com.forecast.notification.application.NotificationService;
import com.forecast.notification.domain.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Listens to forecast.completed and triggers alerts based on forecast results.
 *
 * Two scenarios:
 * 1. FORECAST_FAILED   → always alert ops team
 * 2. FORECAST_COMPLETED → evaluate demand surge / low demand rules
 *
 * Note: currentStock is not in the forecast event — in production you'd
 * either include it in the event (fat event) or call product-service.
 * Here we use a placeholder of 100 to demonstrate the rule logic.
 * Real implementation: enrich the ForecastCompletedEvent with currentStock
 * in the Forecast Service before publishing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastEventConsumer {

    private final AlertEvaluator alertEvaluator;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "forecast.completed",
            groupId = "notification-service-forecast-events"
    )
    public void onForecastEvent(
            @Payload ForecastEventMessage event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.debug("Received forecast event: type={} sku={} partition={} offset={}",
                event.eventType(), event.sku(), partition, offset);

        switch (event.eventType()) {
            case "FORECAST_FAILED" -> {
                Alert alert = alertEvaluator.evaluateForecastFailure(
                        event.productId().toString(),
                        event.sku(),
                        event.errorMessage()
                );
                notificationService.dispatch(alert);
            }
            case "FORECAST_COMPLETED" -> {
                if (event.totalPredictedDemand() == null) return;

                // Placeholder: in production, include currentStock in the event
                int currentStock = 100;

                List<Alert> alerts = alertEvaluator.evaluateForecastResult(
                        event.productId().toString(),
                        event.sku(),
                        event.totalPredictedDemand(),
                        currentStock
                );
                notificationService.dispatchAll(alerts);
            }
            default -> log.debug("No handler for forecast event type: {}", event.eventType());
        }
    }
}
