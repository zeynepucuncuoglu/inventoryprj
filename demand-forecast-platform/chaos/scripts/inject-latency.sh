#!/usr/bin/env bash
# Inject network latency into a running container using Linux tc netem.
# Requires: container must run on Linux host (or Linux VM on Mac).
# Usage: ./inject-latency.sh [service] [delay_ms] [jitter_ms] [duration_seconds]
# Example: ./inject-latency.sh ml-inference-service 800 100 120

set -euo pipefail

SERVICE="${1:-ml-inference-service}"
DELAY_MS="${2:-500}"
JITTER_MS="${3:-50}"
DURATION="${4:-60}"

log() { echo "[$(date '+%Y-%m-%dT%H:%M:%S')] [CHAOS/latency] $*"; }

log "=== LATENCY INJECTION START ==="
log "Target  : $SERVICE"
log "Latency : ${DELAY_MS}ms ± ${JITTER_MS}ms"
log "Duration: ${DURATION}s"

CONTAINER_ID=$(docker ps -qf "name=${SERVICE}" --filter status=running | head -1)
if [ -z "$CONTAINER_ID" ]; then
  log "ERROR: Container $SERVICE not found or not running"
  exit 1
fi

CONTAINER_PID=$(docker inspect --format='{{.State.Pid}}' "$CONTAINER_ID")
log "Container PID: $CONTAINER_PID"

cleanup() {
  log "Removing latency injection..."
  nsenter -t "$CONTAINER_PID" -n -- \
    tc qdisc del dev eth0 root 2>/dev/null || true
  log "Latency injection removed."
}
trap cleanup EXIT INT TERM

# Apply tc netem rule inside container network namespace
nsenter -t "$CONTAINER_PID" -n -- \
  tc qdisc add dev eth0 root netem delay "${DELAY_MS}ms" "${JITTER_MS}ms" distribution normal

log "Latency injected. Monitoring for ${DURATION}s..."

# Show current rule
nsenter -t "$CONTAINER_PID" -n -- tc qdisc show dev eth0

# Periodically report p99 latency from Prometheus
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ELAPSED=0
while [ $ELAPSED -lt "$DURATION" ]; do
  sleep 15
  ELAPSED=$((ELAPSED + 15))
  P99=$(curl -sf "${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.99,rate(http_server_requests_seconds_bucket{job=\"${SERVICE}\"}[1m]))" \
    2>/dev/null | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    r = d['data']['result']
    print(f\"{float(r[0]['value'][1])*1000:.0f}ms\" if r else 'no-data')
except: print('no-data')
" 2>/dev/null || echo "no-data")
  log "T+${ELAPSED}s | ${SERVICE} P99 latency: ${P99}"
done

log "=== LATENCY INJECTION COMPLETE ==="
# cleanup runs via trap
