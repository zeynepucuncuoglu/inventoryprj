# Runbook: Kafka Consumer Lag

**Alert:** `KafkaConsumerLagWarning` / `KafkaConsumerLagCritical`  
**Severity:** Warning → Critical  
**SLO Impact:** Data freshness degradation; orders/forecasts processed late  
**Escalation:** P2 (>1000) → P1 (>5000) → wake on-call SRE

---

## 1. Immediate Triage (< 5 minutes)

```bash
# 1a. Check current lag across ALL consumer groups
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --all-groups 2>/dev/null | \
  awk 'NR==1 || $6~/[0-9]/' | sort -k6 -rn

# 1b. Identify which topic/partition has the lag
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group order-service-group

# 1c. Check if consumers are alive
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group order-service-group | grep -E "CONSUMER-ID|HOST|PARTITION"

# 1d. Check service health (is order-service even running?)
curl -sf http://localhost:8082/actuator/health | python3 -m json.tool
```

**Decision tree:**

| Consumers listed? | Service healthy? | Action |
|---|---|---|
| Yes | Yes | → Step 2: throughput issue |
| No | Yes | → Step 3: consumer crash / rebalance |
| No | No | → Step 4: service is down |
| Yes | No | → Step 4: service is unhealthy |

---

## 2. Consumers Running But Not Draining (Throughput Issue)

**Cause:** Message rate exceeds consumer processing capacity.

```bash
# 2a. Check message production rate on the topic
docker exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic order.events --time -1

# 2b. Check consumer CPU and heap
docker stats order-service --no-stream --format "table {{.CPUPerc}}\t{{.MemUsage}}"

# 2c. Check for slow DB queries causing consumer stall
docker logs order-service --since=10m 2>&1 | grep -E "HikariPool|slow|timeout|ERROR"

# 2d. Scale up consumer replicas (Docker Compose)
docker compose up --scale order-service=3 -d

# 2e. (Kubernetes) Scale up order-service
kubectl scale deployment order-service -n demand-forecast --replicas=5

# 2f. Monitor lag draining — expect improvement within 2-3 minutes
watch -n 5 "docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group order-service-group | tail -20"
```

**Resolution check:** Lag trending downward in Grafana → Kafka panel.

---

## 3. Consumer Crash / Partition Rebalance Storm

**Symptoms:** Consumer IDs change frequently in `--describe` output; lag spikes repeatedly.

```bash
# 3a. Check consumer group rebalance history in logs
docker logs order-service --since=30m 2>&1 | grep -E "rebalance|Revoked|Assigned|LeaveGroup"

# 3b. If rebalance storm, increase session timeout and poll interval
# Edit application.yml or set env vars and restart:
docker compose stop order-service
docker compose up -d order-service \
  -e SPRING_KAFKA_CONSUMER_SESSION_TIMEOUT_MS=45000 \
  -e SPRING_KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS=600000

# 3c. Check for OOM kills triggering restarts
docker inspect order-service | python3 -c "
import sys, json
d = json.load(sys.stdin)[0]
state = d['State']
print(f\"Exit code: {state['ExitCode']}, OOM: {state['OOMKilled']}, Restarts: {d['RestartCount']}\")
"

# 3d. Reset stuck consumer offsets ONLY if data loss is acceptable
# (confirm with product owner before running)
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --topic order.events \
  --reset-offsets --to-latest --execute
```

---

## 4. Service Down — Consumer Not Running

```bash
# 4a. Restart the service
docker compose restart order-service

# 4b. Watch startup
docker logs -f order-service 2>&1 | grep -E "Started|ERROR|Exception" &
sleep 30

# 4c. Verify health after restart
curl -sf http://localhost:8082/actuator/health

# 4d. If service fails to start — check DB connectivity
docker exec order-service curl -sf http://order-db:5432 || \
  echo "DB unreachable from container"
```

---

## 5. Escalation Criteria

| Condition | Action |
|---|---|
| Lag > 5000 after 10 min | Page on-call SRE |
| Lag > 10000 | P1 bridge call, consider topic replay strategy |
| Consumer group missing | Check for broker connectivity issue → kafka-lag runbook → kafka-crash runbook |
| Lag not draining after scale-up | Check topic partition count vs consumer count |

---

## 6. Kafka Partition Count Fix (if consumers > partitions)

```bash
# Kafka does not allow scaling past partition count
# Current partition count:
docker exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic order.events

# Increase partitions (non-destructive, but causes rebalance):
docker exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --alter --topic order.events \
  --partitions 12
```

---

## 7. Post-Incident

- [ ] Document in incident tracker: time lag detected, peak lag, MTTR
- [ ] Update capacity model if traffic spike was organic
- [ ] Check if chaos experiment triggered this (check #chaos-lab Slack)
- [ ] Review: should we pre-create more partitions for order.events?

**Owner:** Platform Engineering  
**Review cadence:** Every 6 months or after P1 incident
