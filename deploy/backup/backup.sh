#!/usr/bin/env bash
# Daily MySQL backup loop (runs forever inside the db-backup container).
#
# Env (the DB_* names are the same ones the app uses, so one .env file serves both):
#   DB_HOST (default db)  DB_PORT (3306)  DB_NAME (baddrop_db)  DB_USERNAME (root)  DB_PASSWORD (required)
#   BACKUP_HOUR      hour of day, container time (UTC), when the daily dump runs      (default 3)
#   BACKUP_KEEP_DAYS how many days of dumps to keep                                   (default 14)
#   BACKUP_DIR       where dumps are written                                          (default /backups)
#   BACKUP_RUN_ON_START  "true" = also take a dump right when the container starts    (default true)
#   ADMIN_NOTIFY_BOT_TOKEN / ADMIN_NOTIFY_CHAT_ID  optional: Telegram message on success/failure
#   BACKUP_SEND_TELEGRAM  "true" = also send the dump file itself to that chat (it contains player data!) (default false)
set -u

DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-baddrop_db}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_HOUR="${BACKUP_HOUR:-3}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_RUN_ON_START="${BACKUP_RUN_ON_START:-true}"
BACKUP_SEND_TELEGRAM="${BACKUP_SEND_TELEGRAM:-true}"
NOTIFY_TOKEN="${ADMIN_NOTIFY_BOT_TOKEN:-}"
NOTIFY_CHAT="${ADMIN_NOTIFY_CHAT_ID:-}"

log() { echo "[backup $(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }

notify() {
  [ -n "$NOTIFY_TOKEN" ] && [ -n "$NOTIFY_CHAT" ] || return 0
  curl -s -m 20 -o /dev/null "https://api.telegram.org/bot${NOTIFY_TOKEN}/sendMessage" \
    --data-urlencode "chat_id=${NOTIFY_CHAT}" --data-urlencode "text=$1" || true
}

send_file() {
  [ "$BACKUP_SEND_TELEGRAM" = "true" ] && [ -n "$NOTIFY_TOKEN" ] && [ -n "$NOTIFY_CHAT" ] || return 0
  curl -s -m 120 -o /dev/null "https://api.telegram.org/bot${NOTIFY_TOKEN}/sendDocument" \
    -F "chat_id=${NOTIFY_CHAT}" -F "document=@$1" || true
}

do_backup() {
  mkdir -p "$BACKUP_DIR"
  local stamp file tmp
  stamp="$(date -u +%Y-%m-%d_%H%M%S)"
  file="${BACKUP_DIR}/${DB_NAME}_${stamp}.sql.gz"
  tmp="${file}.part"

  # The password goes through the environment, never on the command line (it would show up in `ps`).
  if MYSQL_PWD="$DB_PASSWORD" mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" \
        --single-transaction --quick --routines --triggers --no-tablespaces --set-gtid-purged=OFF \
        "$DB_NAME" 2>"${tmp}.err" | gzip -9 > "$tmp" \
     && [ "${PIPESTATUS[0]}" -eq 0 ] && gzip -t "$tmp" && [ "$(stat -c %s "$tmp")" -gt 200 ]; then
    mv "$tmp" "$file"
    rm -f "${tmp}.err"
    local size; size="$(du -h "$file" | cut -f1)"
    log "OK ${file} (${size})"
    # retention: drop dumps older than BACKUP_KEEP_DAYS days, but never the newest one
    find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$BACKUP_KEEP_DAYS" -delete
    notify "✅ BADDROP DB backup OK: $(basename "$file") (${size})"
    send_file "$file"
    return 0
  fi
  log "FAILED: $(head -c 300 "${tmp}.err" 2>/dev/null | tr '\n' ' ')"
  rm -f "$tmp" "${tmp}.err"
  notify "❌ BADDROP DB backup FAILED. Check the db-backup container logs."
  return 1
}

seconds_until_next_run() {
  local now target
  now="$(date -u +%s)"
  target="$(date -u -d "today ${BACKUP_HOUR}:00:00" +%s)"
  [ "$target" -le "$now" ] && target=$((target + 86400))
  echo $((target - now))
}

# `source`-able for tests: BACKUP_TEST_ONCE=true runs exactly one dump and exits with its status.
if [ "${BACKUP_TEST_ONCE:-false}" = "true" ]; then do_backup; exit $?; fi

log "starting: db=${DB_HOST}:${DB_PORT}/${DB_NAME} daily at ${BACKUP_HOUR}:00 UTC, keep ${BACKUP_KEEP_DAYS} days -> ${BACKUP_DIR}"
if [ "$BACKUP_RUN_ON_START" = "true" ]; then
  sleep 20 # let the database finish starting after a joint `docker compose up`
  do_backup || true
fi
while true; do
  wait_s="$(seconds_until_next_run)"
  log "next backup in ${wait_s}s"
  sleep "$wait_s"
  do_backup || true
done
