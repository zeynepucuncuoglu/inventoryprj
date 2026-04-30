package com.forecast.notification.config;

import com.forecast.notification.infrastructure.messaging.ForecastEventMessage;
import com.forecast.notification.infrastructure.messaging.ProductEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseProps() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
    }

    // Log and skip any record that fails deserialization or listener invocation.
    private DefaultErrorHandler skipBadRecords() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "Skipping bad record: topic={} partition={} offset={} reason={}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                new FixedBackOff(0L, 0L)
        );
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ProductEventMessage> productEventsFactory() {
        JsonDeserializer<ProductEventMessage> deser = new JsonDeserializer<>(ProductEventMessage.class);
        deser.setUseTypeHeaders(false);

        var consumerFactory = new DefaultKafkaConsumerFactory<>(
                baseProps(), new StringDeserializer(), new ErrorHandlingDeserializer<>(deser));

        var factory = new ConcurrentKafkaListenerContainerFactory<String, ProductEventMessage>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(skipBadRecords());
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ForecastEventMessage> forecastEventsFactory() {
        JsonDeserializer<ForecastEventMessage> deser = new JsonDeserializer<>(ForecastEventMessage.class);
        deser.setUseTypeHeaders(false);

        var consumerFactory = new DefaultKafkaConsumerFactory<>(
                baseProps(), new StringDeserializer(), new ErrorHandlingDeserializer<>(deser));

        var factory = new ConcurrentKafkaListenerContainerFactory<String, ForecastEventMessage>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(skipBadRecords());
        return factory;
    }
}
