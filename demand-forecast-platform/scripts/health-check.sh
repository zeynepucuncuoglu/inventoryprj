#!/usr/bin/env bash
# Checks health of all 5 microservices, Kafka, and databases.
# Exits 0 if everything is healthy; 1 on any failure.
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASS=0
FAIL=0

log_ok()   { echo -e "${GREEN}[OK]${NC}    $1"; ((PASS++)); }
log_fail() { echo -e "${RED}[FAIL]${NC}  $1"; ((FAIL++)); }
log_info() { echo -e "${BLUE}[INFO]${NC}  $1"; }

# ─────────────────────────────────────────────────────────────
# HTTP health check
# ─────────────────────────────────────────────────────────────
check_http() {
  local name="$1" url="$2" expected_field="${3:-status}"
  local http_code body

  body=$(curl -sf --max-time 5 "$url" 2>/dev/null) && http_code=200 || http_code=$?

  if [[ "$http_code" == "200" ]] && echo "$body" | grep -q "$expected_field"; then
    log_ok "$name → $url"
  else
    log_fail "$name → $url (got: ${body:-no response})"
  fi
}

# ─────────────────────────────────────────────────────────────
# Docker container running check
# ─────────────────────────────────────────────────────────────
check_container() {
  local name="$1"
  if docker ps --filter "name=^/${name}$" --filter "status=running" --format '{{.Names}}' | grep -q "^${name}$"; then
    log_ok "Container ${name} is running"
  else
    log_fail "Container ${name} is NOT running"
  fi
}

# ─────────────────────────────────────────────────────────────
# Kafka broker availability via kafka-topics.sh inside container
# ─────────────────────────────────────────────────────────────
check_kafka() {
  if docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &>/dev/null; then
    log_ok "Kafka broker is reachable"
  else
    log_fail "Kafka broker is NOT reachable"
  fi
}

# ─────────────────────────────────────────────────────────────
# PostgreSQL connectivity via pg_isready inside each container
# ─────────────────────────────────────────────────────────────
check_postgres() {
  local container="$1" user="$2" db="$3"
  if docker exec "$container" pg_isready -U "$user" -d "$db" &>/dev/null; then
    log_ok "PostgreSQL ${container} (${db}) is ready"
  else
    log_fail "PostgreSQL ${container} (${db}) is NOT ready"
  fi
}

# ─────────────────────────────────────────────────────────────
# Redis connectivity
# ─────────────────────────────────────────────────────────────
check_redis() {
  if docker exec redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    log_ok "Redis is reachable"
  else
    log_fail "Redis is NOT reachable"
  fi
}

# ─────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   DemandForecast Platform Health Check${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo ""

log_info "Checking HTTP health endpoints..."
check_http "API Gateway"          "http://localhost:8080/actuator/health"
check_http "Product Service"      "http://localhost:8081/actuator/health"
check_http "Order Service"        "http://localhost:8082/actuator/health"
check_http "Forecast Service"     "http://localhost:8083/actuator/health"
check_http "Notification Service" "http://localhost:8084/actuator/health"
check_http "ML Inference Service" "http://localhost:8000/health" "UP"

echo ""
log_info "Checking infrastructure..."
check_kafka
check_redis
check_postgres "product-db"      "${PRODUCT_DB_USER:-product_user}"       "productdb"
check_postgres "order-db"        "${ORDER_DB_USER:-order_user}"           "orderdb"
check_postgres "forecast-db"     "${FORECAST_DB_USER:-forecast_user}"     "forecastdb"
check_postgres "notification-db" "${NOTIFICATION_DB_USER:-notification_user}" "notificationdb"

echo ""
log_info "Checking container status..."
for c in api-gateway product-service order-service forecast-service notification-service ml-inference-service kafka zookeeper redis product-db order-db forecast-db notification-db; do
  check_container "$c"
done

# ─────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo -e "  Results: ${GREEN}${PASS} passed${NC}  ${RED}${FAIL} failed${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo ""

if [[ "$FAIL" -gt 0 ]]; then
  # Notify Slack if webhook is configured
  if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
    curl -s -X POST "$SLACK_WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -d "{\"text\":\":warning: Health check FAILED: ${FAIL} service(s) unhealthy on \$(hostname)\"}" || true
  fi
  exit 1
fi

exit 0
