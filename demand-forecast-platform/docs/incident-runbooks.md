# Incident Runbooks

## Runbook Index

| Alert | Runbook |
|-------|---------|
| ServiceDown | [#service-down](#service-down) |
| UptimeBelowSLA | [#uptime-below-sla](#uptime-below-sla) |
| HighErrorRate | [#high-error-rate) |
| HighP95Latency | [#high-latency](#high-latency) |
| KafkaConsumerLagHigh | [#kafka-consumer-lag](#kafka-consumer-lag) |
| JvmHeapUsageHigh | [#jvm-heap-high](#jvm-heap-high) |

---

## Service Down

**Trigger:** `up{job="<service>"} == 0` for > 1 minute

### Immediate actions

```bash
# 1. Check container status
docker ps -a | grep <service>

# 2. View recent logs (last 200 lines)
docker logs <service> --tail=200

# 3. Attempt restart
docker compose restart <service>

# 4. If restart fails, rebuild
docker compose up -d --build <service>
```

### Escalation
If service does not come healthy within 5 minutes after restart, escalate and open a P1 incident.

---

## Uptime Below SLA

**Trigger:** 24-hour uptime average < 99%

### Steps
1. Identify which service caused the drop: check Grafana → Service Health row for gaps
2. Correlate with deployment times: `git log --oneline --since="24 hours ago"`
3. If caused by a bad deployment: roll back
   ```bash
   # Roll back to previous Docker image
   docker tag demand-forecast-<service>:<previous-build> demand-forecast-<service>:latest
   docker compose up -d <service>
   ```
4. Document the outage window and MTTR in the post-mortem template

---

## High Error Rate

**Trigger:** HTTP 5xx rate > 1% for > 2 minutes

### Steps

```bash
# 1. Find which endpoints are erroring
docker logs <service> --tail=500 | grep -E "ERROR|5[0-9]{2}"

# 2. Check downstream dependencies (DB, Kafka, ML service)
bash scripts/health-check.sh

# 3. Check if it's a specific endpoint — look at Prometheus
# Query: sum(rate(http_server_requests_seconds_count{status=~"5..",job="<service>"}[5m])) by (uri)
```

### Common causes

| Cause | Fix |
|-------|-----|
| DB connection pool exhausted | Increase `spring.datasource.hikari.maximum-pool-size` |
| Kafka consumer offset lag causing processing errors | Check Kafka consumer lag runbook |
| ML service timeout causing forecast-service 5xx | Check circuit breaker state |
| OOM in JVM | Check JVM heap runbook |

---

## High Latency

**Trigger:** p95 > 500 ms or p99 > 1 s for > 5 minutes

### Steps

```bash
# 1. Identify which service is slow
# Grafana: HTTP Latency Percentiles panel — which service line is high?

# 2. Check if it's database-related
docker logs <service> --tail=200 | grep -iE "slow|timeout|lock"

# 3. Check JVM GC pause
# Prometheus: rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])

# 4. Check forecast-service → ML service latency specifically
docker logs forecast-service --tail=100 | grep -iE "timeout|CircuitBreaker|retry"
```

### For forecast-service specifically
The ML inference call is the most likely bottleneck (65 s timeout configured).
If the ML service is slow, the circuit breaker (50% failure threshold) will open.

---

## Kafka Consumer Lag

**Trigger:** Consumer lag > 1000 records for > 5 minutes

### Steps

```bash
# 1. Check lag per consumer group
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --all-groups

# 2. Check if the consuming service is healthy
curl http://localhost:<port>/actuator/health

# 3. Check for deserialization errors in service logs
docker logs <service> --tail=200 | grep -iE "deserialization|ClassCast|SerializationException"

# 4. If consumer is stuck, restart it
docker compose restart <service>

# 5. If messages are poison pills (can't be deserialized), reset offset
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group <group-id> \
  --topic <topic> \
  --reset-offsets --to-latest --execute
```

---

## JVM Heap High

**Trigger:** JVM heap > 85% for > 5 minutes

### Steps

```bash
# 1. Get heap dump for analysis
docker exec <service> jcmd 1 GC.heap_info
docker exec <service> jcmd 1 VM.gc_heap_info

# 2. Force GC (temporary relief)
docker exec <service> jcmd 1 GC.run

# 3. If heap stays high, restart the service
docker compose restart <service>

# 4. Long-term: review memory settings in docker-compose.yml
```

Add to service environment in docker-compose.yml:
```yaml
environment:
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

---

## Post-Mortem Template

```markdown
## Incident Post-Mortem

**Date:** YYYY-MM-DD
**Duration:** X hours Y minutes
**Severity:** P1 / P2 / P3
**Affected services:**

### Timeline
- HH:MM — Alert fired
- HH:MM — Investigation started
- HH:MM — Root cause identified
- HH:MM — Mitigation applied
- HH:MM — Service restored

### Root Cause

### Impact
- Users affected:
- Requests failed:
- SLA impact:

### Resolution

### Action Items
| Action | Owner | Due |
|--------|-------|-----|
|        |       |     |
```
