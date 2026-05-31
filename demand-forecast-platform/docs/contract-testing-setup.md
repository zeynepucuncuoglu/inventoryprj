# Contract Testing — Spring Cloud Contract Kurulum Rehberi

## Problem: Neden Contract Test?

`order-service` şu mesajı Kafka'ya yazıyor:
```json
{"orderId": "123", "productId": "P001", "quantity": 5, "status": "CONFIRMED"}
```

`forecast-service` bu mesajı okuyor ve `productId` alanını bekliyor.

Eğer `order-service` geliştirici bu alanı `product_id` olarak değiştirirse:
- Kendi servisi test → geçer ✅
- `forecast-service` testi → geçer ✅ (mock kullanıyor)
- Production → `forecast-service` çöker ❌

Contract test bu uyumsuzluğu **CI/CD aşamasında** yakalar.

---

## Kurulum

### 1. Producer Tarafı (order-service pom.xml)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-verifier</artifactId>
    <scope>test</scope>
</dependency>

<plugin>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-contract-maven-plugin</artifactId>
    <version>4.1.2</version>
    <extensions>true</extensions>
    <configuration>
        <baseClassForTests>
            com.forecast.order.contract.BaseContractTest
        </baseClassForTests>
    </configuration>
</plugin>
```

### 2. Contract Tanımı (order-service tarafında)

`src/test/resources/contracts/order/shouldPublishOrderConfirmedEvent.groovy`:

```groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "order-service, sipariş onaylandığında order.events'e mesaj yayar"

    label "order_confirmed"

    input {
        triggeredBy("confirmOrder()")
    }

    outputMessage {
        sentTo "order.events"
        body([
            orderId   : $(producer(regex("[0-9a-f-]+")), consumer("order-123")),
            productId : $(producer(regex("[A-Z0-9-]+")), consumer("P001")),
            quantity  : $(producer(positiveInt()), consumer(5)),
            status    : "CONFIRMED",
            timestamp : $(producer(regex("\\d+")), consumer(1000000))
        ])
        headers {
            header("contentType", applicationJson())
        }
    }
}
```

### 3. Base Test Sınıfı (order-service)

```java
// src/test/java/com/forecast/order/contract/BaseContractTest.java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"order.events"})
public abstract class BaseContractTest {

    @Autowired
    private OrderService orderService;

    @SpyBean
    private KafkaTemplate<String, String> kafkaTemplate;

    // Contract'taki triggeredBy("confirmOrder()") bunu çağırır
    public void confirmOrder() {
        orderService.confirmOrder("order-123");
    }
}
```

### 4. Consumer Tarafı (forecast-service pom.xml)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
</dependency>
```

### 5. Consumer Testi (forecast-service)

```java
// src/test/java/com/forecast/forecastsvc/contract/OrderEventContractTest.java
@SpringBootTest
@AutoConfigureStubRunner(
    ids = "com.forecast:order-service:+:stubs",
    // Önce local Maven repo'ya bak, yoksa Maven Central'dan indir
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class OrderEventContractTest {

    @Autowired
    private ForecastJobService forecastJobService;

    @Test
    void shouldConsumeOrderConfirmedEvent() {
        // Stub runner, order-service'in contract'taki mesajı otomatik gönderir
        // forecast-service bu mesajı alıp işleyebiliyor mu?

        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<ForecastJob> jobs = forecastJobService.findAll();
            assertThat(jobs)
                .anyMatch(j -> "P001".equals(j.getProductId()));
        });
    }
}
```

---

## CI/CD Akışı

```
order-service:
  mvn test               → Contract'tan otomatik test üretilir, çalıştırılır
  mvn install            → Stub jar oluşturulur, Maven local repo'ya yüklenir

forecast-service:
  mvn test               → StubRunner, order-service stub'ını kullanır
                           → Gerçek Kafka olmadan contract mesajını alır
                           → Mesajı işleyip işleyemediği test edilir

CI/CD:
  order-service başarısız → forecast-service çalışmaz
  order-service başarılı  → forecast-service contract'a göre doğrulanır
```

---

## Hangi Servislere Eklenecek?

| Producer (mesaj yazan) | Consumer (mesaj okuyan) | Topic |
|---|---|---|
| order-service | forecast-service | order.events |
| order-service | notification-service | order.events |
| product-service | order-service | product.events |
| product-service | notification-service | product.events |
| forecast-service | notification-service | forecast.completed |

Her satır için ayrı bir contract dosyası yazılmalı.
