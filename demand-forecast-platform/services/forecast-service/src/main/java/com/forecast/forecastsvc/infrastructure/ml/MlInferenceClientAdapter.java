package com.forecast.forecastsvc.infrastructure.ml;

import com.forecast.forecastsvc.application.dto.MlForecastRequest;
import com.forecast.forecastsvc.application.dto.MlForecastResponse;
import com.forecast.forecastsvc.application.port.MlInferenceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Adapter: implements MlInferenceClient port using WebClient + Resilience4j.
 *
 * @CircuitBreaker: If ML service fails 50% of the time over 10 calls,
 *   the circuit OPENS — subsequent calls fail fast without hitting ML service.
 *   After 30s, it goes HALF-OPEN and tries a few calls. If they succeed, it CLOSES.
 *
 * @Retry: Before the circuit breaker counts a failure, we retry 3 times
 *   with 2s delay. Network blips don't open the circuit immediately.
 *
 * Why WebClient (not RestTemplate)?
 *   WebClient is non-blocking. The @Async thread that calls this
 *   doesn't tie up a JVM thread while waiting for ML response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MlInferenceClientAdapter implements MlInferenceClient {

    private final WebClient mlWebClient;

    @Override
    @CircuitBreaker(name = "ml-inference", fallbackMethod = "fallback")
    @Retry(name = "ml-inference")
    public MlForecastResponse requestForecast(MlForecastRequest request) {
        log.debug("Calling ML service for productId={} horizon={}",
                request.product_id(), request.horizon_days());

        return mlWebClient.post()
                .uri("/api/v1/forecast")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MlForecastResponse.class)
                .timeout(Duration.ofSeconds(65))
                .doOnSuccess(r -> log.debug("ML response received: mae={}", r.mae()))
                .block(); // blocking is OK here — we're already in @Async thread
    }

    /**
     * Fallback — called when circuit is OPEN or all retries are exhausted.
     * We throw a domain-friendly exception so ForecastJobService can mark the job FAILED.
     */
    @SuppressWarnings("unused")
    private MlForecastResponse fallback(MlForecastRequest request, Exception ex) {
        log.warn("ML service unavailable for productId={}: {}", request.product_id(), ex.getMessage());
        throw new MlServiceUnavailableException("ML service is currently unavailable: " + ex.getMessage());
    }
}
