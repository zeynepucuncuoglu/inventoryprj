#!/usr/bin/env bash
# Parses Docker container logs for ERROR/EXCEPTION patterns and generates
# an incident report written to logs/incident-<timestamp>.txt
# Usage: ./scripts/log-analyzer.sh [--since <duration>] [--service <name>]
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "$SCRIPT_DIR")"
REPORT_DIR="${COMPOSE_DIR}/logs"
SINCE="1h"
TARGET_SERVICE=""
TIMESTAMP="$(date '+%Y%m%d_%H%M%S')"
REPORT_FILE="${REPORT_DIR}/incident-${TIMESTAMP}.txt"

# ─────────────────────────────────────────────────────────────
# Argument parsing
# ─────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --since)   SINCE="$2";          shift 2 ;;
    --service) TARGET_SERVICE="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

mkdir -p "$REPORT_DIR"

ALL_SERVICES=(
  api-gateway
  product-service
  order-service
  forecast-service
  notification-service
  ml-inference-service
)

if [[ -n "$TARGET_SERVICE" ]]; then
  SERVICES=("$TARGET_SERVICE")
else
  SERVICES=("${ALL_SERVICES[@]}")
fi

TOTAL_ERRORS=0
TOTAL_EXCEPTIONS=0
TOTAL_WARNINGS=0

# ─────────────────────────────────────────────────────────────
# Report header
# ─────────────────────────────────────────────────────────────
{
  echo "═══════════════════════════════════════════════════════"
  echo "  DemandForecast Platform — Incident Report"
  echo "  Generated: $(date)"
  echo "  Log window: last ${SINCE}"
  echo "  Services:   ${SERVICES[*]}"
  echo "═══════════════════════════════════════════════════════"
  echo ""
} > "$REPORT_FILE"

# ─────────────────────────────────────────────────────────────
# Per-service log analysis
# ─────────────────────────────────────────────────────────────
analyze_service() {
  local svc="$1"
  local logs errors exceptions warnings kafka_errors

  # Fetch logs; skip if container doesn't exist
  if ! docker ps -a --format '{{.Names}}' | grep -q "^${svc}$"; then
    echo -e "${YELLOW}[SKIP]${NC}  ${svc} — container not found"
    echo "  [SKIPPED] Container not running" >> "$REPORT_FILE"
    return
  fi

  logs=$(docker logs "$svc" --since "$SINCE" 2>&1 || true)

  errors=$(echo "$logs"      | grep -ciE '(^|\s)(ERROR|SEVERE)(\s|$)' || true)
  exceptions=$(echo "$logs"  | grep -ci 'Exception'                    || true)
  warnings=$(echo "$logs"    | grep -ciE '(^|\s)WARN(\s|$)'           || true)
  kafka_errors=$(echo "$logs"| grep -ci 'KafkaException\|offset.*error\|CommitFailedException' || true)

  TOTAL_ERRORS=$((TOTAL_ERRORS + errors))
  TOTAL_EXCEPTIONS=$((TOTAL_EXCEPTIONS + exceptions))
  TOTAL_WARNINGS=$((TOTAL_WARNINGS + warnings))

  # Terminal output
  if [[ $errors -gt 0 ]] || [[ $exceptions -gt 0 ]]; then
    echo -e "${RED}[ISSUES]${NC} ${svc}: ${errors} errors, ${exceptions} exceptions, ${warnings} warnings"
  else
    echo -e "${GREEN}[OK]${NC}     ${svc}: 0 errors  (${warnings} warnings)"
  fi

  # Report section
  {
    echo "───────────────────────────────────────────────────────"
    echo "  Service: ${svc}"
    echo "  Errors: ${errors}  |  Exceptions: ${exceptions}  |  Warnings: ${warnings}  |  Kafka errors: ${kafka_errors}"
    echo ""

    if [[ $errors -gt 0 ]]; then
      echo "  ERROR lines:"
      echo "$logs" | grep -E '(^|\s)(ERROR|SEVERE)(\s|$)' | head -20 | sed 's/^/    /'
      echo ""
    fi

    if [[ $exceptions -gt 0 ]]; then
      echo "  EXCEPTION lines (first 10 stack frames):"
      echo "$logs" | grep -A 10 'Exception' | head -50 | sed 's/^/    /'
      echo ""
    fi

    if [[ $kafka_errors -gt 0 ]]; then
      echo "  KAFKA ERROR lines:"
      echo "$logs" | grep -iE 'KafkaException|offset.*error|CommitFailedException' | head -10 | sed 's/^/    /'
      echo ""
    fi
  } >> "$REPORT_FILE"
}

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   DemandForecast Log Analyzer (last ${SINCE})${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo ""

for svc in "${SERVICES[@]}"; do
  analyze_service "$svc"
done

# ─────────────────────────────────────────────────────────────
# Report footer / summary
# ─────────────────────────────────────────────────────────────
{
  echo "═══════════════════════════════════════════════════════"
  echo "  SUMMARY"
  echo "  Total errors:     ${TOTAL_ERRORS}"
  echo "  Total exceptions: ${TOTAL_EXCEPTIONS}"
  echo "  Total warnings:   ${TOTAL_WARNINGS}"
  echo ""
  if [[ $TOTAL_ERRORS -gt 0 ]] || [[ $TOTAL_EXCEPTIONS -gt 0 ]]; then
    echo "  Status: ACTION REQUIRED"
  else
    echo "  Status: HEALTHY — no critical issues detected"
  fi
  echo "═══════════════════════════════════════════════════════"
} >> "$REPORT_FILE"

# Terminal summary
echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo -e "  Total: ${RED}${TOTAL_ERRORS} errors${NC}, ${RED}${TOTAL_EXCEPTIONS} exceptions${NC}, ${YELLOW}${TOTAL_WARNINGS} warnings${NC}"
echo -e "  Report saved: ${REPORT_FILE}"
echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
echo ""

# Slack alert if errors found
if [[ $TOTAL_ERRORS -gt 0 ]] || [[ $TOTAL_EXCEPTIONS -gt 0 ]]; then
  if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
    curl -s -X POST "$SLACK_WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -d "{\"text\":\":rotating_light: Log analysis found *${TOTAL_ERRORS} errors* and *${TOTAL_EXCEPTIONS} exceptions* in the last ${SINCE}. Check \`${REPORT_FILE}\`\"}" || true
  fi
  exit 1
fi

exit 0
