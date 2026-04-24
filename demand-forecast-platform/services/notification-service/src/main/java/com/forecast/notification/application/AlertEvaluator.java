package com.forecast.notification.application;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Business rules for when to fire an alert.
 * All thresholds come from config — no magic numbers in code.
 */
@Component
public class AlertEvaluator {

    private final int lowStockThreshold;
    private final BigDecimal lowDemandThreshold;
    private final double demandSurgeMultiplier;

    public AlertEvaluator(
            @Value("${notification.low-stock-threshold}") int lowStockThreshold,
            @Value("${notification.low-demand-forecast-threshold}") int lowDemandThreshold,
            @Value("${notification.high-demand-surge-multiplier}") double demandSurgeMultiplier) {
        this.lowStockThreshold = lowStockThreshold;
        this.lowDemandThreshold = BigDecimal.valueOf(lowDemandThreshold);
        this.demandSurgeMultiplier = demandSurgeMultiplier;
    }

    /**
     * Evaluates stock event — may produce a LOW_STOCK alert.
     */
    public Optional<Alert> evaluateStockEvent(String productId, String sku, int stockQuantity) {
        if (stockQuantity <= lowStockThreshold) {
            AlertSeverity severity = stockQuantity == 0
                    ? AlertSeverity.CRITICAL
                    : AlertSeverity.WARNING;

            return Optional.of(Alert.of(
                    AlertType.LOW_STOCK,
                    severity,
                    productId,
                    sku,
                    "Low Stock Alert: " + sku,
                    String.format("Product %s has only %d units remaining (threshold: %d).",
                            sku, stockQuantity, lowStockThreshold)
            ));
        }
        return Optional.empty();
    }

    /**
     * Evaluates a completed forecast — may produce DEMAND_SURGE or LOW_DEMAND_FORECAST alert.
     */
    public List<Alert> evaluateForecastResult(String productId, String sku,
                                               BigDecimal totalPredictedDemand,
                                               int currentStock) {
        List<Alert> alerts = new ArrayList<>();

        // Rule 1: demand surge — forecast predicts much more than current stock
        BigDecimal surgeThreshold = BigDecimal.valueOf(currentStock * demandSurgeMultiplier);
        if (totalPredictedDemand.compareTo(surgeThreshold) > 0) {
            alerts.add(Alert.of(
                    AlertType.DEMAND_SURGE,
                    AlertSeverity.CRITICAL,
                    productId,
                    sku,
                    "Demand Surge Alert: " + sku,
                    String.format(
                            "Forecast predicts %.0f units demand over next 30 days, " +
                            "but current stock is only %d units (surge threshold: %.0f). " +
                            "Restock urgently.",
                            totalPredictedDemand, currentStock, surgeThreshold)
            ));
        }

        // Rule 2: very low demand — potential overstock / dead inventory
        if (totalPredictedDemand.compareTo(lowDemandThreshold) < 0) {
            alerts.add(Alert.of(
                    AlertType.LOW_DEMAND_FORECAST,
                    AlertSeverity.INFO,
                    productId,
                    sku,
                    "Low Demand Forecast: " + sku,
                    String.format(
                            "Forecast predicts only %.0f units demand over next 30 days. " +
                            "Consider promotions or stock reduction.",
                            totalPredictedDemand)
            ));
        }

        return alerts;
    }

    /**
     * Evaluates a failed forecast — always fires an alert so ops team knows.
     */
    public Alert evaluateForecastFailure(String productId, String sku, String errorMessage) {
        return Alert.of(
                AlertType.FORECAST_FAILED,
                AlertSeverity.WARNING,
                productId,
                sku,
                "Forecast Failed: " + sku,
                String.format("Demand forecast for %s failed: %s", sku, errorMessage)
        );
    }
}
