package com.forecast.forecastsvc.api;

import com.forecast.forecastsvc.application.ForecastJobService;
import com.forecast.forecastsvc.application.dto.ForecastJobResponse;
import com.forecast.forecastsvc.application.dto.SalesDataPoint;
import com.forecast.forecastsvc.application.dto.TriggerForecastRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/forecasts")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastJobService forecastJobService;

    /**
     * Triggers a forecast. Returns 202 Accepted immediately.
     * Client polls GET /{id} to check when status = COMPLETED.
     */
    @PostMapping
    public ResponseEntity<ForecastJobResponse> triggerForecast(
            @Valid @RequestBody TriggerForecastWithHistoryRequest request) {
        ForecastJobResponse response = forecastJobService.triggerForecast(
                new TriggerForecastRequest(
                        request.productId(), request.sku(), request.horizonDays()),
                request.historicalData()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ForecastJobResponse> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(forecastJobService.getJob(id));
    }

    @GetMapping("/products/{productId}/latest")
    public ResponseEntity<ForecastJobResponse> getLatestForecast(@PathVariable UUID productId) {
        return ResponseEntity.ok(forecastJobService.getLatestForecast(productId));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<List<ForecastJobResponse>> getJobsByProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(forecastJobService.getJobsByProduct(productId));
    }

    // Request body wraps TriggerForecastRequest + historical data
    public record TriggerForecastWithHistoryRequest(
            @jakarta.validation.constraints.NotNull UUID productId,
            @jakarta.validation.constraints.NotBlank String sku,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(365) int horizonDays,
            @jakarta.validation.constraints.NotEmpty List<SalesDataPoint> historicalData
    ) {}
}
