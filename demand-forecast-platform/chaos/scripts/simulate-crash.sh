#!/usr/bin/env bash
# Simulate a service crash via SIGKILL and measure recovery time (MTTR).
# Validates: Prometheus alert fires, Docker restart policy recovers, health endpoint returns 200.
# Usage: ./simulate-crash.sh [service] [signal]
# Example: ./simulate-crash.sh product-service SIGKILL

set -euo pipefail

SERVICE="${1:-product-service}"
SIGNAL="${2:-SIGKILL}"
COMPOSE_FILE="$(dirname "$0")/../../docker-compose.yml"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

log() { echo "[$(date '+%Y-%m-%dT%H:%M:%S')] [CHAOS/crash] $*"; }

# Map service to health port
declare -A SERVICE_PORTS=(
  ["api-gateway"]="8080"
  ["product-service"]="8081"
  ["order-service"]="8082"
  ["forecast-service"]="8083"
  ["notification-service"]="8084"
  ["ml-inference-service"]="8000"
)

PORT="${SERVICE_PORTS[$SERVICE]:-8080}"
HEALTH_URL="http://localhost:${PORT}/actuator/health"
[ "$SERVICE" = "ml-inference-service" ] && HEALTH_URL="http://localhost:${PORT}/health"

log "=== SERVICE CRASH SIMULATION ==="
log "Service: $SERVICE (port $PORT)"
log "Signal : $SIGNAL"

# 1. Verify service is up before crash
log "Pre-crash health check..."
STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
if [ "$STATUS" != "200" ]; then
  log "ERROR: $SERVICE not healthy (HTTP $STATUS) — aborting chaos"
  exit 1
fi
log "Pre-crash: $SERVICE is UP (HTTP 200)"

# 2. Record crash time
CRASH_TIME=$(date +%s)
log "Sending $SIGNAL to $SERVICE at epoch $CRASH_TIME"

CONTAINER_ID=$(docker ps -qf "name=${SERVICE}" --filter status=running | head -1)
docker kill --signal="$SIGNAL" "$CONTAINER_ID"
log "Signal sent to container $CONTAINER_ID"

# 3. Measure time-to-down
log "Waiting for service to go DOWN..."
for i in $(seq 1 15); do
  sleep 2
  STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
  if [ "$STATUS" != "200" ]; then
    DOWN_TIME=$(date +%s)
    TTD=$((DOWN_TIME - CRASH_TIME))
    log "Service DOWN confirmed at T+${TTD}s (HTTP $STATUS)"
    break
  fi
  log "Still up... (attempt $i)"
done

# 4. Check if Prometheus alert fired
sleep 10
ALERT_COUNT=$(curl -sf "${PROMETHEUS_URL}/api/v1/alerts" 2>/dev/null | \
  python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    alerts = [a for a in d['data']['alerts'] if a['labels'].get('alertname') == 'ServiceDown' and '${SERVICE}' in str(a['labels'])]
    print(len(alerts))
except: print(0)
" 2>/dev/null || echo "0")
log "Prometheus ServiceDown alerts for ${SERVICE}: $ALERT_COUNT"

# 5. Wait for Docker restart policy to kick in (restart: always)
log "Waiting for Docker restart policy to recover $SERVICE..."
RECOVERED=false
for i in $(seq 1 30); do
  sleep 5
  STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
  if [ "$STATUS" = "200" ]; then
    RECOVERY_TIME=$(date +%s)
    MTTR=$((RECOVERY_TIME - CRASH_TIME))
    log "Service RECOVERED at T+${MTTR}s (HTTP 200)"
    RECOVERED=true
    break
  fi
  log "T+$((i * 5))s | Waiting... (HTTP $STATUS)"
done

if [ "$RECOVERED" = "false" ]; then
  log "WARNING: $SERVICE did not recover within 150s — manual intervention required"
  log "Run: docker compose -f $COMPOSE_FILE start $SERVICE"
  exit 1
fi

log "=== CRASH SIMULATION COMPLETE ==="
log "MTTR: ${MTTR}s"
