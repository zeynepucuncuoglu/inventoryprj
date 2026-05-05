#!/usr/bin/env bash
# Auto-healing daemon for Docker Compose deployments.
# Runs as a background service or cron job every 60 seconds.
# Monitors all platform services and takes remediation action based on health state.
#
# Usage:
#   ./auto-heal.sh                    # run once
#   watch -n 60 ./auto-heal.sh        # run every 60s
#   nohup ./auto-heal.sh --daemon &   # run as background daemon

set -euo pipefail

COMPOSE_FILE="$(dirname "$0")/../docker-compose.yml"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
SLACK_WEBHOOK="${SLACK_WEBHOOK_PLATFORM:-}"
LOG_DIR="$(dirname "$0")/../logs"
HEAL_LOG="${LOG_DIR}/auto-heal.log"
MAX_RESTARTS_PER_HOUR=3

mkdir -p "$LOG_DIR"

log() {
  local level="$1"; shift
  echo "[$(date '+%Y-%m-%dT%H:%M:%S')] [$level] $*" | tee -a "$HEAL_LOG"
}

notify_slack() {
  local message="$1"
  if [ -n "$SLACK_WEBHOOK" ]; then
    curl -sf -X POST "$SLACK_WEBHOOK" \
      -H 'Content-Type: application/json' \
      -d "{\"text\": \"[AUTO-HEAL] ${message}\"}" > /dev/null || true
  fi
}

# Count restarts in the last hour for a service
count_recent_restarts() {
  local service="$1"
  grep -c "\[RESTART\] ${service}" "$HEAL_LOG" 2>/dev/null | \
    xargs -I{} bash -c "
      now=\$(date +%s)
      count=0
      while IFS= read -r line; do
        ts=\$(echo \"\$line\" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}')
        line_epoch=\$(date -d \"\$ts\" +%s 2>/dev/null || date -j -f '%Y-%m-%dT%H:%M:%S' \"\$ts\" +%s 2>/dev/null || echo 0)
        if [ \$((now - line_epoch)) -lt 3600 ]; then count=\$((count+1)); fi
      done < <(grep '\[RESTART\] ${service}' '$HEAL_LOG' 2>/dev/null)
      echo \$count
    " || echo "0"
}

# ── Service health check map ─────────────────────────────────────────────────
declare -A SERVICE_PORTS=(
  ["api-gateway"]="8080"
  ["product-service"]="8081"
  ["order-service"]="8082"
  ["forecast-service"]="8083"
  ["notification-service"]="8084"
  ["ml-inference-service"]="8000"
)

declare -A SERVICE_HEALTH_PATHS=(
  ["api-gateway"]="/actuator/health/liveness"
  ["product-service"]="/actuator/health/liveness"
  ["order-service"]="/actuator/health/liveness"
  ["forecast-service"]="/actuator/health/liveness"
  ["notification-service"]="/actuator/health/liveness"
  ["ml-inference-service"]="/health"
)

# ── Main healing loop ─────────────────────────────────────────────────────────
log "INFO" "=== AUTO-HEAL CYCLE START ==="

HEALED=0
DEGRADED=0

for SERVICE in "${!SERVICE_PORTS[@]}"; do
  PORT="${SERVICE_PORTS[$SERVICE]}"
  HEALTH_PATH="${SERVICE_HEALTH_PATHS[$SERVICE]}"

  # Check if container is running
  CONTAINER_STATUS=$(docker inspect "$SERVICE" --format='{{.State.Status}}' 2>/dev/null || echo "missing")

  if [ "$CONTAINER_STATUS" = "missing" ]; then
    log "WARN" "$SERVICE: container not found"
    DEGRADED=$((DEGRADED+1))
    continue
  fi

  # HTTP health check
  HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
    --max-time 5 \
    "http://localhost:${PORT}${HEALTH_PATH}" 2>/dev/null || echo "000")

  if [ "$HTTP_STATUS" = "200" ] && [ "$CONTAINER_STATUS" = "running" ]; then
    log "INFO" "$SERVICE: healthy (HTTP 200)"
    continue
  fi

  # ── Unhealthy or down — decide action ──────────────────────────────────────
  log "WARN" "$SERVICE: UNHEALTHY (status=$CONTAINER_STATUS, HTTP=$HTTP_STATUS)"
  DEGRADED=$((DEGRADED+1))

  # Check restart count in last hour
  RESTART_COUNT=$(docker inspect "$SERVICE" --format='{{.RestartCount}}' 2>/dev/null || echo "0")
  log "INFO" "$SERVICE: Docker restart count = $RESTART_COUNT"

  if [ "$RESTART_COUNT" -ge "$MAX_RESTARTS_PER_HOUR" ]; then
    log "ERROR" "$SERVICE: $RESTART_COUNT restarts — NOT auto-healing (crash loop). Manual intervention required."
    notify_slack "⚠️ *$SERVICE* is crash-looping (${RESTART_COUNT} restarts). Manual intervention required."
    continue
  fi

  # Collect diagnostic info before restart
  log "INFO" "$SERVICE: collecting diagnostics..."
  docker logs "$SERVICE" --tail 50 2>&1 | \
    tail -20 >> "${LOG_DIR}/${SERVICE}-pre-heal-$(date +%s).log" 2>/dev/null || true

  # Attempt restart
  log "WARN" "[RESTART] $SERVICE: initiating auto-restart"
  notify_slack "🔄 Auto-healing *$SERVICE* (HTTP $HTTP_STATUS, container $CONTAINER_STATUS)"

  docker compose -f "$COMPOSE_FILE" restart "$SERVICE" 2>&1 | \
    tee -a "$HEAL_LOG" || {
    log "ERROR" "$SERVICE: restart command failed"
    notify_slack "❌ Auto-heal FAILED for *$SERVICE* — restart command errored"
    continue
  }

  # Wait and verify recovery
  RECOVERED=false
  for attempt in 1 2 3 4 5; do
    sleep 10
    NEW_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
      --max-time 5 \
      "http://localhost:${PORT}${HEALTH_PATH}" 2>/dev/null || echo "000")
    log "INFO" "$SERVICE: recovery check $attempt/5 — HTTP $NEW_STATUS"
    if [ "$NEW_STATUS" = "200" ]; then
      RECOVERED=true
      HEALED=$((HEALED+1))
      DEGRADED=$((DEGRADED-1))
      break
    fi
  done

  if [ "$RECOVERED" = "true" ]; then
    log "INFO" "$SERVICE: RECOVERED successfully"
    notify_slack "✅ *$SERVICE* recovered after auto-heal"
  else
    log "ERROR" "$SERVICE: FAILED to recover after restart — escalating"
    notify_slack "🚨 *$SERVICE* did NOT recover after auto-heal. Page on-call SRE immediately."
  fi
done

# ── Kafka consumer lag check ──────────────────────────────────────────────────
KAFKA_CONTAINER=$(docker ps -qf name=kafka --filter status=running | head -1)
if [ -n "$KAFKA_CONTAINER" ]; then
  MAX_LAG=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --describe --all-groups 2>/dev/null | \
    awk 'NR>1 && $6~/^[0-9]+$/ {if ($6+0 > max) max=$6+0} END {print max+0}')

  log "INFO" "Kafka max consumer lag: $MAX_LAG"

  if [ "${MAX_LAG:-0}" -gt 5000 ]; then
    log "WARN" "Kafka lag > 5000 — scaling order-service"
    CURRENT_REPLICAS=$(docker compose -f "$COMPOSE_FILE" ps order-service | grep -c "Up" || echo "1")
    if [ "$CURRENT_REPLICAS" -lt 3 ]; then
      docker compose -f "$COMPOSE_FILE" up --scale order-service=3 -d
      log "INFO" "Scaled order-service to 3 replicas"
      notify_slack "📈 Auto-scaled *order-service* to 3 replicas due to Kafka lag=${MAX_LAG}"
    fi
  fi
fi

# ── DB connection pool check ──────────────────────────────────────────────────
POOL_CRITICAL=$(curl -sf \
  "${PROMETHEUS_URL}/api/v1/query?query=(hikaricp_connections_active/hikaricp_connections_max)>0.9" \
  2>/dev/null | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    services = [r['metric'].get('job','?') for r in d['data']['result']]
    print(' '.join(services))
except: print('')
" 2>/dev/null || echo "")

if [ -n "$POOL_CRITICAL" ]; then
  log "WARN" "DB pool > 90% on: $POOL_CRITICAL"
  notify_slack "⚠️ DB connection pool > 90% on: *$POOL_CRITICAL*. Check runbook."
fi

log "INFO" "=== AUTO-HEAL CYCLE END | healed=$HEALED degraded=$DEGRADED ==="

# Exit non-zero if services are still degraded (useful for monitoring)
[ "$DEGRADED" -eq 0 ]
