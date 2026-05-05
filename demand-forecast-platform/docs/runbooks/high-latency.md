# Runbook: High Latency / Latency SLO Breach

**Alerts:** `LatencyP95SLOBreached` (P95 > 300ms) / `LatencyP99SLOBreached` (P99 > 1s)  
**Severity:** Warning (P95) → Critical (P99)  
**SLO Impact:** Latency SLO breach; tail users experiencing timeouts  
**Customer Impact:** Order creation delayed; forecast requests timing out

---

## 1. Immediate Triage (< 5 minutes)

```bash
# 1a. Check current latency per service in Prometheus
curl -sf 'http://localhost:9090/api/v1/query?query=histogram_quantile(0.99,rate(http_server_requests_seconds_bucket[5m]))' | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in sorted(d['data']['result'], key=lambda x: float(x['value'][1]), reverse=True):
    print(f\"{r['metric'].get('job','?'):30s} P99: {float(r['value'][1])*1000:.0f}ms\")
"

# 1b. Check which endpoint/URI is slow
curl -sf 'http://localhost:9090/api/v1/query?query=histogram_quantile(0.99,rate(http_server_requests_seconds_bucket[5m]))by(uri,job)' | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in sorted(d['data']['result'], key=lambda x: float(x['value'][1]), reverse=True)[:10]:
    m = r['metric']
    print(f\"{m.get('job','?'):25s} {m.get('uri','?'):40s} {float(r['value'][1])*1000:.0f}ms\")
"

# 1c. Check traffic volume — is it a traffic spike?
curl -sf 'http://localhost:9090/api/v1/query?query=sum by(job)(rate(http_server_requests_seconds_count[5m]))' | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in d['data']['result']:
    print(f\"{r['metric'].get('job','?'):30s} {float(r['value'][1]):.1f} req/s\")
"
```

---

## 2. Identify Root Cause

### 2A. Database Slow Queries

```bash
# Check HikariCP pool utilization
curl -sf 'http://localhost:9090/api/v1/query?query=hikaricp_connections_active/hikaricp_connections_max' | \
  python3 -c "import sys,json; d=json.load(sys.stdin); [print(r['metric'].get('job'), f\"{float(r['value'][1])*100:.0f}%\") for r in d['data']['result']]"

# Check active PostgreSQL queries
docker exec order-db psql -U orderuser orderdb -c "
SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '1 seconds'
ORDER BY duration DESC;
"

# Check for table locks
docker exec order-db psql -U orderuser orderdb -c "
SELECT blocked.pid, blocked_activity.query AS blocked_query,
       blocking.pid AS blocking_pid, blocking_activity.query AS blocking_query
FROM pg_catalog.pg_locks blocked
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked.pid = blocked_activity.pid
JOIN pg_catalog.pg_locks blocking ON blocking.relation = blocked.relation
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking.pid = blocking_activity.pid
WHERE NOT blocked.granted;
"

# Kill long-running queries if needed (replace 1234 with actual PID)
# docker exec order-db psql -U orderuser orderdb -c "SELECT pg_terminate_backend(1234);"
```

### 2B. JVM GC Pressure

```bash
# Check GC pause time
curl -sf 'http://localhost:9090/api/v1/query?query=rate(jvm_gc_pause_seconds_sum[5m])/rate(jvm_gc_pause_seconds_count[5m])' | \
  python3 -c "import sys,json; d=json.load(sys.stdin); [print(r['metric'].get('job'), f\"{float(r['value'][1])*1000:.0f}ms avg GC pause\") for r in d['data']['result']]"

# Check heap usage
curl -sf 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes{area=\"heap\"}/jvm_memory_max_bytes{area=\"heap\"}' | \
  python3 -c "import sys,json; d=json.load(sys.stdin); [print(r['metric'].get('job'), f\"{float(r['value'][1])*100:.0f}% heap\") for r in d['data']['result']]"
```

### 2C. ML Service Latency (affects forecast-service)

```bash
# Direct ML service latency check
time curl -sf -X POST http://localhost:8000/predict \
  -H 'Content-Type: application/json' \
  -d '{"product_id":"test","historical_data":[100,110,105],"forecast_horizon":7}'

# Check ML service logs for slow inference
docker logs ml-inference-service --since=10m 2>&1 | grep -E "inference_time|took|slow|ERROR"
```

### 2D. Kafka Consumer Delay Causing API Timeout

```bash
# If APIs wait for async Kafka processing, check lag
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --all-groups 2>/dev/null | \
  awk '$6 > 0 {print}'
```

### 2E. Circuit Breaker Half-Open Retry Storm

```bash
# Check if circuit breakers are in HALF_OPEN state causing retry overhead
curl -sf http://localhost:8080/actuator/circuitbreakers | python3 -c "
import sys, json
d = json.load(sys.stdin)
for cb in d.get('circuitBreakers', []):
    state = cb['state']
    if state in ['HALF_OPEN', 'OPEN']:
        print(f\"WARN: {cb['name']} = {state}\")
"
```

---

## 3. Remediation Actions

### Traffic Spike — Scale Up

```bash
# Docker Compose
docker compose up --scale product-service=3 --scale order-service=4 -d

# Kubernetes
kubectl scale deployment order-service -n demand-forecast --replicas=6
kubectl scale deployment api-gateway -n demand-forecast --replicas=4
```

### DB Connection Pool Exhausted

```bash
# Increase HikariCP pool size without restart (if app supports runtime config)
# Or increase via env var restart:
docker compose stop order-service
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20 docker compose up -d order-service
```

### Kill Blocking Queries

```bash
# Terminate all queries > 30 seconds
docker exec order-db psql -U orderuser orderdb -c "
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE (now() - query_start) > interval '30 seconds'
  AND state != 'idle'
  AND pid <> pg_backend_pid();
"
```

### Force GC (last resort — causes brief pause)

```bash
SERVICE_PORT=8082
curl -sf -X POST http://localhost:${SERVICE_PORT}/actuator/gc 2>/dev/null || \
  docker exec $(docker ps -qf name=order-service) jcmd 1 GC.run
```

### Rate-Limit Burst Traffic at Gateway

```bash
# Temporarily lower Redis rate limit for specific IPs
docker exec redis redis-cli SET "rate_limit:${ABUSER_IP}:limit" 10 EX 3600
```

---

## 4. Distributed Trace Investigation (Jaeger)

```bash
# Open Jaeger UI and find slow traces
open http://localhost:16686

# Filter: Service = api-gateway, min duration = 500ms, last 15min
# Trace shows exactly which downstream service/DB query is slow
```

---

## 5. Escalation Criteria

| Condition | Action |
|---|---|
| P99 > 1s for > 5 min | P1 — page on-call SRE |
| Root cause not identified in 15 min | Bridge call |
| Latency on all services simultaneously | Check infra — Kafka, DB, network |
| GC pause > 5s | OOM risk — restart JVM, capture heap dump first |

---

## 6. Post-Incident

- [ ] Capture Jaeger trace ID of slowest requests
- [ ] Export Grafana latency panel as PNG and attach to incident
- [ ] Identify: was this DB, GC, Kafka, ML, or traffic?
- [ ] Create ticket: add DB index, tune pool size, increase replicas, etc.
- [ ] Add load test scenario to catch this regression in staging

**Owner:** Platform Engineering  
**Related:** `kafka-lag.md`, `db-connection-issues.md`, `service-crash.md`
