package com.forecast.forecastsvc.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * ForecastJob is the Aggregate Root.
 *
 * It tracks the lifecycle of a single forecast request:
 *   - Who triggered it (productId, sku)
 *   - Current status (PENDING → RUNNING → COMPLETED/FAILED)
 *   - The result once completed
 *   - Error message if failed
 *
 * Business rules:
 *   - A COMPLETED or FAILED job cannot be re-run (create a new one)
 *   - Result can only be set when status is RUNNING
 *   - horizonDays must be between 1 and 365
 */
public class ForecastJob {

    private final UUID id;
    private final UUID productId;
    private final String sku;
    private final int horizonDays;
    private ForecastStatus status;
    private ForecastResult result;       // null until COMPLETED
    private String errorMessage;         // null unless FAILED
    private final Instant createdAt;
    private Instant updatedAt;

    public static ForecastJob create(UUID productId, String sku, int horizonDays) {
        if (horizonDays < 1 || horizonDays > 365) {
            throw new IllegalArgumentException("horizonDays must be between 1 and 365");
        }
        return new ForecastJob(
                UUID.randomUUID(), productId, sku, horizonDays,
                ForecastStatus.PENDING, null, null,
                Instant.now(), Instant.now()
        );
    }

    public static ForecastJob reconstitute(UUID id, UUID productId, String sku,
                                            int horizonDays, ForecastStatus status,
                                            ForecastResult result, String errorMessage,
                                            Instant createdAt, Instant updatedAt) {
        return new ForecastJob(id, productId, sku, horizonDays,
                status, result, errorMessage, createdAt, updatedAt);
    }

    private ForecastJob(UUID id, UUID productId, String sku, int horizonDays,
                        ForecastStatus status, ForecastResult result, String errorMessage,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.horizonDays = horizonDays;
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markRunning() {
        if (this.status != ForecastStatus.PENDING) {
            throw new IllegalStateException("Only PENDING jobs can be started. Current: " + status);
        }
        this.status = ForecastStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void complete(ForecastResult result) {
        if (this.status != ForecastStatus.RUNNING) {
            throw new IllegalStateException("Only RUNNING jobs can be completed. Current: " + status);
        }
        this.status = ForecastStatus.COMPLETED;
        this.result = result;
        this.updatedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        if (this.status != ForecastStatus.RUNNING) {
            throw new IllegalStateException("Only RUNNING jobs can fail. Current: " + status);
        }
        this.status = ForecastStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return status == ForecastStatus.COMPLETED || status == ForecastStatus.FAILED;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public int getHorizonDays() { return horizonDays; }
    public ForecastStatus getStatus() { return status; }
    public ForecastResult getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
