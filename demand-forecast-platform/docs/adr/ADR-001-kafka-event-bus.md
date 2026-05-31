# ADR-001: Servisler Arası İletişim için Apache Kafka

**Tarih:** 2025-10-01  
**Durum:** Kabul Edildi  
**Karar Veren:** Platform Engineering

---

## Bağlam

6 mikroservisin birbirleriyle haberleşmesi gerekiyor. İki ana seçenek değerlendirildi:
1. Doğrudan HTTP (REST/gRPC)
2. Mesaj kuyruğu (Kafka, RabbitMQ, SQS)

---

## Karar

**Apache Kafka** kullanılacak.

---

## Gerekçe

**HTTP'nin Sorunları:**
- `order-service` → `notification-service` HTTP çağrısı yaparken `notification-service` çökmüş olursa sipariş de başarısız sayılır — gereksiz bağımlılık.
- Yüksek trafikte `order-service` `forecast-service`'i tetiklerken zincirin en yavaş halkası tüm akışı yavaşlatır.
- Servis sayısı arttıkça bağımlılık grafiği karmaşıklaşır.

**Kafka'nın Avantajları:**
- **Bağımsızlık:** `order-service` mesajı bırakır, işi biter. `notification-service` kendi hızında okur.
- **Dayanıklılık:** `notification-service` çöküp kalktığında mesajlar kaybolmaz — lag'ı eriyince devam eder.
- **Replay:** Geçmiş mesajları yeniden işleyebilme (offset sıfırlama).
- **Ölçeklenebilirlik:** Partition sayısını artırarak consumer sayısını ölçeklendirme.

**RabbitMQ yerine Kafka neden?**
- Kafka log-based — mesajlar silinmez, offset'e göre okunur. Bu özellikle `forecast-service`'in geçmiş sipariş verilerini yeniden işleyebilmesi için gerekli.
- RabbitMQ push-based; tüketilince silinir, replay imkanı yoktur.

---

## Sonuçlar

- Her servis için ayrı Kafka consumer group tanımlandı.
- 5 topic, partition sayıları trafiğe göre belirlendi (`order.events` = 6 partition).
- Her topic için `.DLT` Dead Letter Queue oluşturuldu (bkz. ADR-003).
- Schema Registry ile mesaj şemaları versiyonlandı.

---

## Değerlendirilen Alternatifleri Reddetme Nedenleri

| Alternatif | Neden Reddedildi |
|---|---|
| REST HTTP | Servisler arası sıkı bağımlılık, zincir kırılganlığı |
| RabbitMQ | Replay yok, log-based depolama yok |
| AWS SQS | Cloud vendor lock-in, yerel geliştirme zorluğu |
| gRPC | Senkron, bağımlılık sorunu devam eder |
