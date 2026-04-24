package com.forecast.notification.application;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.infrastructure.notifier.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates alert evaluation + dispatching to all registered channels.
 * Channels are injected as a list — add a new channel by implementing the interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;

    public void dispatch(Alert alert) {
        log.info("Dispatching alert: type={} severity={} sku={} title={}",
                alert.type(), alert.severity(), alert.sku(), alert.title());

        channels.forEach(channel -> {
            try {
                channel.send(alert);
            } catch (Exception ex) {
                // One failing channel must not block the others
                log.error("Channel {} failed to send alert for sku={}: {}",
                        channel.getClass().getSimpleName(), alert.sku(), ex.getMessage());
            }
        });
    }

    public void dispatchAll(List<Alert> alerts) {
        alerts.forEach(this::dispatch);
    }
}
