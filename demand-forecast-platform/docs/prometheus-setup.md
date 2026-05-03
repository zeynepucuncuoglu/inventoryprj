# Prometheus & Grafana Setup Guide

## Architecture

```
Services → /actuator/prometheus ─┐
                                  ├──► Prometheus (9090) ──► Grafana (3000)
ML Service → /metrics ───────────┘
```

## 1. Add Prometheus Dependency to Java Services

Each Spring Boot service needs the Micrometer Prometheus registry. Add to `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## 2. Expose the Prometheus Endpoint

Add to each service's `application.yml` (or `application-docker.yml`):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

## 3. Add Prometheus to Python ML Service

Install:
```
prometheus-fastapi-instrumentator==6.1.0
```

In `app/main.py`:
```python
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI(...)
Instrumentator().instrument(app).expose(app)
```

This exposes `/metrics` in the standard Prometheus text format.

## 4. Start the Monitoring Stack

```bash
cd demand-forecast-platform
docker compose up -d prometheus grafana
```

Verify Prometheus is scraping:
- Open http://localhost:9090/targets
- All targets should show `State: UP`

## 5. Access Grafana

- URL: http://localhost:3000
- Default login: `admin` / `admin` (change on first login)
- The **DemandForecast Platform Overview** dashboard is auto-provisioned

## 6. Key Metrics Reference

| Metric | Description |
|--------|-------------|
| `up` | 1=service up, 0=service down |
| `http_server_requests_seconds_count` | Total HTTP request count |
| `http_server_requests_seconds_bucket` | Histogram buckets for latency |
| `jvm_memory_used_bytes{area="heap"}` | JVM heap used |
| `jvm_gc_pause_seconds_sum` | Total GC pause time |
| `kafka_consumer_fetch_manager_records_lag` | Consumer lag per partition |
| `process_uptime_seconds` | Process uptime |

## 7. Alert Rules

Alert rules are in `monitoring/alerts/service_alerts.yml` and cover:

| Alert | Threshold | Severity |
|-------|-----------|----------|
| ServiceDown | up == 0 for 1 min | critical |
| UptimeBelowSLA | < 99% over 24 h | warning |
| HighErrorRate | > 1% HTTP 5xx | warning |
| HighP95Latency | p95 > 500 ms | warning |
| HighP99Latency | p99 > 1 s | critical |
| KafkaConsumerLagHigh | > 1000 records | warning |
| JvmHeapUsageHigh | > 85% | warning |

## 8. Reload Prometheus Config Without Restart

```bash
curl -X POST http://localhost:9090/-/reload
```

(Requires `--web.enable-lifecycle` flag — already set in docker-compose.yml)
