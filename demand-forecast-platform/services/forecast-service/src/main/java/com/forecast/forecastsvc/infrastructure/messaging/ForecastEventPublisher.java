package com.forecast.forecastsvc.infrastructure.messaging;

import com.forecast.forecastsvc.domain.ForecastJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastEventPublisher {

    static final String TOPIC = "forecast.completed";

    private final KafkaTemplate<String, ForecastCompletedEvent> kafkaTemplate;

    public void publishForecastCompleted(ForecastJob job) {
        ForecastCompletedEvent event = new ForecastCompletedEvent(
                ForecastCompletedEvent.FORECAST_COMPLETED,
                job.getId(),
                job.getProductId(),
                job.getSku(),
                job.getStatus().name(),
                job.getResult().totalPredictedDemand(),
                job.getResult().mae(),
                null,
                Instant.now()
        );
        send(job.getProductId().toString(), event);
    }

    public void publishForecastFailed(ForecastJob job) {
        ForecastCompletedEvent event = new ForecastCompletedEvent(
                ForecastCompletedEvent.FORECAST_FAILED,
                job.getId(),
                job.getProductId(),
                job.getSku(),
                job.getStatus().name(),
                null,
                null,
                job.getErrorMessage(),
                Instant.now()
        );
        send(job.getProductId().toString(), event);
    }

    private void send(String key, ForecastCompletedEvent event) {
        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish forecast event for productId={}: {}",
                                event.productId(), ex.getMessage());
                    }
                });
    }
}
