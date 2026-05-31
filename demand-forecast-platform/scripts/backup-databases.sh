#!/usr/bin/env bash
# PostgreSQL backup script — tüm 4 veritabanı
# Çalıştırma: ./backup-databases.sh
# Cron (her gün 02:00): 0 2 * * * /opt/demand-forecast/scripts/backup-databases.sh

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$(dirname "$0")/../backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
DATE=$(date +%Y-%m-%d_%H-%M-%S)

mkdir -p "$BACKUP_DIR"

log() { echo "[$(date '+%Y-%m-%dT%H:%M:%S')] [BACKUP] $*"; }

log "=== BACKUP BAŞLADI: $DATE ==="

# Her DB için bağlantı bilgileri
declare -A DB_CONFIG=(
  ["product-db"]="5435:${PRODUCT_DB_USER:-product_user}:${PRODUCT_DB_PASSWORD:-product_pass}:productdb"
  ["order-db"]="5433:${ORDER_DB_USER:-order_user}:${ORDER_DB_PASSWORD:-order_pass}:orderdb"
  ["forecast-db"]="5434:${FORECAST_DB_USER:-forecast_user}:${FORECAST_DB_PASSWORD:-forecast_pass}:forecastdb"
  ["notification-db"]="5436:${NOTIFICATION_DB_USER:-notification_user}:${NOTIFICATION_DB_PASSWORD:-notification_pass}:notificationdb"
)

FAILED=0
SUCCESS=0

for CONTAINER in "${!DB_CONFIG[@]}"; do
  IFS=':' read -r PORT USER PASS DBNAME <<< "${DB_CONFIG[$CONTAINER]}"
  BACKUP_FILE="${BACKUP_DIR}/${CONTAINER}_${DATE}.sql.gz"

  log "Yedekleniyor: $DBNAME (port $PORT) → $BACKUP_FILE"

  # Container içinden pg_dump al, sıkıştır
  if PGPASSWORD="$PASS" pg_dump \
      -h localhost -p "$PORT" \
      -U "$USER" "$DBNAME" \
      --no-password \
      --verbose \
      --format=plain \
      2>/dev/null | gzip > "$BACKUP_FILE"; then

    SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
    log "✅ $DBNAME tamamlandı — $SIZE"
    SUCCESS=$((SUCCESS + 1))
  else
    log "❌ $DBNAME BAŞARISIZ"
    rm -f "$BACKUP_FILE"
    FAILED=$((FAILED + 1))
  fi
done

# Eski yedekleri sil (RETENTION_DAYS günden eski)
log "Eski yedekler temizleniyor (>${RETENTION_DAYS} gün)..."
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +"$RETENTION_DAYS" -delete
REMAINING=$(find "$BACKUP_DIR" -name "*.sql.gz" | wc -l)

log "=== BACKUP TAMAMLANDI ==="
log "Başarılı: $SUCCESS | Başarısız: $FAILED | Toplam yedek: $REMAINING"

# Slack bildirimi (opsiyonel)
if [ -n "${SLACK_WEBHOOK_PLATFORM:-}" ] && [ "$FAILED" -gt 0 ]; then
  curl -sf -X POST "$SLACK_WEBHOOK_PLATFORM" \
    -H 'Content-Type: application/json' \
    -d "{\"text\": \"⚠️ DB Backup: $FAILED veritabanı yedeklenemedi! Sunucu: $(hostname)\"}" || true
fi

[ "$FAILED" -eq 0 ]
