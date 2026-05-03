#!/usr/bin/env bash
# Önceki bir deploy'a geri döner.
# Her deployment.sh çalışmasında .deploy-history dosyasına SHA kaydedilir.
# Bu script o geçmişten seçim yaparak rebuild eder.
#
# Kullanım:
#   ./scripts/rollback.sh                  → Son deploy'dan öncekine döner
#   ./scripts/rollback.sh <git-sha>        → Belirli bir commit'e döner
#   ./scripts/rollback.sh --list           → Deploy geçmişini listeler
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(dirname "$SCRIPT_DIR")"
HISTORY_FILE="${COMPOSE_DIR}/.deploy-history"
STAGING=false
TARGET_SHA=""

log()      { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }
log_ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

notify_slack() {
  if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
    curl -s -X POST "$SLACK_WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -d "{\"text\":\"$1\"}" || true
  fi
}

# ─────────────────────────────────────────────────────────────
# Argüman ayrıştırma
# ─────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --list)
      if [[ ! -f "$HISTORY_FILE" ]]; then
        echo "Henüz deploy geçmişi yok."
        exit 0
      fi
      echo ""
      echo -e "${BLUE}Deploy Geçmişi (en yeni üstte):${NC}"
      echo "────────────────────────────────────────────────────────"
      tac "$HISTORY_FILE" | head -20 | nl -ba
      echo ""
      exit 0
      ;;
    --staging) STAGING=true; shift ;;
    --*) echo "Bilinmeyen seçenek: $1"; exit 1 ;;
    *) TARGET_SHA="$1"; shift ;;
  esac
done

# ─────────────────────────────────────────────────────────────
# Rollback SHA'yı belirle
# ─────────────────────────────────────────────────────────────
cd "$COMPOSE_DIR"

if [[ -z "$TARGET_SHA" ]]; then
  if [[ ! -f "$HISTORY_FILE" ]] || [[ $(wc -l < "$HISTORY_FILE") -lt 2 ]]; then
    log_fail "Rollback yapılacak önceki deploy bulunamadı. En az 2 deploy gerekli."
  fi
  # Son satır = şu anki deploy, ondan önceki = rollback hedefi
  TARGET_SHA=$(tail -2 "$HISTORY_FILE" | head -1 | awk '{print $2}')
  TARGET_MSG=$(tail -2 "$HISTORY_FILE" | head -1 | cut -d' ' -f3-)
  log_warn "Rollback hedefi: ${TARGET_SHA:0:8} — ${TARGET_MSG}"
else
  # Verilen SHA'nın geçerli olduğunu doğrula
  if ! git cat-file -e "${TARGET_SHA}^{commit}" 2>/dev/null; then
    log_fail "Geçersiz commit SHA: $TARGET_SHA"
  fi
  TARGET_MSG=$(git log -1 --pretty=%s "$TARGET_SHA")
  log_warn "Rollback hedefi: ${TARGET_SHA:0:8} — ${TARGET_MSG}"
fi

# ─────────────────────────────────────────────────────────────
# Onay al
# ─────────────────────────────────────────────────────────────
echo ""
echo -e "${RED}════════════════════════════════════════════════════════${NC}"
echo -e "${RED}   ROLLBACK — Bu işlem geri alınamaz${NC}"
echo -e "${RED}   Hedef: ${TARGET_SHA:0:8} — ${TARGET_MSG}${NC}"
if [[ "$STAGING" == "true" ]]; then
  echo -e "${YELLOW}   Ortam: STAGING${NC}"
else
  echo -e "${RED}   Ortam: PRODUCTION${NC}"
fi
echo -e "${RED}════════════════════════════════════════════════════════${NC}"
echo ""
read -rp "Devam etmek istiyor musun? (evet/hayır): " confirm
if [[ "$confirm" != "evet" ]]; then
  echo "Rollback iptal edildi."
  exit 0
fi

notify_slack ":warning: *Rollback başlatıldı* — hedef: \`${TARGET_SHA:0:8}\` — ${TARGET_MSG}"

# ─────────────────────────────────────────────────────────────
# Rollback adımları
# ─────────────────────────────────────────────────────────────
CURRENT_SHA=$(git rev-parse HEAD)

log "Mevcut branch/SHA kaydediliyor: ${CURRENT_SHA:0:8}"
log "Hedef commit'e geçiliyor: ${TARGET_SHA:0:8}"
git checkout "$TARGET_SHA" -- .

log "Çalışan servisler durduruluyor..."
if [[ "$STAGING" == "true" ]]; then
  docker compose -f docker-compose.yml -f docker-compose.staging.yml stop --timeout 30 || true
else
  docker compose stop --timeout 30 || true
fi

log "Önceki versiyondan yeniden build ediliyor..."
if [[ "$STAGING" == "true" ]]; then
  docker compose -f docker-compose.yml -f docker-compose.staging.yml up -d --build --remove-orphans
else
  docker compose up -d --build --remove-orphans
fi

log "Servisler sağlıklı olana kadar bekleniyor..."
sleep 20

if bash "$SCRIPT_DIR/health-check.sh"; then
  echo ""
  log_ok "Rollback başarılı — ${TARGET_SHA:0:8} versiyonu çalışıyor"
  notify_slack ":white_check_mark: *Rollback tamamlandı* — \`${TARGET_SHA:0:8}\` aktif"

  # Rollback'i de geçmişe kaydet
  echo "$(date '+%Y-%m-%d %H:%M:%S') ${TARGET_SHA} [ROLLBACK] ${TARGET_MSG}" >> "$HISTORY_FILE"
else
  log_fail "Rollback sonrası servisler sağlıklı değil! Manuel müdahale gerekebilir."
  notify_slack ":x: *Rollback BAŞARISIZ* — manuel müdahale gerekiyor"
fi
