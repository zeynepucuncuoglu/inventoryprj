package com.forecast.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        // No retries — log and skip the bad record immediately.
        // Prevents a single malformed message from blocking the entire consumer.
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "Skipping undeserializable record: topic={} partition={} offset={} reason={}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                new FixedBackOff(0L, 0L)
        );
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }
}
