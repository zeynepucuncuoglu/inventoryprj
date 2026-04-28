package com.forecast.forecastsvc.api;

import com.forecast.forecastsvc.application.ForecastJobService;
import com.forecast.forecastsvc.application.dto.ForecastJobResponse;
import com.forecast.forecastsvc.application.dto.SalesDataPoint;
import com.forecast.forecastsvc.application.dto.TriggerForecastRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Forecasts", description = "Demand forecast jobs. Forecasts run asynchronously — " +
        "POST returns 202 Accepted, then poll GET /{id} until status = COMPLETED.")
public class ForecastController {

    private final ForecastJobService forecastJobService;

    @Operation(summary = "Trigger a forecast job",
            description = "Starts an async demand forecast. Returns 202 immediately with the job ID. " +
                    "Poll GET /api/v1/forecasts/{id} to check completion. " +
                    "Requires at least 14 days of historical sales data.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Forecast job accepted and running"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping
    public ResponseEntity<ForecastJobResponse> triggerForecast(
            @Valid @RequestBody TriggerForecastWithHistoryRequest request) {
        ForecastJobResponse response = forecastJobService.triggerForecast(
                new TriggerForecastRequest(request.productId(), request.sku(), request.horizonDays()),
                request.historicalData()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Get forecast job status",
            description = "Returns the current status and result of a forecast job. " +
                    "Status values: PENDING, RUNNING, COMPLETED, FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ForecastJobResponse> getJob(
            @Parameter(description = "Forecast job UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(forecastJobService.getJob(id));
    }

    @Operation(summary = "Get latest completed forecast for a product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Forecast found"),
            @ApiResponse(responseCode = "404", description = "No completed forecast for this product")
    })
    @GetMapping("/products/{productId}/latest")
    public ResponseEntity<ForecastJobResponse> getLatestForecast(
            @Parameter(description = "Product UUID") @PathVariable UUID productId) {
        return ResponseEntity.ok(forecastJobService.getLatestForecast(productId));
    }

    @Operation(summary = "Get all forecast jobs for a product")
    @ApiResponse(responseCode = "200", description = "Job list (may be empty)")
    @GetMapping("/products/{productId}")
    public ResponseEntity<List<ForecastJobResponse>> getJobsByProduct(
            @Parameter(description = "Product UUID") @PathVariable UUID productId) {
        return ResponseEntity.ok(forecastJobService.getJobsByProduct(productId));
    }

    public record TriggerForecastWithHistoryRequest(
            @jakarta.validation.constraints.NotNull UUID productId,
            @jakarta.validation.constraints.NotBlank String sku,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(365) int horizonDays,
            @jakarta.validation.constraints.NotEmpty List<SalesDataPoint> historicalData
    ) {}
}
