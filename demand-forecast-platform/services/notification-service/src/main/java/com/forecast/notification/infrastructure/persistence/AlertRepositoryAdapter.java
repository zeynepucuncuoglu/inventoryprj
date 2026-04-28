package com.forecast.notification.infrastructure.persistence;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertRepository;
import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository jpa;

    @Override
    public Alert save(Alert alert) {
        AlertEntity saved = jpa.save(toEntity(alert));
        return toDomain(saved);
    }

    @Override
    public Optional<Alert> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Alert> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Alert> findBySku(String sku) {
        return jpa.findBySkuOrderByOccurredAtDesc(sku).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Alert> findByType(AlertType type) {
        return jpa.findByTypeOrderByOccurredAtDesc(type).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Alert> findBySeverity(AlertSeverity severity) {
        return jpa.findBySeverityOrderByOccurredAtDesc(severity).stream().map(this::toDomain).toList();
    }

    private AlertEntity toEntity(Alert alert) {
        return AlertEntity.builder()
                .id(alert.id())
                .type(alert.type())
                .severity(alert.severity())
                .productId(alert.productId())
                .sku(alert.sku())
                .title(alert.title())
                .message(alert.message())
                .occurredAt(alert.occurredAt())
                .build();
    }

    private Alert toDomain(AlertEntity entity) {
        return new Alert(
                entity.getId(),
                entity.getType(),
                entity.getSeverity(),
                entity.getProductId(),
                entity.getSku(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getOccurredAt()
        );
    }
}
