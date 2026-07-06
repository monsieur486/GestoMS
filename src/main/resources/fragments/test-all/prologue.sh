#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8089}
GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}

WAIT_TIMEOUT=${WAIT_TIMEOUT:-180}
WAIT_INTERVAL=${WAIT_INTERVAL:-3}

wait_for(){
  local label=$1; shift
  local deadline=$(( SECONDS + WAIT_TIMEOUT ))
  printf 'Waiting for %s ' "$label"
  until "$@" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      printf '\nFAIL %s not ready after %ss\n' "$label" "$WAIT_TIMEOUT"; exit 1
    fi
    printf '.'; sleep "$WAIT_INTERVAL"
  done
  printf ' OK\n'
}

auth_ready(){
  local t
  t=$(curl -s -X POST "$GATEWAY_URL/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"test-admin","password":"admin123"}' | jq -r '.access_token // empty')
  [ -n "$t" ]
}

routed_up(){
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY_URL/$1" || true)
  [[ "$code" =~ ^(200|401|403)$ ]]
}

auth_login(){
  curl -s -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}"
}

auth_refresh(){
  curl -s -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"opaque_refresh_token\":\"$1\"}"
}

check_token(){
  if [ -z "${!1}" ]; then
    echo "Unable to get $2 token"
    exit 1
  fi
}

assert_http(){
  local label=$1
  local expected=$2
  local method=$3
  local token=$4
  local url=$5

  local response_file
  response_file=$(mktemp)

  local status
  status=$(curl -s -o "$response_file" -w "%{http_code}" \
    -X "$method" \
    -H "Authorization: Bearer $token" \
    "$url")

  if [ "$status" = "$expected" ]; then
    echo "OK $label -> $status"
  else
    echo "FAIL $label expected $expected got $status"
    cat "$response_file"
    rm -f "$response_file"
    exit 1
  fi

  rm -f "$response_file"
}

assert_contains(){
  local label=$1
  local haystack=$2
  local needle=$3

  if echo "$haystack" | grep -q "$needle"; then
    echo "OK $label contains $needle"
  else
    echo "FAIL $label missing $needle"
    echo "$haystack"
    exit 1
  fi
}

