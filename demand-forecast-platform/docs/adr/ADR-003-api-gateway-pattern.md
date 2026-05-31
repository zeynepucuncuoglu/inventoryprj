# ADR-003: API Gateway Pattern — Spring Cloud Gateway

**Tarih:** 2025-10-01  
**Durum:** Kabul Edildi  
**Karar Veren:** Platform Engineering

---

## Bağlam

6 mikroservis var. Dış dünya bu servislere nasıl erişecek? JWT doğrulama, rate limiting ve circuit breaker her serviste mi olacak, yoksa merkezi bir noktada mı?

---

## Karar

**Spring Cloud Gateway** üzerinde tek giriş noktası. JWT, rate limiting ve circuit breaker gateway'de yönetilecek.

---

## Gerekçe

**Her serviste ayrı auth'un sorunları:**
- 6 servisin hepsine JWT kütüphanesi eklemek gerekir (tekrar eden kod).
- JWT secret değiştirildiğinde 6 servis güncellenmeli.
- Rate limiting mantığı Redis'te tutulacaksa her servisin Redis'e bağlanması gerekir.

**Gateway Pattern'in Avantajları:**
- **Tek sorumluluk:** Downstream servisler `X-User-Id` header'ına güvenir, JWT bilmez.
- **Merkezi rate limiting:** Redis'teki sayaçlar tek noktadan yönetilir. Kullanıcı bazlı limit (bkz. ADR sonrası değişiklik: `userKeyResolver`).
- **Circuit breaker:** Resilience4j `product-service` çöküşünü fark eder, fallback döner. Downstream servislerin timeout yönetiyle uğraşmasına gerek kalmaz.
- **Routing esnekliği:** `/api/v1/products/**` → `product-service:8081`, versiyonlama gateway'de yönetilir.

**Spring Cloud Gateway vs NGINX/Envoy:**
- Spring Cloud Gateway Java ekosistemiyle entegre, aynı dilde yazılabiliyor.
- Resilience4j ve Spring Security entegrasyonu native.
- NGINX daha performanslı ama Java kodlamasına ihtiyaç duyulan özellikler (custom filter, JWT parsing) zorlaşır.

---

## Güven Modeli

```
İnternet → API Gateway (JWT doğrula) → Private Network → Downstream Servisler
                                                         (X-User-Id güven)
```

Downstream servisler private Docker/K8s ağında — dışarıdan erişilemez. Gateway'den gelen `X-User-Id` header'ına güvenirler. Production'da mTLS ile daha güçlü trust modeli kurulabilir.

---

## Sonuçlar

- Tüm rotalar `/api/v1/` prefix'i ile versiyonlandı.
- `AuthenticationFilter` JWT doğrular, `X-User-Id` ve `X-User-Role` ekler.
- Rate limiting: kullanıcı bazlı (authenticated) + IP bazlı (anonymous).
- Refresh token yönetimi gateway'de Redis üzerinden yapılıyor (`RefreshTokenService`).
- Fallback: `FallbackController` circuit breaker açıkken kullanıcıya anlamlı mesaj döner.

---

## Değerlendirilen Alternatifleri Reddetme Nedenleri

| Alternatif | Neden Reddedildi |
|---|---|
| Her serviste auth | Tekrar eden kod, merkezi yönetim zorluğu |
| NGINX | Custom Java filter yazılamaz, Resilience4j entegrasyonu yok |
| AWS API Gateway | Cloud vendor lock-in, yerel geliştirme zorluğu |
| Kong | Ek öğrenme eğrisi, Java ekosistemi dışı |
