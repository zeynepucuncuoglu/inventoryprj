package com.forecast.notification.application;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class AlertEvaluatorTest {

    private AlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // low-stock-threshold=10, low-demand-threshold=5, surge-multiplier=2.0
        evaluator = new AlertEvaluator(10, 5, 2.0);
    }

    // ─── Stock alerts ───────────────────────────────────────────────────────

    @Test
    void evaluateStockEvent_returnsEmpty_whenStockAboveThreshold() {
        Optional<Alert> alert = evaluator.evaluateStockEvent("pid-1", "SKU-001", 11);

        assertThat(alert).isEmpty();
    }

    @Test
    void evaluateStockEvent_returnsWarning_whenStockAtThreshold() {
        Optional<Alert> alert = evaluator.evaluateStockEvent("pid-1", "SKU-001", 10);

        assertThat(alert).isPresent();
        assertThat(alert.get().type()).isEqualTo(AlertType.LOW_STOCK);
        assertThat(alert.get().severity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void evaluateStockEvent_returnsWarning_whenStockBelowThreshold() {
        Optional<Alert> alert = evaluator.evaluateStockEvent("pid-1", "SKU-001", 3);

        assertThat(alert).isPresent();
        assertThat(alert.get().severity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void evaluateStockEvent_returnsCritical_whenStockIsZero() {
        Optional<Alert> alert = evaluator.evaluateStockEvent("pid-1", "SKU-001", 0);

        assertThat(alert).isPresent();
        assertThat(alert.get().type()).isEqualTo(AlertType.LOW_STOCK);
        assertThat(alert.get().severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void evaluateStockEvent_alertContainsCorrectSkuAndProductId() {
        Optional<Alert> alert = evaluator.evaluateStockEvent("prod-123", "SKU-ABC", 0);

        assertThat(alert.get().productId()).isEqualTo("prod-123");
        assertThat(alert.get().sku()).isEqualTo("SKU-ABC");
    }

    // ─── Forecast alerts ─────────────────────────────────────────────────────

    @Test
    void evaluateForecastResult_returnsEmpty_whenDemandIsNormal() {
        // stock=100, surge threshold = 100 * 2.0 = 200, demand=150 is under surge
        // demand=150 is also above low-demand threshold of 5
        List<Alert> alerts = evaluator.evaluateForecastResult(
                "pid-1", "SKU-001", new BigDecimal("150"), 100);

        assertThat(alerts).isEmpty();
    }

    @Test
    void evaluateForecastResult_returnsDemandSurge_whenForecastExceedsSurgeThreshold() {
        // stock=50, threshold = 50*2.0 = 100; demand=101 triggers surge
        List<Alert> alerts = evaluator.evaluateForecastResult(
                "pid-1", "SKU-001", new BigDecimal("101"), 50);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).type()).isEqualTo(AlertType.DEMAND_SURGE);
        assertThat(alerts.get(0).severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void evaluateForecastResult_returnsLowDemand_whenForecastBelowLowThreshold() {
        List<Alert> alerts = evaluator.evaluateForecastResult(
                "pid-1", "SKU-001", new BigDecimal("3"), 100);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).type()).isEqualTo(AlertType.LOW_DEMAND_FORECAST);
        assertThat(alerts.get(0).severity()).isEqualTo(AlertSeverity.INFO);
    }

    @Test
    void evaluateForecastResult_returnsBothAlerts_whenSurgeAndLowDemandApply() {
        // stock=1, surge threshold=2; demand=3 triggers surge (3>2)
        // demand=3 is below low threshold of 5 — triggers low demand too
        List<Alert> alerts = evaluator.evaluateForecastResult(
                "pid-1", "SKU-001", new BigDecimal("3"), 1);

        assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting(Alert::type)
                .containsExactlyInAnyOrder(AlertType.DEMAND_SURGE, AlertType.LOW_DEMAND_FORECAST);
    }

    // ─── Forecast failure alerts ──────────────────────────────────────────────

    @Test
    void evaluateForecastFailure_alwaysReturnsForecastFailedAlert() {
        Alert alert = evaluator.evaluateForecastFailure("pid-1", "SKU-001", "ML service down");

        assertThat(alert.type()).isEqualTo(AlertType.FORECAST_FAILED);
        assertThat(alert.severity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(alert.message()).contains("ML service down");
    }
}
