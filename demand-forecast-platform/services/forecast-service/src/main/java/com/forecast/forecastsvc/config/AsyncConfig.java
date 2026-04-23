package com.forecast.forecastsvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for ML inference jobs.
 *
 * Why a dedicated pool?
 * ML calls can take 10-60 seconds. Using the default Spring async pool
 * would starve other async tasks. A separate pool with a bounded queue
 * prevents unbounded memory growth if ML service slows down.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "forecastExecutor")
    public Executor forecastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);       // always-alive threads for ML jobs
        executor.setMaxPoolSize(10);       // max under heavy load
        executor.setQueueCapacity(50);     // queue up to 50 pending jobs
        executor.setThreadNamePrefix("forecast-ml-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60); // graceful shutdown
        executor.initialize();
        return executor;
    }
}
