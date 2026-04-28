package com.forecast.notification.infrastructure.persistence;

import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {
    List<AlertEntity> findBySkuOrderByOccurredAtDesc(String sku);
    List<AlertEntity> findByTypeOrderByOccurredAtDesc(AlertType type);
    List<AlertEntity> findBySeverityOrderByOccurredAtDesc(AlertSeverity severity);
}
