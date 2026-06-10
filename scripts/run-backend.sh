#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
MAVEN_VERSION="3.9.9"
LOCAL_MAVEN="$ROOT_DIR/.tools/apache-maven-$MAVEN_VERSION/bin/mvn"

load_env_file() {
  local env_file="$1"
  local line name value

  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    [ -z "$line" ] && continue
    [[ "$line" == \#* ]] && continue
    [[ "$line" != *=* ]] && continue

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
  if [ -f "$ENV_FILE" ]; then
    load_env_file "$ENV_FILE"
  fi
done

if command -v mvn >/dev/null 2>&1; then
  MVN_BIN="mvn"
else
  MVN_BIN="$LOCAL_MAVEN"
  if [ ! -x "$MVN_BIN" ]; then
    mkdir -p "$ROOT_DIR/.tools"
    ARCHIVE="$ROOT_DIR/.tools/apache-maven-$MAVEN_VERSION-bin.tar.gz"
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" -o "$ARCHIVE"
    tar -xzf "$ARCHIVE" -C "$ROOT_DIR/.tools"
  fi
fi

cd "$BACKEND_DIR"
exec "$MVN_BIN" spring-boot:run
