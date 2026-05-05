# Production Architecture — Demand Forecast Platform

## Textual Architecture Diagram

```
═══════════════════════════════════════════════════════════════════════════════
                     DEMAND FORECAST PLATFORM — PRODUCTION
═══════════════════════════════════════════════════════════════════════════════

┌──────────────────────────────────────────────────────────────────────────┐
│                        TRAFFIC INGRESS                                   │
│                                                                          │
│   Internet  ──────►  Load Balancer (K8s LoadBalancer / Nginx Ingress)   │
│   Mobile              │                                                  │
│   Internal            ▼                                                  │
│   Services     ┌─────────────┐      JWT Auth     ┌──────────────────┐   │
│                │ API Gateway │──────────────────► │ Redis (rate limit)│   │
│                │ :8080       │  Resilience4j CBs  └──────────────────┘   │
│                │ HPA: 2-10   │                                           │
│                └──────┬──────┘                                           │
│                       │  Routes (Spring Cloud Gateway)                   │
└───────────────────────┼──────────────────────────────────────────────────┘
                        │
┌───────────────────────┼──────────────────────────────────────────────────┐
│                 MICROSERVICES LAYER                                       │
│                       │                                                  │
│     ┌─────────────────┼─────────────────────┐                           │
│     ▼                 ▼                       ▼                          │
│ ┌───────────┐   ┌───────────┐         ┌───────────┐   ┌──────────────┐  │
│ │ product-  │   │  order-   │         │ forecast- │   │notification- │  │
│ │ service   │   │  service  │         │  service  │   │  service     │  │
│ │ :8081     │   │  :8082    │         │  :8083    │   │  :8084       │  │
│ │ HPA: 2-8  │   │ HPA: 3-12 │         │ HPA: 2-6  │   │ HPA: 2-4    │  │
│ └─────┬─────┘   └─────┬─────┘         └─────┬─────┘   └──────┬───────┘  │
│       │               │                     │                │           │
│       ▼               ▼                     │                │           │
│  ┌─────────┐    ┌─────────┐                 │     HTTP       │           │
│  │product  │    │ order   │                 ▼     Call       │           │
│  │   db    │    │   db    │         ┌─────────────────┐      │           │
│  │(PG 16)  │    │(PG 16)  │         │ ml-inference-   │      │           │
│  └─────────┘    └─────────┘         │ service (Python)│      │           │
│                                     │ :8000, HPA: 2-6 │      │           │
│                                     └─────────┬───────┘      │           │
│                                               │forecast-db    │notification│
│                                               ▼(PG 16)       ▼ -db(PG16) │
└───────────────────────────────────────────────────────────────────────────┘
                        │ Kafka Events (async)
┌───────────────────────┼──────────────────────────────────────────────────┐
│                 EVENT BUS — Apache Kafka                                  │
│                                                                           │
│  Topics:                                                                  │
│  ├── product.events       (3 partitions, RF=3)                           │
│  ├── order.events         (6 partitions, RF=3) ◄── highest throughput    │
│  ├── forecast.requested   (3 partitions, RF=3)                           │
│  ├── forecast.completed   (3 partitions, RF=3)                           │
│  └── notification.alerts  (3 partitions, RF=3)                           │
│                                                                           │
│  Kafka UI: :8090  |  Zookeeper: :2181                                    │
└───────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                 OBSERVABILITY STACK                                       │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  METRICS                                                          │    │
│  │  Services ──/actuator/prometheus──► Prometheus :9090              │    │
│  │  Prometheus ──────────────────────► Grafana :3000 (dashboards)   │    │
│  │  Prometheus ──────────────────────► Alertmanager :9093           │    │
│  │  Alertmanager ─────────────────────► PagerDuty (P1 critical)     │    │
│  │                 └──────────────────► Slack (all severities)      │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  LOGS (ELK Stack)                                                 │    │
│  │  Containers ──► Filebeat ──► Logstash :5044 ──► Elasticsearch    │    │
│  │  Elasticsearch ──────────────────────────────► Kibana :5601      │    │
│  │  Log indices: demand-forecast-{service}-{date}                   │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  DISTRIBUTED TRACING                                              │    │
│  │  Services ──OTLP/gRPC──► Jaeger :4317 ──► Jaeger UI :16686      │    │
│  │  trace_id propagated via: X-B3-TraceId, traceparent headers      │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                 CI/CD PIPELINE                                            │
│                                                                           │
│  develop branch:                                                          │
│  Code Push ──► GitHub Actions ──► Test(parallel) ──► Build ──► Dev       │
│                                                                           │
│  main branch:                                                             │
│  Code Push ──► Security Scan ──► Test(parallel) ──► Build                │
│              ──► Push Images ──► Deploy Staging ──► SLO Gate             │
│              ──► [APPROVAL GATE] ──► Deploy Production (blue-green)       │
│              ──► Health Check ──► Auto-rollback on failure               │
│                                                                           │
│  Jenkins: parallel test → build → Trivy scan → push → multi-env deploy   │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                 AUTO-HEALING LAYER                                        │
│                                                                           │
│  K8s: restartPolicy=Always, liveness probe → kubelet restart              │
│  K8s: readiness probe → removed from LB until healthy                    │
│  K8s: HPA → scale out on CPU/memory/Kafka lag                            │
│  K8s: PodDisruptionBudget → min replicas during node drain               │
│  Alertmanager webhook → kubectl rollout restart on ServiceDown           │
│  Docker Compose: restart:unless-stopped + auto-heal.sh cron              │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                 CHAOS / INCIDENT SIMULATION                               │
│                                                                           │
│  chaos-toolkit experiments:                                               │
│  ├── kafka-lag-experiment.json     → pause consumer, produce 2000 msgs   │
│  ├── service-crash-experiment.json → SIGKILL + measure MTTR              │
│  ├── db-exhaustion-experiment.json → hold N connections to exhaust pool  │
│  └── latency-spike-experiment.json → tc netem inject 800ms delay         │
│                                                                           │
│  Fault injection scripts:                                                 │
│  ├── inject-kafka-lag.sh           (manual, duration-controlled)         │
│  ├── inject-latency.sh             (tc netem, with auto-cleanup)          │
│  ├── exhaust-db-connections.py     (psycopg2, hold idle connections)      │
│  └── simulate-crash.sh            (SIGKILL + MTTR measurement)           │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Component Breakdown

| Component | Technology | Role | Scaling |
|---|---|---|---|
| API Gateway | Spring Cloud Gateway + Resilience4j | JWT auth, rate limiting, circuit breaking | HPA 2-10 pods |
| product-service | Spring Boot 3.2 / Java 21 | Product catalog, inventory | HPA 2-8 pods |
| order-service | Spring Boot 3.2 / Java 21 | Order lifecycle, Kafka consumer | HPA 3-12 pods (Kafka lag metric) |
| forecast-service | Spring Boot 3.2 / Java 21 | Trigger ML, persist results | HPA 2-6 pods |
| notification-service | Spring Boot 3.2 / Java 21 | Alert history, Kafka consumer | HPA 2-4 pods |
| ml-inference-service | FastAPI / Python 3.12 | Prophet + sklearn forecasting | HPA 2-6 pods |
| Apache Kafka | Confluent 7.6 | Event bus, 5 topics | 3 brokers in prod (1 in dev) |
| PostgreSQL | PG 16 Alpine | Per-service DB | PVC + streaming replica in prod |
| Redis | 7.2 Alpine | Rate limiting (sliding window) | Single node + AOF |
| Prometheus | 2.x | Metrics collection (15s interval) | Statefulset |
| Grafana | 10.x | Dashboards | 1 replica |
| Alertmanager | 0.27 | Alert routing (PD + Slack) | 1 replica |
| Elasticsearch | 8.13 | Log storage (per-service indices) | 1 node dev / 3 node prod |
| Logstash | 8.13 | Log parsing + enrichment | 1 replica |
| Filebeat | 8.13 | Container log collector | DaemonSet |
| Kibana | 8.13 | Log search + dashboards | 1 replica |
| Jaeger | 1.57 | Distributed tracing (OTLP) | All-in-one dev / operator prod |

---

## SLO Definitions

| SLO | Target | Alert (Warning) | Alert (Critical) |
|---|---|---|---|
| Availability | 99.9% uptime | Burn rate 6× | Burn rate 14.4× (P1) |
| Latency P95 | < 300ms | > 300ms for 5min | — |
| Latency P99 | < 1000ms | — | > 1000ms for 3min (P1) |
| Kafka Lag | < 1000 msg | > 1000 for 5min | > 5000 for 2min (P1) |
| DB Pool | < 80% used | > 80% for 5min | Timeout count > 0 (P1) |
| DB Query P95 | < 500ms | > 500ms for 5min | — |

---

## Folder / Repo Structure (Added by this transformation)

```
demand-forecast-platform/
│
├── chaos/                           ← NEW: Incident simulation
│   ├── chaos-toolkit/
│   │   ├── kafka-lag-experiment.json
│   │   ├── service-crash-experiment.json
│   │   ├── db-exhaustion-experiment.json
│   │   └── latency-spike-experiment.json
│   └── scripts/
│       ├── inject-kafka-lag.sh
│       ├── inject-latency.sh
│       ├── exhaust-db-connections.py
│       └── simulate-crash.sh
│
├── observability/                   ← NEW: Full observability stack
│   ├── docker-compose.observability.yml   (ELK + Jaeger + Alertmanager)
│   ├── elk/
│   │   ├── logstash/pipeline/demand-forecast.conf
│   │   ├── logstash/config/logstash.yml
│   │   └── filebeat/filebeat.yml
│   └── alertmanager/
│       ├── alertmanager.yml
│       └── templates/slack.tmpl
│
├── kubernetes/                      ← NEW: K8s manifests
│   ├── namespaces.yaml
│   ├── secrets.yaml
│   ├── services/
│   │   ├── api-gateway.yaml          (Deployment + Service + HPA + PDB)
│   │   └── backend-services.yaml     (all 5 other services)
│   └── auto-healing/
│       └── restart-webhook.yaml      (Alertmanager → kubectl restart)
│
├── monitoring/alerts/
│   ├── service_alerts.yml            (existing — 7 groups, 13 rules)
│   └── slo_alerts.yml               ← NEW: burn-rate SLO alerts
│
├── scripts/
│   ├── auto-heal.sh                 ← NEW: Docker Compose auto-healing daemon
│   ├── deployment.sh                (existing)
│   ├── health-check.sh              (existing)
│   ├── log-analyzer.sh              (existing)
│   └── rollback.sh                  (existing)
│
├── docs/runbooks/                   ← NEW: Operational runbooks
│   ├── kafka-lag.md
│   ├── service-crash.md
│   ├── high-latency.md
│   └── db-connection-issues.md
│
└── Jenkinsfile                      ← UPGRADED: Multi-env + approval gates
```

---

## CV Bullet Points

**Senior DevOps / SRE:**

- Designed and implemented a **multi-environment CI/CD pipeline** (dev → staging → production) with mandatory approval gates, SLO-based promotion guards, and automatic blue-green rollback on production failures, reducing MTTR from hours to under 10 minutes.

- Built a **production-grade incident simulation framework** using Chaos Toolkit with 4 experiment types (Kafka lag, service SIGKILL, DB connection exhaustion, network latency injection via tc netem), enabling regular GameDay testing and measurable SLA validation.

- Implemented **multi-window SLO burn-rate alerting** (Google SRE Workbook pattern) with 5-minute/1-hour fast-burn P1 paging and 30-minute/6-hour slow-burn P2 notification via Alertmanager → PagerDuty → Slack, cutting false-positive alert noise by ~70%.

- Deployed full **ELK observability stack** (Elasticsearch 8.13 + Logstash + Filebeat) with structured Logstash pipeline that parses Spring Boot JSON logs, extracts trace IDs, tags exceptions/circuit-breaker events, and routes to per-service daily indices for Kibana dashboards.

- Integrated **distributed tracing** with Jaeger (OTLP/gRPC) across 6 microservices (5 Java + 1 Python FastAPI), enabling end-to-end trace correlation with ELK log correlation via trace_id field.

- Authored **Kubernetes HPA configurations** scaling on CPU, memory, and custom Kafka consumer lag metrics (via Prometheus Adapter / KEDA), with PodDisruptionBudgets guaranteeing zero-downtime during node drains.

- Implemented **auto-healing mechanisms** at multiple layers: K8s liveness probes → pod restart, readiness probes → traffic drain, Alertmanager webhook → `kubectl rollout restart`, and a Docker Compose auto-heal daemon with crash-loop detection and Slack escalation.

- Wrote **production-grade operational runbooks** for Kafka lag, service crashes, high latency, and DB connection exhaustion, each with decision trees, diagnostic commands, remediation scripts, and post-incident checklists.

**Backend / Platform Engineering:**

- Maintained event-driven microservices platform on Apache Kafka (6 topics, 5 consumer groups) with Spring Boot 3.2 / Java 21, achieving 99.9% uptime and P99 latency < 500ms under peak load.

- Implemented **resilience patterns** across API Gateway: Resilience4j circuit breakers with configurable OPEN/HALF_OPEN state, Redis-based sliding-window rate limiting, and configurable retry policies with exponential backoff.

- Designed **per-service PostgreSQL isolation** (4 separate databases) with Flyway migrations, HikariCP connection pool tuning, and Testcontainers-based integration tests running against real PostgreSQL instances.
