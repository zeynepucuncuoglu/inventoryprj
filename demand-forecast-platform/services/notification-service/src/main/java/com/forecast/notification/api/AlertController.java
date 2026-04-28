package com.forecast.notification.api;

import com.forecast.notification.domain.Alert;
import com.forecast.notification.domain.AlertRepository;
import com.forecast.notification.domain.AlertSeverity;
import com.forecast.notification.domain.AlertType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert history — all alerts triggered by stock and forecast events")
public class AlertController {

    private final AlertRepository alertRepository;

    @Operation(summary = "List all alerts",
            description = "Returns all persisted alerts. Filter by sku, type, or severity.")
    @ApiResponse(responseCode = "200", description = "Alert list")
    @GetMapping
    public ResponseEntity<List<Alert>> getAlerts(
            @Parameter(description = "Filter by product SKU") @RequestParam(required = false) String sku,
            @Parameter(description = "Filter by alert type (LOW_STOCK, DEMAND_SURGE, LOW_DEMAND_FORECAST, FORECAST_FAILED)")
            @RequestParam(required = false) AlertType type,
            @Parameter(description = "Filter by severity (INFO, WARNING, CRITICAL)")
            @RequestParam(required = false) AlertSeverity severity) {

        List<Alert> alerts;
        if (sku != null) {
            alerts = alertRepository.findBySku(sku);
        } else if (type != null) {
            alerts = alertRepository.findByType(type);
        } else if (severity != null) {
            alerts = alertRepository.findBySeverity(severity);
        } else {
            alerts = alertRepository.findAll();
        }
        return ResponseEntity.ok(alerts);
    }

    @Operation(summary = "Get a single alert by ID")
    @ApiResponse(responseCode = "200", description = "Alert found")
    @ApiResponse(responseCode = "404", description = "Alert not found")
    @GetMapping("/{id}")
    public ResponseEntity<Alert> getAlert(
            @Parameter(description = "Alert UUID") @PathVariable UUID id) {
        return alertRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
