# Capacity Planning — Demand Forecast Platform

**Son güncelleme:** 2026-05-08  
**Gözden geçirme:** Her 3 ayda bir veya P1 olayından sonra

---

## Mevcut Yük (Baseline)

| Metrik | Değer |
|---|---|
| Günlük sipariş | 10,000 |
| Tepe saatlik sipariş | 1,500 (saat 10:00-11:00) |
| Saniyedeki istek (p95) | 20 req/s |
| Tahmin isteği/gün | 500 (her ürün için 1 kez) |
| Ortalama mesaj boyutu (Kafka) | 512 byte |

---

## Servis Bazında Kaynak Kullanımı

### API Gateway
| | Mevcut | Tepe | Limit |
|---|---|---|---|
| CPU | %15 | %45 | %70 |
| RAM | 420 MB | 650 MB | 1 GB |
| Req/s | 20 | 60 | ~200 |

**Ölçekleme tetikleyicisi:** CPU > %70 → HPA pod ekler (max 10 pod).

---

### order-service (En kritik)
| | Mevcut | Tepe | Limit |
|---|---|---|---|
| CPU | %20 | %55 | %65 |
| RAM | 480 MB | 700 MB | 1.5 GB |
| Kafka lag | 0–50 | 800 | 1000 (alert) |
| DB bağlantısı | 3/10 | 7/10 | 10/10 |

**Ölçekleme tetikleyicisi:** Kafka lag > 500 → HPA pod ekler (max 12 pod).

---

### ml-inference-service
| | Mevcut | Tepe | Limit |
|---|---|---|---|
| CPU | %25 | %55 | %60 |
| RAM | 1.2 GB | 2.1 GB | 3 GB |
| Tahmin süresi (p95) | 800ms | 3.2s | 10s (timeout) |

**Not:** Prophet modeli RAM'e model yükler — pod başlatma 45s sürer.  
**Ölçekleme tetikleyicisi:** CPU > %60 → HPA pod ekler (max 6 pod, 600s scale-down stabilizasyonu).

---

## Büyüme Senaryoları

### Senaryo A: 2× Büyüme (20,000 sipariş/gün)

| Servis | Şu an | Gerekli | Eylem |
|---|---|---|---|
| api-gateway | 2 pod | 3 pod | HPA otomatik |
| order-service | 3 pod | 5 pod | HPA otomatik |
| order.events partitions | 6 | 6 | Yeterli |
| ml-inference-service | 2 pod | 3 pod | HPA otomatik |
| order-db | db.t3.micro | db.t3.small | Manuel upgrade |

**Ek maliyet tahmini:** +€15/ay (Hetzner), +$40/ay (AWS RDS upgrade)

---

### Senaryo B: 10× Büyüme (100,000 sipariş/gün)

| Servis | Şu an | Gerekli | Eylem |
|---|---|---|---|
| api-gateway | 2 pod | 8 pod | HPA otomatik |
| order-service | 3 pod | 15 pod | order.events partition artır (6→18) |
| Kafka broker | 1 | 3 | Manuel — broker ekleme |
| order-db | tek node | read replica | Manuel — RDS Read Replica |
| ml-inference-service | 2 pod | 8 pod | HPA otomatik |

**order.events partition artırma:**
```bash
docker exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --alter --topic order.events --partitions 18
```

**Ek maliyet tahmini:** +€80/ay (Hetzner 2 sunucu), +$200/ay (AWS)

---

## Kafka Kapasite Hesabı

```
Günlük sipariş:          10,000
Mesaj boyutu:            512 byte
Günlük veri:             10,000 × 512 B = 5 MB/gün
Retention (7 gün):       35 MB

Tepe:                    1,500 sipariş/saat = 0.42 mesaj/sn
Kafka kapasitesi:        10 MB/sn (çok üstünde, sorun yok)
```

Partition sayısı consumer sayısıyla eşleşmeli:
```
6 partition → max 6 consumer (pod) parallel okuyabilir
```

---

## PostgreSQL Disk Büyümesi

| Veritabanı | Şu an | 1 yıl sonra | Notlar |
|---|---|---|---|
| order-db | 2 GB | ~25 GB | Sipariş geçmişi birikmekte |
| product-db | 500 MB | ~1 GB | Ürün sayısı yavaş büyür |
| forecast-db | 1 GB | ~8 GB | Tahmin sonuçları birikmekte |
| notification-db | 300 MB | ~2 GB | Uyarı geçmişi |

**Eylem:** 6 ayda bir disk kullanımını kontrol et:
```bash
docker exec order-db psql -U order_user orderdb \
  -c "SELECT pg_size_pretty(pg_database_size('orderdb'));"
```

---

## SLO Eşik → Ölçekleme Karar Matrisi

| Sinyal | Eşik | Otomatik | Manuel |
|---|---|---|---|
| CPU > %70, 5 dakika | HPA pod ekle | ✅ | — |
| Kafka lag > 500 | HPA pod ekle | ✅ | — |
| Kafka lag > 5000 | — | — | ✅ Partition artır |
| DB bağlantı > %80 | — | — | ✅ Pool büyüt / replica ekle |
| ML P99 > 5s | HPA pod ekle | ✅ | — |
| Disk > %80 | — | — | ✅ Volume genişlet |
| Broker 1 | — | — | ✅ Broker ekle |

---

## Kapasite Alarm Eşikleri (Grafana Alertleri)

```yaml
# monitoring/alerts/slo_alerts.yml'e ekle
- alert: CapacityWarning
  expr: |
    (kafka_log_log_size / 1073741824) > 10  # 10 GB
  for: 1h
  labels:
    severity: warning
  annotations:
    summary: "Kafka disk kullanımı 10GB'ı geçti — büyüme planını gözden geçir"
```
