package com.forecast.notification.infrastructure.notifier;

import com.forecast.notification.domain.Alert;

/**
 * Port — defines how an alert gets delivered.
 * Each implementation is a different channel: log, webhook, email, PagerDuty, etc.
 * Add a new channel by implementing this interface and annotating with @Component.
 */
public interface NotificationChannel {
    void send(Alert alert);
}
