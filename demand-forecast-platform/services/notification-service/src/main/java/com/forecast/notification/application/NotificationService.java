package com.forecast.notification.application;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertRepository;
import com.forecast.notification.infrastructure.notifier.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final AlertRepository alertRepository;

    @Transactional
    public void dispatch(Alert alert) {
        Alert saved = alertRepository.save(alert);

        log.info("Alert persisted and dispatching: type={} severity={} sku={} id={}",
                saved.type(), saved.severity(), saved.sku(), saved.id());

        channels.forEach(channel -> {
            try {
                channel.send(saved);
            } catch (Exception ex) {
                log.error("Channel {} failed for sku={}: {}",
                        channel.getClass().getSimpleName(), saved.sku(), ex.getMessage());
            }
        });
    }

    @Transactional
    public void dispatchAll(List<Alert> alerts) {
        alerts.forEach(this::dispatch);
    }
}
