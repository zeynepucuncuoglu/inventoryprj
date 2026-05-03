#!/usr/bin/env bash
# Rolling deployment: pull latest images, restart containers, verify health.
# Usage: ./scripts/deployment.sh [--env-file <path>] [--skip-pull]
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="${COMPOSE_DIR}/.env"
SKIP_PULL=false
HEALTH_RETRIES=12
HEALTH_WAIT=10

log()      { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }
log_ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

# ─────────────────────────────────────────────────────────────
# Argument parsing
# ─────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --skip-pull) SKIP_PULL=true; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

notify_slack() {
  local msg="$1"
  if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
    curl -s -X POST "$SLACK_WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -d "{\"text\":\"${msg}\"}" || true
  fi
}

# ─────────────────────────────────────────────────────────────
# Wait for a single service's health endpoint
# ─────────────────────────────────────────────────────────────
wait_healthy() {
  local name="$1" url="$2"
  local attempt=0
  while [[ $attempt -lt $HEALTH_RETRIES ]]; do
    if curl -sf --max-time 5 "$url" &>/dev/null; then
      log_ok "$name is healthy"
      return 0
    fi
    ((attempt++))
    log "  Waiting for ${name}... (${attempt}/${HEALTH_RETRIES})"
    sleep "$HEALTH_WAIT"
  done
  log_fail "$name did not become healthy in time"
  return 1
}

# ─────────────────────────────────────────────────────────────
# Main deployment flow
# ─────────────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   DemandForecast Platform Deployment${NC}"
echo -e "${BLUE}   $(date)${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo ""

cd "$COMPOSE_DIR"

if [[ -f "$ENV_FILE" ]]; then
  log "Loading environment from $ENV_FILE"
  # shellcheck source=/dev/null
  set -o allexport; source "$ENV_FILE"; set +o allexport
else
  log_warn ".env file not found at $ENV_FILE — using defaults"
fi

notify_slack ":rocket: Deployment started on \$(hostname) at $(date '+%Y-%m-%d %H:%M:%S')"

# Deploy geçmişine mevcut SHA'yı kaydet (rollback için kullanılır)
CURRENT_SHA=$(git -C "$COMPOSE_DIR" rev-parse HEAD 2>/dev/null || echo "unknown")
CURRENT_MSG=$(git -C "$COMPOSE_DIR" log -1 --pretty=%s 2>/dev/null || echo "")
echo "$(date '+%Y-%m-%d %H:%M:%S') ${CURRENT_SHA} ${CURRENT_MSG}" >> "${COMPOSE_DIR}/.deploy-history"
log "Deploy kaydedildi: ${CURRENT_SHA:0:8} — ${CURRENT_MSG}"

# Step 1: Pull latest images
if [[ "$SKIP_PULL" == "false" ]]; then
  log "Pulling latest Docker images..."
  docker compose pull --quiet
  log_ok "Images pulled"
else
  log_warn "Skipping image pull (--skip-pull)"
fi

# Step 2: Stop running containers gracefully (30s timeout)
log "Stopping running containers (graceful shutdown)..."
docker compose stop --timeout 30 || true
log_ok "Containers stopped"

# Step 3: Start all services with latest images
log "Starting all services..."
docker compose up -d --remove-orphans
log_ok "Containers started"

# Step 4: Wait for all services to become healthy
echo ""
log "Waiting for services to become healthy..."
FAILED=0

declare -A SERVICE_URLS=(
  ["api-gateway"]="http://localhost:8080/actuator/health"
  ["product-service"]="http://localhost:8081/actuator/health"
  ["order-service"]="http://localhost:8082/actuator/health"
  ["forecast-service"]="http://localhost:8083/actuator/health"
  ["notification-service"]="http://localhost:8084/actuator/health"
  ["ml-inference-service"]="http://localhost:8000/health"
)

for svc in "${!SERVICE_URLS[@]}"; do
  wait_healthy "$svc" "${SERVICE_URLS[$svc]}" || ((FAILED++))
done

# Step 5: Summary
echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
if [[ $FAILED -eq 0 ]]; then
  echo -e "${GREEN}   Deployment SUCCESSFUL — all services healthy${NC}"
  notify_slack ":white_check_mark: Deployment *succeeded* on \$(hostname) — all 6 services healthy"
  echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
  exit 0
else
  echo -e "${RED}   Deployment FAILED — ${FAILED} service(s) unhealthy${NC}"
  notify_slack ":x: Deployment *FAILED* on \$(hostname) — ${FAILED} service(s) did not become healthy"
  echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
  docker compose logs --tail=50
  exit 1
fi
