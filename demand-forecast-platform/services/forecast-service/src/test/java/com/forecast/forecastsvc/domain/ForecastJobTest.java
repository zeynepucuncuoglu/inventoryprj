package com.forecast.forecastsvc.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ForecastJobTest {

    private static ForecastJob pendingJob() {
        return ForecastJob.create(UUID.randomUUID(), "SKU-001", 30);
    }

    private static ForecastResult dummyResult() {
        ForecastPoint point = new ForecastPoint(
                LocalDate.now(), BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("20")
        );
        return new ForecastResult("prophet", List.of(point), new BigDecimal("1.5"));
    }

    @Test
    void create_setsInitialState() {
        UUID productId = UUID.randomUUID();
        ForecastJob job = ForecastJob.create(productId, "SKU-001", 30);

        assertThat(job.getId()).isNotNull();
        assertThat(job.getProductId()).isEqualTo(productId);
        assertThat(job.getSku()).isEqualTo("SKU-001");
        assertThat(job.getHorizonDays()).isEqualTo(30);
        assertThat(job.getStatus()).isEqualTo(ForecastStatus.PENDING);
        assertThat(job.getResult()).isNull();
        assertThat(job.getErrorMessage()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 366, 1000})
    void create_throwsOnInvalidHorizonDays(int horizon) {
        assertThatThrownBy(() -> ForecastJob.create(UUID.randomUUID(), "SKU-001", horizon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horizonDays");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 365})
    void create_acceptsValidHorizonDays(int horizon) {
        assertThatNoException().isThrownBy(() ->
                ForecastJob.create(UUID.randomUUID(), "SKU-001", horizon));
    }

    @Test
    void markRunning_transitionsPendingToRunning() {
        ForecastJob job = pendingJob();

        job.markRunning();

        assertThat(job.getStatus()).isEqualTo(ForecastStatus.RUNNING);
    }

    @Test
    void markRunning_throwsWhenNotPending() {
        ForecastJob job = pendingJob();
        job.markRunning();

        assertThatThrownBy(job::markRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void complete_transitionsRunningToCompleted() {
        ForecastJob job = pendingJob();
        job.markRunning();

        job.complete(dummyResult());

        assertThat(job.getStatus()).isEqualTo(ForecastStatus.COMPLETED);
        assertThat(job.getResult()).isNotNull();
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    void complete_throwsWhenNotRunning() {
        ForecastJob job = pendingJob();

        assertThatThrownBy(() -> job.complete(dummyResult()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    void fail_transitionsRunningToFailed() {
        ForecastJob job = pendingJob();
        job.markRunning();

        job.fail("Connection timeout");

        assertThat(job.getStatus()).isEqualTo(ForecastStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(job.getResult()).isNull();
    }

    @Test
    void fail_throwsWhenNotRunning() {
        ForecastJob job = pendingJob();

        assertThatThrownBy(() -> job.fail("error"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    void isTerminal_falseForPendingAndRunning() {
        ForecastJob pending = pendingJob();
        ForecastJob running = pendingJob();
        running.markRunning();

        assertThat(pending.isTerminal()).isFalse();
        assertThat(running.isTerminal()).isFalse();
    }

    @Test
    void isTerminal_trueForCompletedAndFailed() {
        ForecastJob completed = pendingJob();
        completed.markRunning();
        completed.complete(dummyResult());

        ForecastJob failed = pendingJob();
        failed.markRunning();
        failed.fail("error");

        assertThat(completed.isTerminal()).isTrue();
        assertThat(failed.isTerminal()).isTrue();
    }
}
