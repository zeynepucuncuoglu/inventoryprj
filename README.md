# Demand Forecast Platform

An event-driven microservices platform for inventory tracking, order processing, ML-based demand forecasting, and real-time alerting — built to production-grade standards.

---

## Architecture

```
                    ┌────────────────────────────────────────────────────┐
                    │                   API Gateway :8080                 │
                    │  JWT auth · User-based rate limiting (Redis)        │
                    │  Circuit breakers (Resilience4j) · CORS            │
                    │  Refresh token rotation · /api/v1/ versioning      │
                    └──────────┬───────────┬──────────┬──────────────────┘
                               │           │          │
               ┌───────────────▼──┐  ┌─────▼──────┐  ┌▼────────────────┐
               │ product-service  │  │order-service│  │forecast-service │
               │     :8081        │  │   :8082     │  │    :8083        │
               └────────┬─────────┘  └──────┬──────┘  └───────┬─────────┘
                        │                   │                  │ HTTP
                   product-db          order-db           ml-inference
                  (PG :5435)          (PG :5433)          service :8000
                        │                   │            (Python/FastAPI)
                        └───────────────────▼
                                      Apache Kafka
                        ┌─────────────────────────────────────────┐
                        │  product.events    · product.events.DLT  │
                        │  order.events      · order.events.DLT    │
                        │  forecast.requested/completed + DLTs     │
                        │  notification.alerts + DLT               │
                        └──────────┬──────────────┬────────────────┘
                                   │              │
                      ┌────────────▼──┐    ┌──────▼───────────┐
                      │notification-  │    │  Schema Registry  │
                      │service :8084  │    │     :8091         │
                      └───────┬───────┘    └──────────────────┘
                              │
                        notification-db
                          (PG :5436)

┌─────────────────────────────────────────────────────────────────────┐
│                      OBSERVABILITY STACK                             │
│  Prometheus :9090 → Alertmanager :9093 → PagerDuty / Slack          │
│  Grafana :3000    (dashboards + SLO burn-rate alerting)              │
│  Filebeat → Logstash → Elasticsearch → Kibana :5601  (ELK logs)     │
│  Jaeger :16686    (distributed tracing, OTLP)                        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Services

| Service | Port | Swagger UI | Description |
|---|---|---|---|
| api-gateway | 8080 | — | Spring Cloud Gateway — JWT auth, rate limiting, circuit breakers, refresh tokens |
| product-service | 8081 | [/swagger-ui.html](http://localhost:8081/swagger-ui.html) | Product catalog, stock management |
| order-service | 8082 | [/swagger-ui.html](http://localhost:8082/swagger-ui.html) | Order lifecycle (PENDING → CONFIRMED → SHIPPED → DELIVERED) |
| forecast-service | 8083 | [/swagger-ui.html](http://localhost:8083/swagger-ui.html) | Triggers ML forecasts, persists results |
| notification-service | 8084 | [/swagger-ui.html](http://localhost:8084/swagger-ui.html) | Alert history — low stock, demand surges, forecast failures |
| ml-inference-service | 8000 | [/docs](http://localhost:8000/docs) | Python/FastAPI — Prophet + scikit-learn demand forecasting |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Java services | Java 21, Spring Boot 3.2.4, Spring Data JPA, Spring Kafka |
| API gateway | Spring Cloud Gateway, Resilience4j, Redis rate limiting (user-based) |
| ML service | Python 3.12, FastAPI, Prophet, scikit-learn, pandas |
| Messaging | Apache Kafka 7.6 (Confluent) + Schema Registry + Dead Letter Queues |
| Databases | PostgreSQL 16 — one per service, Flyway migrations |
| Auth | JWT (HMAC-SHA256) + Redis-backed refresh token rotation |
| API docs | springdoc-openapi 2.5.0 / Swagger UI |
| Testing | JUnit 5, Mockito, Testcontainers, pytest, k6 load tests |
| CI/CD | GitHub Actions (multi-env: dev→staging→production) + Jenkins |
| Monitoring | Prometheus + Grafana + Alertmanager (SLO burn-rate alerting) |
| Logging | ELK Stack (Filebeat + Logstash + Elasticsearch + Kibana) |
| Tracing | Jaeger (OpenTelemetry OTLP) |
| Chaos | Chaos Toolkit + fault injection scripts (Kafka lag, crash, DB exhaustion, latency) |
| Kubernetes | Deployments + HPA (CPU/memory/Kafka lag) + PDB + liveness/readiness probes |
| Security | Trivy image scanning, HTTPS (Caddy + Let's Encrypt), Dependabot |
| Infra | Docker Compose, Redis 7.2, Schema Registry 7.6 |

---

## Running Locally

**Prerequisites:** Docker and Docker Compose (16 GB RAM recommended for full stack).

```bash
git clone <repo-url>
cd demand-forecast-platform

# Copy and fill in secrets
cp .env.example .env

# Start core services
docker compose up --build

# Optional: full observability (ELK + Jaeger + Alertmanager)
docker compose -f docker-compose.yml \
               -f observability/docker-compose.observability.yml up --build

# Optional: HTTPS (requires domain + ports 80/443 open)
docker compose -f docker-compose.yml \
               -f docker-compose.https.yml up
```

On first boot `kafka-init` creates all topics + DLT queues and each service runs Flyway migrations automatically.

---

## URLs

| URL | Description |
|---|---|
| http://localhost:8080 | API Gateway |
| http://localhost:8090 | Kafka UI |
| http://localhost:8091 | Schema Registry |
| http://localhost:8081/swagger-ui.html | Product Service API |
| http://localhost:8082/swagger-ui.html | Order Service API |
| http://localhost:8083/swagger-ui.html | Forecast Service API |
| http://localhost:8084/swagger-ui.html | Notification Service API |
| http://localhost:8000/docs | ML Inference Service API |
| http://localhost:9090 | Prometheus |
| http://localhost:9093 | Alertmanager |
| http://localhost:3000 | Grafana (admin / admin) |
| http://localhost:5601 | Kibana |
| http://localhost:16686 | Jaeger |

---

## API Overview

All requests through the gateway require `Authorization: Bearer <token>` (except `/api/v1/auth/**`).

### Auth
```bash
# Refresh access token (15 min TTL)
POST /api/v1/auth/refresh
{"refreshToken": "uuid"}

# Logout — invalidates refresh token in Redis
POST /api/v1/auth/logout
{"refreshToken": "uuid"}
```

### Products
```bash
POST  /api/v1/products
{"name":"Widget A","sku":"WGT-001","price":29.99,"stockQuantity":500,"lowStockThreshold":50}

PATCH /api/v1/products/{id}/stock
{"delta":-50,"reason":"ORDER_FULFILLMENT"}
```

### Orders
```bash
POST /api/v1/orders
{"customerId":"cust-123","items":[{"productId":"...","quantity":10,"unitPrice":29.99}]}
# State machine: PENDING → CONFIRMED → SHIPPED → DELIVERED
```

### Forecasts
```bash
POST /api/v1/forecasts
{"productId":"...","sku":"WGT-001","horizonDays":30}
```

### Alerts
```bash
GET /api/v1/alerts?sku=WGT-001&severity=CRITICAL
# type: LOW_STOCK | DEMAND_SURGE | LOW_DEMAND_FORECAST | FORECAST_FAILED
```

---

## Event Flow

```
Stock adjusted    → product.events → notification-service (LOW_STOCK check)
Order updated     → order.events   → forecast-service (trigger after N orders)
Forecast done     → forecast.completed → notification-service (DEMAND_SURGE check)
Failed message    → *.DLT (30-day retention, replay-able)
```

---

## Testing

```bash
# Java service
cd demand-forecast-platform/services/order-service && mvn test

# Python ML service
cd demand-forecast-platform/services/ml-inference-service && pytest tests/ -v

# Load test (requires k6)
k6 run demand-forecast-platform/tests/load/k6-smoke.js
```

| Service | Tests | Coverage Minimum |
|---|---|---|
| product-service | 30 | 70% |
| order-service | 25 | 70% |
| forecast-service | 16 | 70% |
| notification-service | 19 | 70% |
| ml-inference-service | 9 (pytest) | 80% |

Testcontainers spins up real PostgreSQL 16 — no H2, no mock DBs. JaCoCo enforces line coverage on every build.

---

## CI/CD

```
develop → Security scan → Parallel tests → Build → Deploy dev

main    → Security scan → Parallel tests → Build
        → Deploy staging → E2E + k6 load test → SLO gate
        → [Manual approval + change ticket]
        → Deploy production (blue-green) → Auto-rollback on failure
```

Defined in [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) and [`Jenkinsfile`](demand-forecast-platform/Jenkinsfile).  
Dependabot configured for weekly security updates across all services.

> **Note:** Deployment steps are configured as non-blocking (`continue-on-error: true`) because this is a portfolio/demo project without live production server secrets. The real quality gate (`ci-gate` job) blocks the pipeline on test failures or Docker build failures — deploy steps are intentionally non-blocking since no server secrets are configured.

---

## SLO / Alerting

| SLO | Target | P1 Alert | P2 Alert |
|---|---|---|---|
| Availability | 99.9% | Burn-rate 14.4× → PagerDuty | Burn-rate 6× → Slack |
| Latency P95 | < 300ms | — | > 300ms for 5 min |
| Latency P99 | < 1000ms | > 1000ms for 3 min | — |
| Kafka lag | < 1000 msgs | > 5000 msgs | > 1000 msgs |
| DB pool | < 80% | Pool timeout > 0 | Pool > 80% |

---

## Chaos Engineering

| Script | Simulates |
|---|---|
| `inject-kafka-lag.sh` | Pause consumer, accumulate messages |
| `simulate-crash.sh` | SIGKILL + measure MTTR |
| `exhaust-db-connections.py` | Fill connection pool → 503s |
| `inject-latency.sh` | 800ms network delay via tc netem |

Chaos Toolkit experiments: `chaos/chaos-toolkit/*.json`

---

## Kubernetes

Manifests in `kubernetes/`: Deployment + Service + HPA + PodDisruptionBudget for every service.  
HPA scales on CPU, memory, and Kafka consumer lag. Alertmanager webhook auto-restarts failed pods.

---

## Runbooks

- [Kafka Lag](demand-forecast-platform/docs/runbooks/kafka-lag.md)
- [Service Crash](demand-forecast-platform/docs/runbooks/service-crash.md)
- [High Latency](demand-forecast-platform/docs/runbooks/high-latency.md)
- [DB Connection Issues](demand-forecast-platform/docs/runbooks/db-connection-issues.md)

Architecture decisions: [`docs/adr/`](demand-forecast-platform/docs/adr/) (ADR-001 to ADR-003).  
Capacity planning: [`docs/capacity-planning.md`](demand-forecast-platform/docs/capacity-planning.md).
