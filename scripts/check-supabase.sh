#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for ENV_FILE in "$ROOT_DIR/.env" "$ROOT_DIR/.env.local"; do
  if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck source=/dev/null
    source "$ENV_FILE"
    set +a
  fi
done

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required to check Supabase connectivity."
  exit 1
fi

: "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
: "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
: "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"

JDBC_URL="${SPRING_DATASOURCE_URL#jdbc:}"
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$JDBC_URL?sslmode=require" \
  --username "$SPRING_DATASOURCE_USERNAME" \
  --no-password \
  --command "select current_database() as database_name, current_user as database_user, current_setting('server_version') as postgres_version;"
