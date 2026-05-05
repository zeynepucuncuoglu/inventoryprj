# Runbook: Service Crash / ServiceDown Alert

**Alert:** `ServiceDown`  
**Severity:** Critical (P1)  
**SLO Impact:** Availability SLO directly breached (up == 0)  
**Escalation:** Page on-call SRE immediately → incident bridge within 5 min

---

## 1. Immediate Triage (< 2 minutes)

```bash
# 1a. Check which service is down
curl -sf http://localhost:9090/api/v1/alerts | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
for a in d['data']['alerts']:
    if a['labels'].get('alertname') == 'ServiceDown':
        print(f\"DOWN: {a['labels'].get('job')} since {a['startsAt']}\")
"

# 1b. Check container status
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | \
  grep -E "demand-forecast|NAMES"

# 1c. Check exit code and OOM status
SERVICE=order-service  # replace with actual service name
docker inspect $SERVICE --format='Exit: {{.State.ExitCode}} | OOM: {{.State.OOMKilled}} | Restarts: {{.RestartCount}} | Status: {{.State.Status}}'
```

**Exit code reference:**

| Exit Code | Meaning | Action |
|---|---|---|
| 0 | Clean exit | Check logs for intentional shutdown |
| 1 | Application error | Check application logs for exception |
| 137 | SIGKILL (OOM or external) | Check OOM field; increase memory |
| 139 | Segfault | JVM crash — collect hs_err_pid file |
| 143 | SIGTERM (Docker stop) | Normal shutdown — was it intentional? |

---

## 2. Collect Diagnostic Data (Before Restarting)

**Never restart without collecting diagnostics for P1 incidents.**

```bash
SERVICE=order-service

# 2a. Get last 200 lines of logs
docker logs $SERVICE --tail 200 2>&1 | tee /tmp/${SERVICE}-crash-$(date +%s).log

# 2b. Look for the root cause exception
docker logs $SERVICE --since=5m 2>&1 | \
  grep -A 20 -E "Exception|Error|FATAL|OOMError|OutOfMemory"

# 2c. If JVM crash — collect hs_err file from container
docker cp $SERVICE:/app/hs_err_pid*.log /tmp/ 2>/dev/null && \
  echo "JVM crash file copied" || echo "No JVM crash file"

# 2d. If service is still running but unhealthy — take thread dump
CONTAINER_ID=$(docker ps -qf "name=$SERVICE")
if [ -n "$CONTAINER_ID" ]; then
  docker exec $CONTAINER_ID \
    jcmd 1 Thread.print 2>/dev/null > /tmp/${SERVICE}-threads-$(date +%s).txt
  docker exec $CONTAINER_ID \
    jcmd 1 VM.native_memory 2>/dev/null > /tmp/${SERVICE}-memory-$(date +%s).txt
fi
```

---

## 3. Restart Decision Matrix

| Scenario | Action |
|---|---|
| OOM (exit 137, OOMKilled=true) | Restart with higher memory limit; investigate heap dump |
| Application exception on startup | Fix config or rollback image — don't restart blindly |
| DB connection failure on startup | Verify DB is up first (Step 4), then restart |
| Kafka connection failure | Verify Kafka is up (Step 5), then restart |
| Unknown / clean exit | Restart once; if it crashes again — escalate |

---

## 4. Restart Service

```bash
# 4a. Standard restart (Docker Compose)
docker compose restart $SERVICE
sleep 10

# 4b. Watch startup logs
docker logs -f $SERVICE 2>&1 | grep -E "Started|ERROR|Exception|Caused by" &
LOG_PID=$!

# 4c. Wait for health endpoint
for i in $(seq 1 18); do
  sleep 5
  PORT=8082  # adjust per service
  STATUS=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:$PORT/actuator/health 2>/dev/null || echo "000")
  echo "T+$((i*5))s | Health: HTTP $STATUS"
  [ "$STATUS" = "200" ] && { echo "Service is UP"; kill $LOG_PID 2>/dev/null; break; }
done
```

---

## 5. Rollback If Service Cannot Start

```bash
# 5a. Rollback to previous image
bash demand-forecast-platform/scripts/rollback.sh $SERVICE

# 5b. If rollback script not available — manually specify previous tag
PREVIOUS_TAG="sha-abc1234"  # get from Docker Hub or CI run history
docker compose stop $SERVICE
# Edit docker-compose.yml to use PREVIOUS_TAG, then:
docker compose up -d $SERVICE

# 5c. Verify rollback succeeded
curl -sf http://localhost:8082/actuator/info | python3 -m json.tool
```

---

## 6. Check Dependencies (if service can't start)

```bash
# Check DB (example: order-service depends on order-db)
docker exec order-db pg_isready -U orderuser -d orderdb

# Check Kafka
docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 | head -5

# Check Redis (api-gateway only)
docker exec redis redis-cli ping

# Check ML service (forecast-service only)
curl -sf http://localhost:8000/health
```

---

## 7. Kubernetes-Specific Recovery

```bash
# K8s: Force pod restart by deleting (Deployment will recreate)
kubectl delete pod -n demand-forecast -l app=$SERVICE

# K8s: Check pod events
kubectl describe pod -n demand-forecast -l app=$SERVICE | tail -30

# K8s: Get crash logs from previous pod instance
kubectl logs -n demand-forecast -l app=$SERVICE --previous --tail=200

# K8s: Scale to 0 and back (force full restart)
kubectl scale deployment $SERVICE -n demand-forecast --replicas=0
sleep 5
kubectl scale deployment $SERVICE -n demand-forecast --replicas=2

# K8s: Check if node is the problem (pod evicted due to node pressure)
kubectl get events -n demand-forecast --sort-by='.lastTimestamp' | tail -20
```

---

## 8. Circuit Breaker Status (api-gateway perspective)

```bash
# Check if gateway's circuit breaker is OPEN for the crashed service
curl -sf http://localhost:8080/actuator/circuitbreakers | python3 -c "
import sys, json
d = json.load(sys.stdin)
for cb in d.get('circuitBreakers', []):
    print(f\"{cb['name']}: {cb['state']} (failure rate: {cb.get('failureRate', 0):.1f}%)\")
"

# If circuit is OPEN after service restart, manually reset it (if endpoint exposed)
curl -X POST http://localhost:8080/actuator/circuitbreakers/$SERVICE/reset 2>/dev/null || \
  echo "Auto-resets after half-open timeout (default 60s)"
```

---

## 9. Post-Incident Checklist

- [ ] Root cause identified and documented
- [ ] MTTR logged (time ServiceDown fired → time service recovered)
- [ ] Heap dump / logs uploaded to incident tracker
- [ ] Action items created: fix OOM, add resource limit, fix startup dependency
- [ ] Update alert runbook link if steps were missing or wrong
- [ ] Consider adding chaos experiment to verify recovery procedure

**Owner:** Platform Engineering  
**Escalation:** On-call SRE → Engineering Lead (P1 unresolved after 30 min)
