package com.forecast.forecastsvc.application;

import com.forecast.forecastsvc.application.dto.*;
import com.forecast.forecastsvc.application.port.MlInferenceClient;
import com.forecast.forecastsvc.domain.*;
import com.forecast.forecastsvc.infrastructure.messaging.ForecastEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastJobService {

    private final ForecastJobRepository jobRepository;
    private final MlInferenceClient mlClient;
    private final ForecastEventPublisher eventPublisher;

    /**
     * Step 1: Create the job record synchronously — return immediately.
     * Step 2: Run ML inference asynchronously — don't block the HTTP thread.
     *
     * Why @Async?
     * ML inference can take 10-60 seconds. If we blocked the HTTP call,
     * the client would time out. Instead: create job → return 202 Accepted →
     * client polls GET /forecasts/{id} until status = COMPLETED.
     */
    @Transactional
    public ForecastJobResponse triggerForecast(TriggerForecastRequest request,
                                                List<SalesDataPoint> historicalData) {
        ForecastJob job = ForecastJob.create(request.productId(), request.sku(), request.horizonDays());
        ForecastJob saved = jobRepository.save(job);

        log.info("Forecast job created: id={} productId={} sku={}",
                saved.getId(), saved.getProductId(), saved.getSku());

        // Fire-and-forget — runs in a separate thread pool
        runInference(saved.getId(), historicalData, request.horizonDays());

        return ForecastJobResponse.from(saved);
    }

    /**
     * The actual ML call — runs asynchronously.
     * Uses a new transaction because the outer transaction is already committed.
     */
    @Async("forecastExecutor")
    @Transactional
    public void runInference(UUID jobId, List<SalesDataPoint> historicalData, int horizonDays) {
        ForecastJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        job.markRunning();
        jobRepository.save(job);

        try {
            MlForecastRequest mlRequest = new MlForecastRequest(
                    job.getProductId().toString(),
                    job.getSku(),
                    horizonDays,
                    historicalData,
                    "prophet"
            );

            // This call has a circuit breaker around it (configured in MlInferenceClientAdapter)
            MlForecastResponse mlResponse = mlClient.requestForecast(mlRequest);

            ForecastResult result = mapToResult(mlResponse);
            job.complete(result);
            jobRepository.save(job);

            log.info("Forecast job completed: id={} mae={} totalDemand={}",
                    jobId, result.mae(), result.totalPredictedDemand());

            eventPublisher.publishForecastCompleted(job);

        } catch (Exception ex) {
            log.error("Forecast job failed: id={} error={}", jobId, ex.getMessage());
            job.fail(ex.getMessage());
            jobRepository.save(job);
            eventPublisher.publishForecastFailed(job);
        }
    }

    @Transactional(readOnly = true)
    public ForecastJobResponse getJob(UUID id) {
        return jobRepository.findById(id)
                .map(ForecastJobResponse::from)
                .orElseThrow(() -> new ForecastJobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public ForecastJobResponse getLatestForecast(UUID productId) {
        return jobRepository.findLatestCompletedByProductId(productId)
                .map(ForecastJobResponse::from)
                .orElseThrow(() -> new ForecastJobNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public List<ForecastJobResponse> getJobsByProduct(UUID productId) {
        return jobRepository.findByProductId(productId).stream()
                .map(ForecastJobResponse::from)
                .toList();
    }

    private ForecastResult mapToResult(MlForecastResponse mlResponse) {
        List<ForecastPoint> points = mlResponse.forecast().stream()
                .map(p -> new ForecastPoint(
                        p.date(),
                        p.predicted_quantity().max(BigDecimal.ZERO),
                        p.lower_bound().max(BigDecimal.ZERO),
                        p.upper_bound().max(BigDecimal.ZERO)
                ))
                .toList();

        return new ForecastResult(mlResponse.model_used(), points, mlResponse.mae());
    }
}
