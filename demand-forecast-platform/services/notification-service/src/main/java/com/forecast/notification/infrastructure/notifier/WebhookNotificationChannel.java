package com.forecast.notification.infrastructure.notifier;

import com.forecast.notification.domain.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Sends alerts as HTTP POST to a webhook URL.
 * Compatible with Slack incoming webhooks, Teams, PagerDuty, etc.
 *
 * Only active when notification.webhook.enabled=true.
 * This prevents accidental pings during local dev or tests.
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "notification.webhook.enabled", havingValue = "true")
public class WebhookNotificationChannel implements NotificationChannel {

    private final WebClient webClient;
    private final String webhookUrl;
    private final int timeoutSeconds;

    public WebhookNotificationChannel(
            WebClient.Builder webClientBuilder,
            @Value("${notification.webhook.url}") String webhookUrl,
            @Value("${notification.webhook.timeout-seconds}") int timeoutSeconds) {
        this.webClient = webClientBuilder.build();
        this.webhookUrl = webhookUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void send(Alert alert) {
        // Slack-compatible payload shape
        Map<String, Object> payload = Map.of(
                "text", formatSlackMessage(alert),
                "attachments", java.util.List.of(Map.of(
                        "color", severityColor(alert),
                        "fields", java.util.List.of(
                                Map.of("title", "Type",     "value", alert.type().name(),     "short", true),
                                Map.of("title", "Severity", "value", alert.severity().name(), "short", true),
                                Map.of("title", "SKU",      "value", alert.sku(),             "short", true),
                                Map.of("title", "Time",     "value", alert.occurredAt().toString(), "short", true)
                        )
                ))
        );

        webClient.post()
                .uri(webhookUrl)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(r -> log.debug("Webhook delivered for alertId={}", alert.id()))
                .doOnError(e -> log.error("Webhook delivery failed for alertId={}: {}",
                        alert.id(), e.getMessage()))
                .subscribe(); // fire-and-forget — don't block the Kafka consumer thread
    }

    private String formatSlackMessage(Alert alert) {
        return String.format("*%s* — %s\n%s", alert.severity(), alert.title(), alert.message());
    }

    private String severityColor(Alert alert) {
        return switch (alert.severity()) {
            case CRITICAL -> "#FF0000";
            case WARNING  -> "#FFA500";
            case INFO     -> "#36A64F";
        };
    }
}
