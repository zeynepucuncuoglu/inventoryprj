/**
 * k6 Smoke Test — Demand Forecast Platform
 *
 * Amaç: Deploy sonrası temel sağlık kontrolü.
 * Yük seviyesi düşük, her endpoint en az bir kez test edilir.
 *
 * Çalıştırmak için:
 *   k6 run k6-smoke.js
 *   k6 run --env GATEWAY_URL=http://staging:9080 k6-smoke.js
 *
 * CI/CD'de:
 *   k6 run --out json=results.json k6-smoke.js
 */

import http from "k6/http";
import { check, sleep, group } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

// ── Özel metrikler ──────────────────────────────────────────────────────────
const errorRate   = new Rate("error_rate");
const orderTime   = new Trend("order_creation_duration", true);
const productTime = new Trend("product_list_duration", true);
const failedReqs  = new Counter("failed_requests");

// ── Konfigürasyon ───────────────────────────────────────────────────────────
const BASE_URL = __ENV.GATEWAY_URL || "http://localhost:8080";

// Test senaryosu: önce smoke (düşük yük), sonra soak (uzun süre)
export const options = {
  scenarios: {
    // Smoke: 30 saniye, 5 kullanıcı — temel doğrulama
    smoke: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      tags: { scenario: "smoke" },
    },
    // Load: 2 dakika rampa + 3 dakika tepe — normal yük
    load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "1m", target: 20 },   // yavaşça artır
        { duration: "2m", target: 20 },   // sabit tut
        { duration: "1m", target: 0 },    // yavaşça düşür
      ],
      tags: { scenario: "load" },
      startTime: "35s",  // smoke bittikten sonra başla
    },
  },
  // SLO eşikleri — bunlar başarısız olursa CI/CD durur
  thresholds: {
    "http_req_duration{scenario:smoke}":   ["p95<500", "p99<1000"],
    "http_req_duration{scenario:load}":    ["p95<500", "p99<1000"],
    "http_req_failed{scenario:smoke}":     ["rate<0.01"],  // %1'den az hata
    "http_req_failed{scenario:load}":      ["rate<0.01"],
    "error_rate":                          ["rate<0.01"],
    "order_creation_duration":             ["p95<600"],
    "product_list_duration":               ["p95<300"],
  },
};

// ── Test verisi ─────────────────────────────────────────────────────────────
// Not: Gerçek ortamda JWT token'ı auth endpoint'inden al
const AUTH_TOKEN = __ENV.JWT_TEST_TOKEN || "test-token-replace-in-ci";

const HEADERS = {
  "Content-Type":  "application/json",
  "Authorization": `Bearer ${AUTH_TOKEN}`,
};

// Her VU için farklı ürün ID'si kullan
function randomId() {
  return `product-${Math.floor(Math.random() * 1000)}`;
}

// ── Ana test akışı ──────────────────────────────────────────────────────────
export default function () {

  group("Health Checks", () => {
    // Her servis için /actuator/health kontrolü (gateway üzerinden değil, direkt)
    const health = http.get(`${BASE_URL}/actuator/health`);
    check(health, {
      "gateway health 200": (r) => r.status === 200,
      "gateway status UP":  (r) => {
        try { return JSON.parse(r.body).status === "UP"; }
        catch { return false; }
      },
    });

    errorRate.add(health.status !== 200);
    if (health.status !== 200) failedReqs.add(1);
  });

  sleep(0.5);

  group("Product API", () => {
    // Ürün listesi
    const listStart = Date.now();
    const list = http.get(`${BASE_URL}/api/v1/products`, { headers: HEADERS });
    productTime.add(Date.now() - listStart);

    const listOk = check(list, {
      "product list 200":        (r) => r.status === 200,
      "product list has body":   (r) => r.body && r.body.length > 0,
      "product list is array":   (r) => {
        try { return Array.isArray(JSON.parse(r.body)); }
        catch { return false; }
      },
    });

    errorRate.add(!listOk);
    if (!listOk) failedReqs.add(1);

    // Ürün oluştur
    const newProduct = JSON.stringify({
      name:          `Test Product ${__VU}-${__ITER}`,
      sku:           `SKU-${__VU}-${__ITER}-${Date.now()}`,
      price:         Math.floor(Math.random() * 500) + 10,
      stockQuantity: Math.floor(Math.random() * 100) + 1,
      lowStockThreshold: 5,
    });

    const create = http.post(`${BASE_URL}/api/v1/products`, newProduct, { headers: HEADERS });
    const createOk = check(create, {
      "product create 201": (r) => r.status === 201,
    });

    errorRate.add(!createOk);
    if (!createOk) failedReqs.add(1);
  });

  sleep(0.5);

  group("Order API", () => {
    // Sipariş listesi
    const list = http.get(`${BASE_URL}/api/v1/orders`, { headers: HEADERS });
    check(list, {
      "order list 200": (r) => r.status === 200,
    });

    errorRate.add(list.status !== 200);

    // Sipariş oluştur
    const orderStart = Date.now();
    const newOrder = JSON.stringify({
      customerId: `customer-${__VU}`,
      items: [
        { productId: randomId(), quantity: Math.floor(Math.random() * 5) + 1 }
      ],
    });

    const create = http.post(`${BASE_URL}/api/v1/orders`, newOrder, { headers: HEADERS });
    orderTime.add(Date.now() - orderStart);

    const createOk = check(create, {
      "order create 201 or 400": (r) => r.status === 201 || r.status === 400,
      "order not 500":           (r) => r.status < 500,
    });

    errorRate.add(!createOk);
    if (!createOk) failedReqs.add(1);
  });

  sleep(0.5);

  group("Forecast API", () => {
    const list = http.get(`${BASE_URL}/api/v1/forecasts`, { headers: HEADERS });
    check(list, {
      "forecast list not 500": (r) => r.status < 500,
    });
    errorRate.add(list.status >= 500);
  });

  sleep(1);
}

// ── Test bitti özet ─────────────────────────────────────────────────────────
export function handleSummary(data) {
  const passed  = data.metrics.http_req_failed.values.rate < 0.01;
  const p95     = data.metrics.http_req_duration.values["p(95)"];
  const p99     = data.metrics.http_req_duration.values["p(99)"];
  const total   = data.metrics.http_reqs.values.count;

  console.log("\n═══════════════════════════════════════");
  console.log("  k6 SMOKE TEST SONUÇLARI");
  console.log("═══════════════════════════════════════");
  console.log(`  Toplam istek  : ${total}`);
  console.log(`  P95 latency   : ${p95?.toFixed(0)}ms  (SLO: <500ms)`);
  console.log(`  P99 latency   : ${p99?.toFixed(0)}ms  (SLO: <1000ms)`);
  console.log(`  Hata oranı    : ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
  console.log(`  Sonuç         : ${passed ? "✅ PASSED" : "❌ FAILED"}`);
  console.log("═══════════════════════════════════════\n");

  return {
    "tests/load/results/summary.json": JSON.stringify(data, null, 2),
  };
}
