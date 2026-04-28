package com.forecast.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {
    Alert save(Alert alert);
    Optional<Alert> findById(UUID id);
    List<Alert> findAll();
    List<Alert> findBySku(String sku);
    List<Alert> findByType(AlertType type);
    List<Alert> findBySeverity(AlertSeverity severity);
}
