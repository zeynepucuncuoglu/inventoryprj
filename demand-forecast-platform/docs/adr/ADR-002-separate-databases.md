# ADR-002: Her Servis İçin Ayrı PostgreSQL Veritabanı

**Tarih:** 2025-10-01  
**Durum:** Kabul Edildi  
**Karar Veren:** Platform Engineering

---

## Bağlam

4 Java servisi (product, order, forecast, notification) veri saklıyor. Tek bir paylaşılan veritabanı mı, yoksa her servisin kendi veritabanı mı kullanılacak?

---

## Karar

**Her servis kendi PostgreSQL veritabanına sahip olacak.**

---

## Gerekçe

**Paylaşılan DB'nin Sorunları:**
- Bir servisin şema değişikliği diğer servisleri etkiler (deploy koordinasyonu gerektirir).
- `order-service`'in ağır sorgusu `product-service`'in bağlantı havuzunu tüketebilir.
- Tek bir DB çöküşü tüm servisleri durdurur.
- Servisler birbirinin tablolarını doğrudan okumaya başlar — "distributed monolith" antipattern.

**Ayrı DB'nin Avantajları:**
- **Şema bağımsızlığı:** `order-service` şemasını değiştirir, diğer servisler etkilenmez.
- **Hata izolasyonu:** `notification-db` çökerse sadece bildirimler durur, siparişler devam eder.
- **Bağımsız ölçekleme:** `order-db` için daha büyük instance seçilebilir.
- **Flyway migration'ları bağımsız:** Her servis kendi migration'larını yönetir.

**Dezavantaj — Kabul Edilen Ödün:**
- JOIN yapılamaz — servisler arası veri ilişkisi Kafka olayları üzerinden kurulur.
- 4 ayrı DB bağlantı havuzu yönetimi.
- Yerel geliştirmede 4 PostgreSQL container çalıştırmak gerekiyor.

Bu ödünler, servis bağımsızlığının sağladığı faydalar karşısında kabul edilebilir düzeyde.

---

## Sonuçlar

- Port ayrımı: product(5435), order(5433), forecast(5434), notification(5436).
- Her servisin kendi HikariCP pool konfigürasyonu var.
- Servisler arası veri paylaşımı sadece Kafka event'leri üzerinden yapılıyor.
- Her DB için ayrı backup işlemi (`scripts/backup-databases.sh`).
