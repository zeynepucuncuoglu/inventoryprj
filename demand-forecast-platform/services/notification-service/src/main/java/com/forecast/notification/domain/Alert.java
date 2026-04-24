package com.forecast.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a notification that needs to be sent.
 * Immutable — created once, never mutated.
 */
public record Alert(
        UUID id,
        AlertType type,
        AlertSeverity severity,
        String productId,
        String sku,
        String title,
        String message,
        Instant occurredAt
) {
    public static Alert of(AlertType type, AlertSeverity severity,
                           String productId, String sku,
                           String title, String message) {
        return new Alert(
                UUID.randomUUID(), type, severity,
                productId, sku, title, message, Instant.now()
        );
    }
}
