#!/usr/bin/env bash
# Full end-to-end test for Demand Forecast Platform
# Usage: ./test-e2e.sh
# Requires: Docker running, docker compose up --build already done

set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
fail() { echo -e "${RED}✗ $*${NC}"; exit 1; }
info() { echo -e "${BLUE}▶ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }

# ── Config ────────────────────────────────────────────────────────────────────
GATEWAY="http://localhost:8080"
PRODUCT_SVC="http://localhost:8081"
ORDER_SVC="http://localhost:8082"
FORECAST_SVC="http://localhost:8083"
NOTIFICATION_SVC="http://localhost:8084"
ML_SVC="http://localhost:8000"

JWT_SECRET="forecast-platform-dev-secret-key-change-in-prod"

# ── Generate JWT ──────────────────────────────────────────────────────────────
info "Generating JWT token..."
TOKEN=$(python3 -c "
import jwt, datetime
payload = {'sub': 'e2e-test-user', 'role': 'ADMIN', 'exp': datetime.datetime.now(datetime.UTC) + datetime.timedelta(hours=1)}
print(jwt.encode(payload, '${JWT_SECRET}', algorithm='HS256'))
")
ok "Token generated"

AUTH="-H \"Authorization: Bearer $TOKEN\""
JSON="-H \"Content-Type: application/json\""

# ── Helper: HTTP call with assertion ─────────────────────────────────────────
# call <method> <url> [body] [expected_status]
call() {
  local method=$1 url=$2 body=${3:-} expected=${4:-200}
  local args=(-s -o /tmp/e2e_body -w "%{http_code}" -X "$method" "$url" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
  [[ -n "$body" ]] && args+=(-d "$body")
  local status
  status=$("${args[@]}")
  if [[ "$status" != "$expected" ]]; then
    warn "Response body: $(cat /tmp/e2e_body)"
    fail "$method $url → expected $expected, got $status"
  fi
  cat /tmp/e2e_body
}

echo ""
echo "════════════════════════════════════════════════════════"
echo "         Demand Forecast Platform — E2E Test"
echo "════════════════════════════════════════════════════════"

# ══════════════════════════════════════════════════════════════════════════════
# 1. HEALTH CHECKS
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "1. Health checks..."

for svc in "$PRODUCT_SVC" "$ORDER_SVC" "$FORECAST_SVC" "$NOTIFICATION_SVC"; do
  status=$(curl -s -o /dev/null -w "%{http_code}" "$svc/actuator/health")
  [[ "$status" == "200" ]] || fail "Health check failed for $svc (status $status)"
done
status=$(curl -s -o /dev/null -w "%{http_code}" "$ML_SVC/health")
[[ "$status" == "200" ]] || fail "ML service health check failed"

ok "All 5 services healthy"

# ══════════════════════════════════════════════════════════════════════════════
# 2. PRODUCT SERVICE — Create & Query
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "2. Product Service..."

SKU="E2E-$(date +%s)"

PRODUCT=$(call POST "$GATEWAY/api/v1/products" \
  "{\"name\":\"E2E Widget\",\"sku\":\"$SKU\",\"category\":\"Electronics\",\"price\":49.99,\"initialStock\":100}" \
  201)

PRODUCT_ID=$(echo "$PRODUCT" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
STOCK=$(echo "$PRODUCT" | python3 -c "import sys,json; print(json.load(sys.stdin)['stockQuantity'])")

ok "Product created: id=$PRODUCT_ID sku=$SKU stock=$STOCK"

# Get by ID
call GET "$GATEWAY/api/v1/products/$PRODUCT_ID" "" 200 > /dev/null
ok "Product GET by ID works"

# Duplicate SKU → 409
status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/v1/products" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"name\":\"Dup\",\"sku\":\"$SKU\",\"category\":\"X\",\"price\":1,\"initialStock\":1}")
[[ "$status" == "409" ]] || fail "Expected 409 for duplicate SKU, got $status"
ok "Duplicate SKU returns 409 ✓"

# ══════════════════════════════════════════════════════════════════════════════
# 3. STOCK ADJUSTMENT → triggers LOW_STOCK alert
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "3. Stock adjustment & LOW_STOCK alert..."

ADJUSTED=$(call PATCH "$GATEWAY/api/v1/products/$PRODUCT_ID/stock" \
  '{"delta":-95,"reason":"E2E_TEST"}' 200)
NEW_STOCK=$(echo "$ADJUSTED" | python3 -c "import sys,json; print(json.load(sys.stdin)['stockQuantity'])")
ok "Stock adjusted to $NEW_STOCK (below threshold of 10)"

# Insufficient stock → 422
status=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "$GATEWAY/api/v1/products/$PRODUCT_ID/stock" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"delta":-9999,"reason":"OVERFLOW"}')
[[ "$status" == "422" ]] || fail "Expected 422 for insufficient stock, got $status"
ok "Insufficient stock returns 422 ✓"

# Wait for Kafka event to propagate
info "  Waiting 3s for Kafka event to propagate..."
sleep 3

# Check alert was created
ALERTS=$(call GET "$NOTIFICATION_SVC/api/v1/alerts?sku=$SKU" "" 200)
ALERT_COUNT=$(echo "$ALERTS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
[[ "$ALERT_COUNT" -ge 1 ]] || fail "Expected at least 1 LOW_STOCK alert, got $ALERT_COUNT"
ALERT_TYPE=$(echo "$ALERTS" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['type'])")
ALERT_SEV=$(echo "$ALERTS" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['severity'])")
ok "LOW_STOCK alert created: type=$ALERT_TYPE severity=$ALERT_SEV"

# ══════════════════════════════════════════════════════════════════════════════
# 4. ORDER SERVICE — Full lifecycle
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "4. Order Service — full state machine..."

ORDER=$(call POST "$GATEWAY/api/v1/orders" \
  "{\"customerId\":\"cust-e2e\",\"items\":[{\"productId\":\"$PRODUCT_ID\",\"sku\":\"$SKU\",\"quantity\":2,\"unitPrice\":49.99}]}" \
  201)
ORDER_ID=$(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
ORDER_STATUS=$(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
ok "Order created: id=$ORDER_ID status=$ORDER_STATUS"

# PENDING → CONFIRMED
CONFIRMED=$(call PATCH "$GATEWAY/api/v1/orders/$ORDER_ID/confirm" "" 200)
ok "Order confirmed: $(echo "$CONFIRMED" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"

# CONFIRMED → SHIPPED
SHIPPED=$(call PATCH "$GATEWAY/api/v1/orders/$ORDER_ID/ship" "" 200)
ok "Order shipped: $(echo "$SHIPPED" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"

# SHIPPED → DELIVERED
DELIVERED=$(call PATCH "$GATEWAY/api/v1/orders/$ORDER_ID/deliver" "" 200)
ok "Order delivered: $(echo "$DELIVERED" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"

# Cannot cancel DELIVERED order
status=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "$GATEWAY/api/v1/orders/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $TOKEN")
[[ "$status" == "422" ]] || fail "Expected 422 cancelling delivered order, got $status"
ok "Cannot cancel DELIVERED order → 422 ✓"

# ══════════════════════════════════════════════════════════════════════════════
# 5. ML SERVICE — Direct forecast
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "5. ML Inference Service..."

ML_RESULT=$(curl -s -X POST "$ML_SVC/predict" \
  -H "Content-Type: application/json" \
  -d "{\"product_id\":\"$PRODUCT_ID\",\"sku\":\"$SKU\",\"horizon_days\":30}")
TOTAL=$(echo "$ML_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('total_predicted_demand','N/A'))" 2>/dev/null || echo "N/A")
ok "ML forecast: 30-day predicted demand = $TOTAL units"

# ══════════════════════════════════════════════════════════════════════════════
# 6. FORECAST SERVICE — Trigger via Gateway
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "6. Forecast Service..."

FORECAST=$(call POST "$GATEWAY/api/v1/forecasts" \
  "{\"productId\":\"$PRODUCT_ID\",\"sku\":\"$SKU\",\"horizonDays\":30}" \
  202)
JOB_ID=$(echo "$FORECAST" | python3 -c "import sys,json; print(json.load(sys.stdin).get('jobId','N/A'))" 2>/dev/null || echo "N/A")
ok "Forecast job triggered: jobId=$JOB_ID"

# Wait for async forecast + Kafka propagation
info "  Waiting 5s for forecast to complete and alert to propagate..."
sleep 5

# Check forecast alerts
FORECAST_ALERTS=$(call GET "$NOTIFICATION_SVC/api/v1/alerts?sku=$SKU" "" 200)
TOTAL_ALERTS=$(echo "$FORECAST_ALERTS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
ok "Total alerts for $SKU: $TOTAL_ALERTS"

# ══════════════════════════════════════════════════════════════════════════════
# 7. NOTIFICATION SERVICE — Filter & lookup
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "7. Notification Service — filters..."

# Filter by severity
CRITICAL=$(call GET "$NOTIFICATION_SVC/api/v1/alerts?severity=CRITICAL" "" 200)
CRIT_COUNT=$(echo "$CRITICAL" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
ok "CRITICAL alerts in system: $CRIT_COUNT"

# Get single alert by ID
FIRST_ALERT_ID=$(echo "$FORECAST_ALERTS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id']) if d else print('')")
if [[ -n "$FIRST_ALERT_ID" ]]; then
  call GET "$NOTIFICATION_SVC/api/v1/alerts/$FIRST_ALERT_ID" "" 200 > /dev/null
  ok "GET /alerts/{id} works ✓"
fi

# ══════════════════════════════════════════════════════════════════════════════
# 8. AUTH — Reject missing/bad token
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "8. Auth — reject invalid tokens..."

# No token
status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/v1/products")
[[ "$status" == "401" ]] || fail "Expected 401 with no token, got $status"
ok "No token → 401 ✓"

# Bad token
status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/v1/products" \
  -H "Authorization: Bearer this.is.garbage")
[[ "$status" == "401" ]] || fail "Expected 401 with bad token, got $status"
ok "Bad token → 401 ✓"

# ══════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ══════════════════════════════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════════════════════"
echo -e "${GREEN}  All tests passed!${NC}"
echo "════════════════════════════════════════════════════════"
echo ""
echo "  Product ID  : $PRODUCT_ID"
echo "  SKU         : $SKU"
echo "  Order ID    : $ORDER_ID"
echo "  Total alerts: $TOTAL_ALERTS"
echo ""
echo "  Swagger UIs:"
echo "  → http://localhost:8081/swagger-ui.html  (product)"
echo "  → http://localhost:8082/swagger-ui.html  (order)"
echo "  → http://localhost:8083/swagger-ui.html  (forecast)"
echo "  → http://localhost:8084/swagger-ui.html  (notification)"
echo "  → http://localhost:8090                  (kafka ui)"
echo ""
