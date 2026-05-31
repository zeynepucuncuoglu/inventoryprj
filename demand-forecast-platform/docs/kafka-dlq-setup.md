# Kafka Dead Letter Queue (DLQ) Kurulum Rehberi

## Her Java Servisine Eklenmesi Gereken Config

### 1. pom.xml'e dependency ekle

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Spring Kafka zaten mevcut, ek dependency gerekmez.

---

### 2. KafkaConsumerConfig.java (her serviste)

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer(
            KafkaTemplate<String, String> template) {
        // Hatalı mesaj → topic.DLT'ye yönlendir
        return new DeadLetterPublishingRecoverer(template,
            (record, ex) -> {
                // DLT topic adı: orijinal topic + ".DLT"
                return new TopicPartition(record.topic() + ".DLT", 0);
            });
    }

    @Bean
    public DefaultErrorHandler errorHandler(
            DeadLetterPublishingRecoverer recoverer) {
        // 3 kez dene: 1s, 2s, 4s bekleyerek (exponential backoff)
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(10000L); // max 10 saniye

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Bu hataları retry etme — direkt DLT'ye gönder
        handler.addNotRetryableExceptions(
            JsonParseException.class,         // mesaj formatı bozuk
            IllegalArgumentException.class    // validation hatası
        );

        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
```

---

### 3. Consumer — değişiklik gerekmez

```java
@KafkaListener(topics = "order.events", groupId = "order-service-group")
public void onOrderEvent(String message) {
    // Hata fırlarsa → DefaultErrorHandler devreye girer
    // 3 retry sonra → order.events.DLT'ye gider
    orderService.process(message);
}
```

---

### 4. DLT'deki Mesajları İzleme

```bash
# DLT'de bekleyen mesajları gör
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.events.DLT \
  --from-beginning \
  --property print.headers=true

# DLT mesaj sayısı
docker exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 \
  --topic order.events.DLT
```

---

### 5. DLT'deki Mesajları Tekrar İşleme

```bash
# DLT'deki mesajları ana topic'e kopyala (tekrar işle)
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.events.DLT \
  --from-beginning | \
docker exec -i kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic order.events
```

---

## DLQ Nasıl Çalışır — Adım Adım

```
Kafka: order.events'ten mesaj geldi
            ↓
order-service: işlemeye çalıştı → HATA (DB bağlantısı yok)
            ↓
DefaultErrorHandler: 1. retry (1 saniye bekle)
            ↓
order-service: tekrar denedi → HATA
            ↓
DefaultErrorHandler: 2. retry (2 saniye bekle)
            ↓
order-service: tekrar denedi → HATA
            ↓
DefaultErrorHandler: 3. retry (4 saniye bekle)
            ↓
3 retry de başarısız → DeadLetterPublishingRecoverer
            ↓
Mesaj order.events.DLT'ye yazıldı ← KAYBOLMADI
            ↓
Kafka: sonraki mesaja geçti (sistem bloke olmadı)
```
