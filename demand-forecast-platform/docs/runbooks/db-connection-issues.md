# Runbook: Database Connection Issues

**Alerts:** `DBConnectionPoolHigh` (>80%) / `DBConnectionPoolExhausted` (timeouts) / `DBQuerySlow`  
**Severity:** Warning → Critical  
**SLO Impact:** Service degradation → complete outage if pool exhausted  
**Affected Services:** product-service (5435), order-service (5433), forecast-service (5434), notification-service (5436)

---

## 1. Immediate Triage (< 5 minutes)

```bash
# 1a. Check HikariCP pool metrics across all services
for SERVICE_PORT in 8081 8082 8083 8084; do
  echo -n "Port $SERVICE_PORT: "
  curl -sf "http://localhost:${SERVICE_PORT}/actuator/metrics/hikaricp.connections.active" 2>/dev/null | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"active={int(d['measurements'][0]['value'])}\")" || \
    echo "unreachable"
done

# 1b. Check via Prometheus for all services
curl -sf 'http://localhost:9090/api/v1/query?query=hikaricp_connections_active' | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in d['data']['result']:
    pool = r['metric'].get('pool', '?')
    job  = r['metric'].get('job', '?')
    active = int(float(r['value'][1]))
    print(f'{job:30s} {pool:20s} active={active}')
"

# 1c. Check Prometheus for connection timeouts (critical signal)
curl -sf 'http://localhost:9090/api/v1/query?query=hikaricp_connections_timeout_total' | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in d['data']['result']:
    if float(r['value'][1]) > 0:
        print(f\"TIMEOUT on {r['metric'].get('job','?')}: {r['value'][1]} timeouts\")
"
```

---

## 2. Identify Root Cause

### 2A. Check Active Connections in PostgreSQL

```bash
# Replace DB_NAME, DB_USER, DB_PORT per affected service:
# product-db: port 5435, productdb, productuser
# order-db:   port 5433, orderdb, orderuser
# forecast-db: port 5434, forecastdb, forecastuser

DB_PORT=5433; DB_USER=orderuser; DB_NAME=orderdb

docker exec order-db psql -U $DB_USER $DB_NAME -c "
SELECT count(*), state, application_name, wait_event_type, wait_event
FROM pg_stat_activity
GROUP BY state, application_name, wait_event_type, wait_event
ORDER BY count DESC;
"
```

### 2B. Check for Connection Leaks (Long-Running Idle Connections)

```bash
docker exec order-db psql -U orderuser orderdb -c "
SELECT pid, usename, application_name, client_addr,
       state, now() - state_change AS idle_duration,
       left(query, 80) AS last_query
FROM pg_stat_activity
WHERE state = 'idle'
  AND (now() - state_change) > interval '10 minutes'
ORDER BY idle_duration DESC;
"
```

### 2C. Check pg_stat_activity for Max Connections

```bash
docker exec order-db psql -U orderuser orderdb -c "
SELECT setting::int AS max_connections FROM pg_settings WHERE name = 'max_connections';
" 
docker exec order-db psql -U orderuser orderdb -c "
SELECT count(*) AS total_connections, 
       count(*) FILTER (WHERE state = 'active') AS active,
       count(*) FILTER (WHERE state = 'idle') AS idle
FROM pg_stat_activity;
"
```

### 2D. Check for Blocking / Deadlocks

```bash
docker exec order-db psql -U orderuser orderdb -c "
SELECT blocked.pid AS blocked_pid,
       blocked_activity.query AS blocked_query,
       blocking.pid AS blocking_pid,
       blocking_activity.query AS blocking_query,
       now() - blocked_activity.query_start AS wait_duration
FROM pg_locks blocked
JOIN pg_stat_activity blocked_activity ON blocked.pid = blocked_activity.pid
JOIN pg_locks blocking ON blocking.relation = blocked.relation AND blocking.granted
JOIN pg_stat_activity blocking_activity ON blocking.pid = blocking_activity.pid
WHERE NOT blocked.granted
ORDER BY wait_duration DESC;
"
```

---

## 3. Remediation Actions

### 3A. Terminate Idle Connections to Free Pool

```bash
# Terminate idle connections > 5 minutes (safe — HikariCP will reconnect)
docker exec order-db psql -U orderuser orderdb -c "
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE state = 'idle'
  AND application_name LIKE 'HikariPool%'
  AND (now() - state_change) > interval '5 minutes'
  AND pid <> pg_backend_pid();
"
```

### 3B. Kill Blocking Queries

```bash
# Identify blocking PID from step 2D, then:
BLOCKING_PID=1234  # replace with actual PID
docker exec order-db psql -U orderuser orderdb -c "SELECT pg_terminate_backend($BLOCKING_PID);"
```

### 3C. Restart HikariCP Pool (via service restart)

```bash
# If pool is corrupted — restart the service
docker compose restart order-service

# Kubernetes
kubectl rollout restart deployment/order-service -n demand-forecast
```

### 3D. Emergency: Increase Pool Size Without Full Restart

```bash
# Add more connections temporarily (restart with higher pool size)
docker compose stop order-service
docker compose run -d \
  -e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30 \
  -e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=5 \
  order-service
```

### 3E. PostgreSQL max_connections Increase (Requires DB Restart)

```bash
# ONLY if pg_stat_activity shows max_connections reached
# Current setting:
docker exec order-db psql -U postgres -c "SHOW max_connections;"

# Increase (requires PostgreSQL restart — confirm maintenance window):
docker exec order-db psql -U postgres -c "ALTER SYSTEM SET max_connections = 200;"
docker compose restart order-db
# Wait for DB restart, then restart dependent services
sleep 15
docker compose restart order-service
```

---

## 4. PostgreSQL Vacuum / Bloat Check (If Queries Are Slow)

```bash
# Check for table bloat causing slow full-table scans
docker exec order-db psql -U orderuser orderdb -c "
SELECT relname AS table_name,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
       n_dead_tup AS dead_tuples,
       last_autovacuum,
       last_autoanalyze
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC;
"

# Force VACUUM ANALYZE if autovacuum is behind
docker exec order-db psql -U orderuser orderdb -c "VACUUM ANALYZE orders;"
```

---

## 5. Kubernetes Database Checks

```bash
# Check if postgres pod is healthy
kubectl get pods -n demand-forecast | grep -E "db|postgres"

# Check PVC disk space
kubectl exec -n demand-forecast deploy/order-service -- \
  df -h /var/lib/postgresql/data

# If using CloudSQL/RDS — check cloud console for:
# - CPU > 80%
# - Storage autoscaling
# - Active connections vs max_connections
```

---

## 6. Escalation Criteria

| Condition | Action |
|---|---|
| Pool > 90% utilized | P1 — scale service or increase pool |
| Timeouts > 10 in 1 min | P1 — service is dropping requests |
| DB unreachable | P1 — page DB admin |
| Deadlocks recurring | Engineering ticket, investigate transaction ordering |
| Disk > 85% on DB volume | Urgent — expand storage before DB crashes |

---

## 7. Post-Incident Checklist

- [ ] Root cause: leak / pool too small / DB overloaded / long tx?
- [ ] Export `pg_stat_activity` snapshot during incident
- [ ] Check if Flyway migration left open transactions
- [ ] Review: does HikariCP `connectionTimeout` need increasing?
- [ ] Add `hikaricp.connections.timeout_total` to Grafana alert panel
- [ ] Check if chaos experiment triggered this (`#chaos-lab` Slack)

**Owner:** Platform Engineering + Database Team  
**Related:** `high-latency.md`, `service-crash.md`
