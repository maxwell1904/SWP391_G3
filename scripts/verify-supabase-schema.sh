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

for ENV_FILE in "$ROOT_DIR/.env" "$ROOT_DIR/.env.local"; do
  [ -f "$ENV_FILE" ] && load_env_file "$ENV_FILE"
done

command -v psql >/dev/null 2>&1 || { echo "psql is required to verify Supabase schema."; exit 1; }
: "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
: "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
: "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"

JDBC_URL="${SPRING_DATASOURCE_URL#jdbc:}"
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$JDBC_URL?sslmode=require" \
  --username "$SPRING_DATASOURCE_USERNAME" \
  --no-password \
  --set ON_ERROR_STOP=1 \
  --file "$ROOT_DIR/database/supabase/verify.sql"
