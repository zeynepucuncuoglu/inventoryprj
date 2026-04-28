package com.forecast.notification.infrastructure.persistence;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AlertRepositoryAdapter.class)
class AlertRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    AlertRepositoryAdapter repository;

    private static Alert lowStockAlert(String sku) {
        return Alert.of(AlertType.LOW_STOCK, AlertSeverity.WARNING,
                "prod-" + sku, sku, "Low stock for " + sku, "Stock below threshold");
    }

    private static Alert demandSurgeAlert(String sku) {
        return Alert.of(AlertType.DEMAND_SURGE, AlertSeverity.CRITICAL,
                "prod-" + sku, sku, "Demand surge for " + sku, "Forecast exceeds stock threshold");
    }

    @Test
    void save_andFindById_roundtrip() {
        Alert saved = repository.save(lowStockAlert("SKU-001"));

        Optional<Alert> found = repository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().sku()).isEqualTo("SKU-001");
        assertThat(found.get().type()).isEqualTo(AlertType.LOW_STOCK);
        assertThat(found.get().severity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void findById_returnsEmpty_whenNotExists() {
        Optional<Alert> found = repository.findById(java.util.UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findBySku_returnsOnlyMatchingAlerts() {
        repository.save(lowStockAlert("SKU-A"));
        repository.save(lowStockAlert("SKU-A"));
        repository.save(demandSurgeAlert("SKU-B"));

        List<Alert> results = repository.findBySku("SKU-A");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Alert::sku).containsOnly("SKU-A");
    }

    @Test
    void findBySku_returnsEmpty_whenNoMatch() {
        assertThat(repository.findBySku("SKU-MISSING")).isEmpty();
    }

    @Test
    void findByType_returnsOnlyMatchingType() {
        repository.save(lowStockAlert("SKU-C"));
        repository.save(demandSurgeAlert("SKU-D"));
        repository.save(demandSurgeAlert("SKU-E"));

        List<Alert> surges = repository.findByType(AlertType.DEMAND_SURGE);

        assertThat(surges).hasSize(2);
        assertThat(surges).extracting(Alert::type).containsOnly(AlertType.DEMAND_SURGE);
    }

    @Test
    void findBySeverity_returnsOnlyMatchingSeverity() {
        repository.save(lowStockAlert("SKU-F"));   // WARNING
        repository.save(demandSurgeAlert("SKU-G")); // CRITICAL

        List<Alert> criticals = repository.findBySeverity(AlertSeverity.CRITICAL);

        assertThat(criticals).hasSize(1);
        assertThat(criticals.get(0).severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void findAll_returnsEveryPersistedAlert() {
        repository.save(lowStockAlert("SKU-H"));
        repository.save(demandSurgeAlert("SKU-I"));
        repository.save(lowStockAlert("SKU-J"));

        List<Alert> all = repository.findAll();

        assertThat(all).hasSize(3);
    }

    @Test
    void save_assignsUniqueIds() {
        Alert a = repository.save(lowStockAlert("SKU-K"));
        Alert b = repository.save(lowStockAlert("SKU-L"));

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void findBySku_orderedByOccurredAtDesc_mostRecentFirst() throws InterruptedException {
        repository.save(lowStockAlert("SKU-M"));
        Thread.sleep(10);
        Alert newer = repository.save(lowStockAlert("SKU-M"));

        List<Alert> results = repository.findBySku("SKU-M");

        assertThat(results.get(0).id()).isEqualTo(newer.id());
    }
}
