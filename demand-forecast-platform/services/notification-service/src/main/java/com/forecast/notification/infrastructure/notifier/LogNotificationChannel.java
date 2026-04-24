package com.forecast.notification.infrastructure.notifier;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Always-on channel — logs every alert.
 * Useful for local dev and as a fallback if other channels fail.
 * Runs first (Order 1) so alerts are always visible even if webhook fails.
 */
@Slf4j
@Component
@Order(1)
public class LogNotificationChannel implements NotificationChannel {

    @Override
    public void send(Alert alert) {
        String logMessage = "[ALERT] id={} type={} severity={} sku={} | {}";

        if (alert.severity() == AlertSeverity.CRITICAL) {
            log.error(logMessage, alert.id(), alert.type(), alert.severity(),
                    alert.sku(), alert.message());
        } else if (alert.severity() == AlertSeverity.WARNING) {
            log.warn(logMessage, alert.id(), alert.type(), alert.severity(),
                    alert.sku(), alert.message());
        } else {
            log.info(logMessage, alert.id(), alert.type(), alert.severity(),
                    alert.sku(), alert.message());
        }
    }
}
