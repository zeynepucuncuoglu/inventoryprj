# Troubleshooting Guide

## Services Won't Start

**Symptom:** `docker compose up` hangs or services exit immediately.

```bash
# Check which containers are unhealthy
docker compose ps

# View logs for a specific service
docker compose logs --follow <service-name>

# Check health of infrastructure first (databases, Kafka must be healthy before services)
docker compose ps | grep -E "(healthy|unhealthy|starting)"
```

**Common cause:** Databases take 15-30 s to become `healthy`. Services that `depends_on`
a healthy DB will wait — this is expected. If a DB stays `unhealthy`:

```bash
docker logs product-db
# Look for: "database system is ready to accept connections"
```

---

## Kafka Topics Missing

```bash
# Re-run topic creation
docker compose run --rm kafka-init

# List existing topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

---

## Forecast Service Circuit Breaker Open

The forecast service uses a circuit breaker (Resilience4j) to protect against ML service failures.
If you see `CircuitBreaker 'ml-inference' is OPEN`:

```bash
# Check ML service health
curl http://localhost:8000/health

# View forecast service logs
docker logs forecast-service --tail=100

# Force circuit breaker reset (requires actuator endpoint)
curl -X POST http://localhost:8083/actuator/circuitbreakers/ml-inference/reset
```

---

## API Gateway Returns 401

- Missing JWT token → add `Authorization: Bearer <token>` header
- Token expired → JWT default TTL is 24 h; generate a new one

```bash
# Generate a test token (requires python + PyJWT)
python3 - <<'EOF'
import jwt, datetime
payload = {
    "sub": "test-user",
    "iat": datetime.datetime.utcnow(),
    "exp": datetime.datetime.utcnow() + datetime.timedelta(hours=24)
}
token = jwt.encode(payload, "forecast-platform-dev-secret-key-change-in-prod", algorithm="HS256")
print(token)
EOF
```

---

## Prometheus Targets Show "DOWN"

1. Verify the service is running: `curl http://localhost:<port>/actuator/prometheus`
2. Check `micrometer-registry-prometheus` is in the service's `pom.xml`
3. Check `management.endpoints.web.exposure.include=prometheus` is in `application.yml`
4. Confirm the service is on the `forecast-net` Docker network: `docker inspect <container> | grep forecast-net`

---

## Grafana Dashboards Empty / No Data

- Verify Prometheus is running: http://localhost:9090/targets
- All targets must show `State: UP`
- If services have just started, wait 2-3 scrape intervals (30-45 s) for data to appear
- Check the dashboard time range — default is `Last 1 hour`; use `Last 5 minutes` while debugging

---

## Tests Failing — TestContainers

TestContainers requires a running Docker daemon.

```bash
# Verify Docker is available
docker info

# If running in CI without Docker-in-Docker, ensure the runner has Docker socket access
# For GitHub Actions: ubuntu-latest runners have Docker pre-installed
```

---

## Port Conflicts

| Service | Port | Conflict resolution |
|---------|------|---------------------|
| api-gateway | 8080 | Stop any local Tomcat/Spring Boot running on 8080 |
| kafka | 29092 | `lsof -i :29092` to find conflicting process |
| postgres (product) | 5435 | Intentionally non-standard to avoid conflicts |
| grafana | 3000 | Common — Node.js dev servers often use 3000 |

Change ports in `docker-compose.yml` host mapping (left side of `port: HOST:CONTAINER`).

---

## Full Reset (Nuclear Option)

```bash
cd demand-forecast-platform
docker compose down -v          # Stops containers AND deletes volumes (data loss!)
docker compose up --build -d    # Rebuilds images and starts fresh
```
