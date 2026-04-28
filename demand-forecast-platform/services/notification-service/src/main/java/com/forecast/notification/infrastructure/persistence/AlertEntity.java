package com.forecast.notification.infrastructure.persistence;

import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_sku", columnList = "sku"),
        @Index(name = "idx_alerts_type", columnList = "type"),
        @Index(name = "idx_alerts_severity", columnList = "severity"),
        @Index(name = "idx_alerts_occurred_at", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEntity {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
