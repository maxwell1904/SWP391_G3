#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

load_env_file() {
  local env_file="$1" line name value
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

for command_name in curl jq psql; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "$command_name is required."
    exit 1
  }
done

: "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
: "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
: "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"
: "${PAYPAL_CLIENT_ID:?PAYPAL_CLIENT_ID is required}"
: "${PAYPAL_CLIENT_SECRET:?PAYPAL_CLIENT_SECRET is required}"

paypal_base="https://api-m.sandbox.paypal.com"
[ "${PAYPAL_ENV:-sandbox}" = "live" ] && paypal_base="https://api-m.paypal.com"
access_token="$(
  curl -fsS -u "$PAYPAL_CLIENT_ID:$PAYPAL_CLIENT_SECRET" \
    -d "grant_type=client_credentials" \
    "$paypal_base/v1/oauth2/token" | jq -er '.access_token'
)"

jdbc_url="${SPRING_DATASOURCE_URL#jdbc:}?sslmode=require"
export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"

capture_ids="$(
  psql "$jdbc_url" --username "$SPRING_DATASOURCE_USERNAME" --no-password --tuples-only --no-align \
    --command "select provider_capture_id
               from payment
               where payment_method = 'paypal_sandbox'
                 and status in ('paid', 'partially_refunded', 'refunded')
                 and provider_capture_id is not null
                 and (provider_fee_amount is null or provider_net_amount is null)
               order by payment_id"
)"

reconciled=0
while IFS= read -r capture_id; do
  [ -z "$capture_id" ] && continue
  capture="$(
    curl -fsS -H "Authorization: Bearer $access_token" \
      "$paypal_base/v2/payments/captures/$capture_id"
  )"
  amount="$(jq -er '.amount.value' <<<"$capture")"
  currency="$(jq -er '.amount.currency_code' <<<"$capture")"
  fee="$(jq -er '.seller_receivable_breakdown.paypal_fee.value' <<<"$capture")"
  net="$(jq -er '.seller_receivable_breakdown.net_amount.value' <<<"$capture")"

  updated="$(
    printf '%s\n' \
      "update payment" \
      "set provider_fee_amount = :'fee'::numeric(12,2)," \
      "    provider_net_amount = :'net'::numeric(12,2)" \
      "where provider_capture_id = :'capture_id'" \
      "  and amount = :'amount'::numeric(12,2)" \
      "  and currency = :'currency'" \
      "  and (provider_fee_amount is null or provider_net_amount is null)" \
      "returning payment_id;" |
      psql "$jdbc_url" --username "$SPRING_DATASOURCE_USERNAME" --no-password \
        --tuples-only --no-align \
        --set capture_id="$capture_id" \
        --set amount="$amount" \
        --set currency="$currency" \
        --set fee="$fee" \
        --set net="$net"
  )"
  [ -n "$updated" ] && reconciled=$((reconciled + 1))
done <<<"$capture_ids"

echo "Reconciled PayPal fee breakdowns: $reconciled"
