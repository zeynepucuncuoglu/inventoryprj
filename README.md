# Demand Forecast Platform

An event-driven microservices platform that tracks inventory, processes orders, runs ML-based demand forecasting, and fires real-time alerts when stock or demand thresholds are breached.

Built as a portfolio project demonstrating production-grade backend engineering practices: hexagonal architecture, Kafka event streaming, per-service PostgreSQL databases, JWT-authenticated API gateway, circuit breakers, Flyway migrations, Testcontainers integration tests, and a full CI/CD pipeline.

---

## Architecture

```
                        ┌─────────────────────────────────────────────┐
                        │              API Gateway :8080               │
                        │   JWT auth · Rate limiting (Redis) · CORS   │
                        │         Circuit breaker per service          │
                        └──────────┬──────────┬──────────┬────────────┘
                                   │          │          │
                    ┌──────────────▼──┐  ┌────▼───────┐  ┌▼───────────────┐
                    │ product-service │  │order-service│  │forecast-service│
                    │     :8081       │  │   :8082     │  │    :8083       │
                    └────────┬────────┘  └─────┬───────┘  └──────┬─────────┘
                             │                 │                  │
                    product-db             order-db          forecast-db
                    (postgres :5435)   (postgres :5433)  (postgres :5434)
                             │                 │                  │
                             └─────────────────▼──────────────────┘
                                               │
                                        Apache Kafka
                              ┌────────────────┴────────────────┐
                              │         Kafka Topics             │
                              │  product.events                  │
                              │  order.events                    │
                              │  forecast.requested              │
                              │  forecast.completed              │
                              │  notification.alerts             │
                              └────────┬────────────┬────────────┘
                                       │            │
                          ┌────────────▼──┐   ┌─────▼──────────────┐
                          │notification-  │   │  ml-inference-      │
                          │service :8084  │   │  service :8000      │
                          └───────┬───────┘   │  (Python/FastAPI)   │
                                  │           └────────────────────┘
                           notification-db
                           (postgres :5436)
```

---

## Services

| Service | Port | Swagger UI | Description |
|---|---|---|---|
| api-gateway | 8080 | — | Spring Cloud Gateway — JWT auth, rate limiting, circuit breakers |
| product-service | 8081 | [/swagger-ui.html](http://localhost:8081/swagger-ui.html) | Product catalog, stock management |
| order-service | 8082 | [/swagger-ui.html](http://localhost:8082/swagger-ui.html) | Order lifecycle (PENDING → CONFIRMED → SHIPPED → DELIVERED) |
| forecast-service | 8083 | [/swagger-ui.html](http://localhost:8083/swagger-ui.html) | Triggers ML forecasts, persists results |
| notification-service | 8084 | [/swagger-ui.html](http://localhost:8084/swagger-ui.html) | Alert history — low stock, demand surges, forecast failures |
| ml-inference-service | 8000 | [/docs](http://localhost:8000/docs) | Python/FastAPI — linear-regression demand forecasting |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Java services | Java 21, Spring Boot 3.2.4, Spring Data JPA, Spring Kafka |
| API gateway | Spring Cloud Gateway, Resilience4j circuit breaker, Redis rate limiting |
| ML service | Python 3.12, FastAPI, scikit-learn, pandas |
| Messaging | Apache Kafka 7.6 (Confluent) |
| Databases | PostgreSQL 16 — one per service |
| Migrations | Flyway |
| Auth | JWT (HMAC-SHA256) |
| API docs | springdoc-openapi 2.5.0 / Swagger UI |
| Testing | JUnit 5, Mockito, Testcontainers, pytest |
| CI/CD | GitHub Actions — test matrix → Docker build |
| Infra | Docker Compose, Redis 7.2 |

---

## Running Locally

**Prerequisites:** Docker and Docker Compose.

```bash
git clone <repo-url>
cd demand-forecast-platform
docker compose up --build
```

On first boot, `kafka-init` creates all topics and each service runs its Flyway migrations automatically.

**Useful URLs once running:**

| URL | What it is |
|---|---|
| http://localhost:8080 | API Gateway (all requests go here) |
| http://localhost:8090 | Kafka UI — browse topics and messages |
| http://localhost:8081/swagger-ui.html | Product Service API |
| http://localhost:8082/swagger-ui.html | Order Service API |
| http://localhost:8083/swagger-ui.html | Forecast Service API |
| http://localhost:8084/swagger-ui.html | Notification Service API |
| http://localhost:8000/docs | ML Inference Service API |

---

## API Overview

All requests through the gateway require a `Bearer` JWT token (except `/api/v1/auth/**`).

### Products `POST /api/v1/products`
```json
{ "name": "Widget A", "sku": "WGT-001", "category": "Tools", "price": 29.99, "initialStock": 500 }
```

### Stock adjustment `PATCH /api/v1/products/{id}/stock`
```json
{ "delta": -50, "reason": "ORDER_FULFILLMENT" }
```
Publishes `StockUpdatedEvent` to `product.events`. Notification-service consumes it and evaluates against the low-stock threshold.

### Orders `POST /api/v1/orders`
```json
{ "customerId": "cust-123", "items": [{ "productId": "...", "sku": "WGT-001", "quantity": 10, "unitPrice": 29.99 }] }
```
State machine: `PENDING → CONFIRMED → SHIPPED → DELIVERED`. Cancellation only allowed before `SHIPPED`.

### Forecasts `POST /api/v1/forecasts`
```json
{ "productId": "...", "sku": "WGT-001", "horizonDays": 30 }
```
Publishes `ForecastRequestedEvent` → forecast-service calls ml-inference-service → persists result → publishes `ForecastCompletedEvent` → notification-service evaluates for demand surge or low demand alerts.

### Alerts `GET /api/v1/alerts?sku=WGT-001&severity=CRITICAL`
Filter by `sku`, `type` (`LOW_STOCK`, `DEMAND_SURGE`, `LOW_DEMAND_FORECAST`, `FORECAST_FAILED`), or `severity` (`INFO`, `WARNING`, `CRITICAL`).

---

## Event Flow

```
Stock adjusted
  → product.events
    → notification-service: evaluate LOW_STOCK threshold

Order placed / confirmed / shipped / delivered
  → order.events

Forecast requested
  → forecast.requested
    → forecast-service: calls ML service, persists ForecastPoints
      → forecast.completed
        → notification-service: evaluate DEMAND_SURGE / LOW_DEMAND_FORECAST
```

---

## Testing

```bash
# Run all tests for a specific service (requires Java 21)
cd demand-forecast-platform/services/product-service
mvn test

# ML service
cd demand-forecast-platform/services/ml-inference-service
pytest tests/ -v
```

| Service | Unit | Service (Mockito) | Integration (Testcontainers) | Total |
|---|---|---|---|---|
| product-service | 13 | 9 | 8 | **30** |
| order-service | 16 | 9 | — | **25** |
| forecast-service | 16 | — | — | **16** |
| notification-service | 10 | — | 9 | **19** |
| ml-inference-service | — | — | 9 (pytest) | **9** |
| **Total** | | | | **99** |

Testcontainers tests spin up a real PostgreSQL 16 container and run Flyway migrations before each test class — no H2, no mocks for the database layer.

---

## CI/CD

GitHub Actions pipeline defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

1. **Test Java services** — matrix build across all 4 Java services with Java 21 (Temurin)
2. **Test ML service** — Python 3.12, pytest
3. **Docker build** — builds all 6 service images after tests pass, uses GHA layer cache

---

## Design Decisions

**Hexagonal architecture** — each service has `domain` (pure Java, no framework dependencies), `application` (use cases, orchestration), and `infrastructure` (JPA, Kafka, HTTP) layers. The domain never imports Spring, making it trivial to swap the persistence adapter without touching business logic.

**One database per service** — services share no tables. Inter-service communication is exclusively through Kafka events. This makes each service independently deployable and prevents schema coupling.

**Optimistic locking** — `ProductEntity` uses JPA `@Version`. The repository adapter loads the existing entity before updating to preserve the version field, preventing `ObjectOptimisticLockingFailureException` under concurrent stock adjustments.

**Circuit breakers at the gateway** — Resilience4j circuit breakers wrap each downstream service. If a service crosses a 50% failure rate in a 10-request sliding window, the gateway opens the circuit for 15 seconds and returns a structured fallback response instead of letting failures cascade.
