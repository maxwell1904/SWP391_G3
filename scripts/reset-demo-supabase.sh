#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

load_env_file() {
  local env_file="$1"
  local line name value

  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    [ -z "$line" ] && continue
    [[ "$line" == \#* || "$line" != *=* ]] && continue
    name="${line%%=*}"
    value="${line#*=}"
    [[ "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ (${value:0:1} == '"' && ${value: -1} == '"') || (${value:0:1} == "'" && ${value: -1} == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "$name=$value"
  done < "$env_file"
}

for env_file in "$ROOT_DIR/.env" "$ROOT_DIR/.env.local"; do
  [ -f "$env_file" ] && load_env_file "$env_file"
done

command -v psql >/dev/null 2>&1 || { echo "psql is required."; exit 1; }
: "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
: "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
: "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"

JDBC_URL="${SPRING_DATASOURCE_URL#jdbc:}"
if [[ "$JDBC_URL" != postgres*://*.supabase.com:*/* ]]; then
  echo "Refusing to reset a database that is not an explicit Supabase target."
  exit 1
fi

if [ "${CONFIRM_RESET:-}" != "YES" ]; then
  echo "This operation replaces all GoalZone business data."
  echo "Run with CONFIRM_RESET=YES and optionally DEMO_DATE=YYYY-MM-DD."
  exit 1
fi

if [ -z "${DEMO_DATE:-}" ]; then
  DEMO_DATE="$(date -v+1d +%F 2>/dev/null || date -d tomorrow +%F)"
fi
if [[ ! "$DEMO_DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "DEMO_DATE must use YYYY-MM-DD."
  exit 1
fi

PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$JDBC_URL?sslmode=require" \
  --username "$SPRING_DATASOURCE_USERNAME" \
  --no-password \
  --set ON_ERROR_STOP=1 \
  --set "demo_date=$DEMO_DATE" \
  --file "$ROOT_DIR/database/supabase/reset_demo.sql"

PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$JDBC_URL?sslmode=require" \
  --username "$SPRING_DATASOURCE_USERNAME" \
  --no-password \
  --set ON_ERROR_STOP=1 \
  --file "$ROOT_DIR/database/supabase/verify.sql"
