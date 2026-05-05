#!/usr/bin/env bash
# Manually pause a Kafka consumer group to simulate consumer lag buildup.
# Usage: ./inject-kafka-lag.sh [service] [duration_seconds]
# Example: ./inject-kafka-lag.sh order-service 120

set -euo pipefail

SERVICE="${1:-order-service}"
DURATION="${2:-60}"
COMPOSE_FILE="$(dirname "$0")/../../docker-compose.yml"
KAFKA_CONTAINER=$(docker ps -qf name=kafka --filter status=running | head -1)

log() { echo "[$(date '+%Y-%m-%dT%H:%M:%S')] [CHAOS/kafka-lag] $*"; }

if [ -z "$KAFKA_CONTAINER" ]; then
  log "ERROR: No running Kafka container found"
  exit 1
fi

log "=== KAFKA LAG INJECTION START ==="
log "Target service : $SERVICE"
log "Duration       : ${DURATION}s"

# 1. Capture baseline lag
log "Baseline consumer group offsets:"
docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --all-groups 2>/dev/null | grep -v "^$" || true

# 2. Pause the target service container
log "Pausing container: $SERVICE"
docker compose -f "$COMPOSE_FILE" pause "$SERVICE"

# 3. Produce 500 test messages to drive lag up
log "Producing 500 messages to order.events..."
for i in $(seq 1 500); do
  printf '{"orderId":"chaos-%04d","productId":"test","quantity":1,"status":"PENDING"}\n' "$i"
done | docker exec -i "$KAFKA_CONTAINER" \
  kafka-console-producer --bootstrap-server localhost:9092 --topic order.events
log "500 messages produced."

# 4. Poll lag every 10s for the duration
ELAPSED=0
while [ $ELAPSED -lt "$DURATION" ]; do
  sleep 10
  ELAPSED=$((ELAPSED + 10))
  LAG=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --describe --group "${SERVICE}-group" 2>/dev/null | \
    awk 'NR>1 && /[0-9]/ {sum+=$6} END {print sum+0}')
  log "T+${ELAPSED}s | Consumer lag on ${SERVICE}-group: ${LAG} messages"
done

# 5. Resume the service
log "Resuming container: $SERVICE"
docker compose -f "$COMPOSE_FILE" unpause "$SERVICE"

# 6. Wait for drain and report final lag
sleep 15
FINAL_LAG=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group "${SERVICE}-group" 2>/dev/null | \
  awk 'NR>1 && /[0-9]/ {sum+=$6} END {print sum+0}')
log "Final lag after resume: ${FINAL_LAG} messages"
log "=== KAFKA LAG INJECTION COMPLETE ==="
