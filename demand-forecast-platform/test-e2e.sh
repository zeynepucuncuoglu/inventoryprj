#!/usr/bin/env bash
# Full end-to-end test for Demand Forecast Platform
# Usage: ./test-e2e.sh   (requires: Docker running + docker compose up --build done)

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
fail() { echo -e "${RED}✗ FAILED: $*${NC}"; exit 1; }
info() { echo -e "${BLUE}▶ $*${NC}"; }

BODY=/tmp/e2e_body

# ── Config ────────────────────────────────────────────────────────────────────
GATEWAY="http://localhost:8080"
PRODUCT_SVC="http://localhost:8081"
ORDER_SVC="http://localhost:8082"
NOTIFICATION_SVC="http://localhost:8084"
ML_SVC="http://localhost:8000"
JWT_SECRET="forecast-platform-dev-secret-key-change-in-prod"

# ── Generate JWT ──────────────────────────────────────────────────────────────
info "Generating JWT token..."
TOKEN=$(python3 -c "
import jwt, datetime
payload = {'sub': 'e2e-test-user', 'role': 'ADMIN',
           'exp': datetime.datetime.now(datetime.UTC) + datetime.timedelta(hours=1)}
print(jwt.encode(payload, '${JWT_SECRET}', algorithm='HS256'))
")
ok "Token generated"

# ── Helper ───────────────────────────────────────────────────────────────────
# do_curl <expected_status> <method> <url> [body]
# Writes response body to $BODY. Fails loudly on wrong status.
do_curl() {
  local expected=$1 method=$2 url=$3 body=${4:-}
  local args=(-s -o "$BODY" -w "%{http_code}" -X "$method" "$url"
    -H "Authorization: Bearer $TOKEN"
    -H "Content-Type: application/json")
  [[ -n "$body" ]] && args+=(-d "$body")
  local status
  status=$(curl "${args[@]}")
  if [[ "$status" != "$expected" ]]; then
    echo -e "${YELLOW}  Response: $(cat "$BODY")${NC}"
    fail "$method $url → expected HTTP $expected, got $status"
  fi
}

jq_field() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)" < "$BODY"; }
jq_len()   { python3 -c "import sys,json; print(len(json.load(sys.stdin)))" < "$BODY"; }

echo ""
echo "════════════════════════════════════════════════════════"
echo "         Demand Forecast Platform — E2E Test"
echo "════════════════════════════════════════════════════════"

# ══════════════════════════════════════════════════════════════════════════════
# 1. HEALTH CHECKS
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "1. Health checks..."
for url in "$PRODUCT_SVC/actuator/health" "$ORDER_SVC/actuator/health" \
           "http://localhost:8083/actuator/health" "$NOTIFICATION_SVC/actuator/health"; do
  s=$(curl -s -o /dev/null -w "%{http_code}" "$url")
  [[ "$s" == "200" ]] || fail "Health check failed: $url (HTTP $s)"
done
s=$(curl -s -o /dev/null -w "%{http_code}" "$ML_SVC/health")
[[ "$s" == "200" ]] || fail "ML service health check failed (HTTP $s)"
ok "All 5 services healthy"

# ══════════════════════════════════════════════════════════════════════════════
# 2. PRODUCT SERVICE
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "2. Product Service..."
SKU="E2E-$(date +%s)"

do_curl 201 POST "$GATEWAY/api/v1/products" \
  "{\"name\":\"E2E Widget\",\"sku\":\"$SKU\",\"category\":\"Electronics\",\"price\":49.99,\"initialStock\":100}"
PRODUCT_ID=$(jq_field "['id']")
STOCK=$(jq_field "['stockQuantity']")
ok "Product created: sku=$SKU stock=$STOCK"

do_curl 200 GET "$GATEWAY/api/v1/products/$PRODUCT_ID"
ok "GET /products/{id} → 200"

# Duplicate SKU must return 409
s=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/v1/products" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"name\":\"Dup\",\"sku\":\"$SKU\",\"category\":\"X\",\"price\":1,\"initialStock\":1}")
[[ "$s" == "409" ]] || fail "Duplicate SKU → expected 409, got $s"
ok "Duplicate SKU → 409"

# ══════════════════════════════════════════════════════════════════════════════
# 3. STOCK ADJUSTMENT → LOW_STOCK alert
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "3. Stock adjustment + LOW_STOCK alert..."

do_curl 200 PATCH "$GATEWAY/api/v1/products/$PRODUCT_ID/stock" \
  '{"delta":-95,"reason":"E2E_TEST"}'
NEW_STOCK=$(jq_field "['stockQuantity']")
ok "Stock adjusted to $NEW_STOCK (threshold = 10 → alert expected)"

# Insufficient stock → 422
s=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "$GATEWAY/api/v1/products/$PRODUCT_ID/stock" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"delta":-9999,"reason":"OVERFLOW"}')
[[ "$s" == "422" ]] || fail "Insufficient stock → expected 422, got $s"
ok "Insufficient stock → 422"

info "  Waiting 3s for Kafka event to propagate..."
sleep 3

do_curl 200 GET "$NOTIFICATION_SVC/api/v1/alerts?sku=$SKU"
ALERT_COUNT=$(jq_len)
[[ "$ALERT_COUNT" -ge 1 ]] || fail "Expected LOW_STOCK alert, got $ALERT_COUNT alerts"
ALERT_TYPE=$(jq_field "[0]['type']")
ALERT_SEV=$(jq_field "[0]['severity']")
ok "LOW_STOCK alert fired: type=$ALERT_TYPE severity=$ALERT_SEV"

# ══════════════════════════════════════════════════════════════════════════════
# 4. ORDER — full state machine
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "4. Order state machine (PENDING→CONFIRMED→SHIPPED→DELIVERED)..."

do_curl 201 POST "$GATEWAY/api/v1/orders" \
  "{\"customerId\":\"00000000-0000-0000-0000-000000000001\",\"items\":[{\"productId\":\"$PRODUCT_ID\",\"sku\":\"$SKU\",\"quantity\":2,\"unitPrice\":49.99}]}"
ORDER_ID=$(jq_field "['id']")
ok "Order created (PENDING): id=$ORDER_ID"

do_curl 200 PATCH "$GATEWAY/api/v1/orders/$ORDER_ID/confirm"
ok "PENDING → CONFIRMED"

do_curl 200 PATCH "$GATEWAY/api/v1/orders/$ORDER_ID/ship"
ok "CONFIRMED → SHIPPED"

do_curl 200 PATCH "$GATEWAY/api/v1/orders/$ORDER_ID/deliver"
ok "SHIPPED → DELIVERED"

# Cancel after delivery must fail
s=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  "$GATEWAY/api/v1/orders/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
[[ "$s" == "409" ]] || fail "Cancel DELIVERED → expected 409, got $s"
ok "Cancel after DELIVERED → 409"

# ══════════════════════════════════════════════════════════════════════════════
# 5. ML SERVICE — direct call
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "5. ML Inference Service..."

# Build 20 days of synthetic historical data (min 14 required)
HIST_DATA=$(python3 -c "
import json, datetime
today = datetime.date.today()
pts = [{'date': str(today - datetime.timedelta(days=20-i)), 'quantity': 10 + i % 5}
       for i in range(20)]
print(json.dumps(pts))
")

ML_BODY="{\"product_id\":\"$PRODUCT_ID\",\"sku\":\"$SKU\",\"horizon_days\":30,\"historical_data\":$HIST_DATA}"
s=$(curl -s -o "$BODY" -w "%{http_code}" -X POST "$ML_SVC/api/v1/forecast" \
  -H "Content-Type: application/json" \
  -d "$ML_BODY")
[[ "$s" == "200" ]] || fail "ML /api/v1/forecast → expected 200, got $s"
PREDICTED=$(python3 -c "import sys,json; d=json.load(sys.stdin); print(round(sum(p['predicted_quantity'] for p in d['forecast']), 1))" < "$BODY")
ok "30-day forecast: predicted demand = $PREDICTED units"

# ══════════════════════════════════════════════════════════════════════════════
# 6. FORECAST SERVICE — via gateway
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "6. Forecast Service..."
do_curl 202 POST "$GATEWAY/api/v1/forecasts" \
  "{\"productId\":\"$PRODUCT_ID\",\"sku\":\"$SKU\",\"horizonDays\":30,\"historicalData\":$HIST_DATA}"
JOB_ID=$(jq_field "['id']")
ok "Forecast job triggered: jobId=$JOB_ID"

info "  Waiting 5s for forecast + Kafka propagation..."
sleep 5

do_curl 200 GET "$NOTIFICATION_SVC/api/v1/alerts?sku=$SKU"
TOTAL_ALERTS=$(jq_len)
ok "Total alerts for $SKU after forecast: $TOTAL_ALERTS"

# ══════════════════════════════════════════════════════════════════════════════
# 7. NOTIFICATION — filters
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "7. Notification Service filters..."

do_curl 200 GET "$NOTIFICATION_SVC/api/v1/alerts?severity=CRITICAL"
ok "Filter by severity=CRITICAL → 200"

do_curl 200 GET "$NOTIFICATION_SVC/api/v1/alerts?type=LOW_STOCK"
ok "Filter by type=LOW_STOCK → 200"

# GET by ID
do_curl 200 GET "$NOTIFICATION_SVC/api/v1/alerts?sku=$SKU"
FIRST_ID=$(jq_field "[0]['id']")
do_curl 200 GET "$NOTIFICATION_SVC/api/v1/alerts/$FIRST_ID"
ok "GET /alerts/{id} → 200"

# 404 for unknown id
s=$(curl -s -o /dev/null -w "%{http_code}" \
  "$NOTIFICATION_SVC/api/v1/alerts/00000000-0000-0000-0000-000000000000")
[[ "$s" == "404" ]] || fail "Unknown alert id → expected 404, got $s"
ok "Unknown alert id → 404"

# ══════════════════════════════════════════════════════════════════════════════
# 8. AUTH — reject invalid tokens
# ══════════════════════════════════════════════════════════════════════════════
echo ""
info "8. Auth..."
s=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/v1/products")
[[ "$s" == "401" ]] || fail "No token → expected 401, got $s"
ok "No token → 401"

s=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/v1/products" \
  -H "Authorization: Bearer this.is.garbage")
[[ "$s" == "401" ]] || fail "Bad token → expected 401, got $s"
ok "Bad token → 401"

# ══════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ══════════════════════════════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════════════════════"
echo -e "${GREEN}  All tests passed! ✓${NC}"
echo "════════════════════════════════════════════════════════"
echo ""
echo "  SKU         : $SKU"
echo "  Product ID  : $PRODUCT_ID"
echo "  Order ID    : $ORDER_ID"
echo "  Total alerts: $TOTAL_ALERTS"
echo ""
echo "  Swagger UIs  →  http://localhost:808{1,2,3,4}/swagger-ui.html"
echo "  Kafka UI     →  http://localhost:8090"
echo ""
