#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8089}
GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}

# Full stack startup takes ~60s; poll until everything actually serves so this script
# can be launched right after ./prod-start.sh. Override on a slow host with
# WAIT_TIMEOUT / WAIT_INTERVAL (seconds).
WAIT_TIMEOUT=${WAIT_TIMEOUT:-180}
WAIT_INTERVAL=${WAIT_INTERVAL:-3}

wait_for(){
  # $1=label, $2...=predicate command (exit 0 = ready). Polls until ready or WAIT_TIMEOUT.
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
  # ms-auth + Keycloak realm: a real login returning an access_token.
  local t
  t=$(curl -s -X POST "$GATEWAY_URL/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"test-admin","password":"admin123"}' | jq -r '.access_token // empty')
  [ -n "$t" ]
}

routed_up(){
  # A gateway-routed endpoint that answers (200/401/403) is up and registered in Eureka.
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

echo 'Waiting for the full stack to be ready (~60s on first start)...'
wait_for 'ms-eureka'              curl -fs http://localhost:8761
wait_for 'ms-auth + keycloak'    auth_ready
wait_for 'service-a'             routed_up service-a/api/resources-a
wait_for 'service-b'             routed_up service-b/api/resources-b
wait_for 'service-c'             routed_up service-c/api/resources-c
wait_for 'service-consumer'      routed_up service-consumer/api/aggregate
wait_for 'ms-admin'              curl -fs http://localhost:9100
echo 'Stack is ready.'
echo

echo 'Getting tokens via ms-auth...'
ADMIN_LOGIN=$(auth_login test-admin admin123)
TOKEN_ADMIN=$(echo "$ADMIN_LOGIN" | jq -r '.access_token // empty')
OPAQUE_ADMIN=$(echo "$ADMIN_LOGIN" | jq -r '.opaque_refresh_token // empty')

BATCH_LOGIN=$(auth_login test-batch user123)
TOKEN_BATCH=$(echo "$BATCH_LOGIN" | jq -r '.access_token // empty')

SERVICE_A_LOGIN=$(auth_login test-service-a user123)
TOKEN_SERVICE_A=$(echo "$SERVICE_A_LOGIN" | jq -r '.access_token // empty')

SERVICE_B_LOGIN=$(auth_login test-service-b user123)
TOKEN_SERVICE_B=$(echo "$SERVICE_B_LOGIN" | jq -r '.access_token // empty')

SERVICE_C_LOGIN=$(auth_login test-service-c user123)
TOKEN_SERVICE_C=$(echo "$SERVICE_C_LOGIN" | jq -r '.access_token // empty')

check_token TOKEN_ADMIN ADMIN
check_token TOKEN_BATCH BATCH
check_token TOKEN_SERVICE_A SERVICE_A
check_token TOKEN_SERVICE_B SERVICE_B
check_token TOKEN_SERVICE_C SERVICE_C

cat > tokens.env <<TEOF
TOKEN_ADMIN=${TOKEN_ADMIN}
TOKEN_BATCH=${TOKEN_BATCH}
TOKEN_SERVICE_A=${TOKEN_SERVICE_A}
TOKEN_SERVICE_B=${TOKEN_SERVICE_B}
TOKEN_SERVICE_C=${TOKEN_SERVICE_C}
TEOF
chmod 600 tokens.env

echo 'Testing resource role matrix...'
assert_http 'ADMIN can access service-a' 200 GET "$TOKEN_ADMIN" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-a user can access own resource' 200 GET "$TOKEN_SERVICE_A" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-a user cannot access service-b' 403 GET "$TOKEN_SERVICE_A" "$GATEWAY_URL/service-b/api/resources-b"
assert_http 'service-a user cannot access service-c' 403 GET "$TOKEN_SERVICE_A" "$GATEWAY_URL/service-c/api/resources-c"

assert_http 'ADMIN can access service-b' 200 GET "$TOKEN_ADMIN" "$GATEWAY_URL/service-b/api/resources-b"
assert_http 'service-b user can access own resource' 200 GET "$TOKEN_SERVICE_B" "$GATEWAY_URL/service-b/api/resources-b"
assert_http 'service-b user cannot access service-a' 403 GET "$TOKEN_SERVICE_B" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-b user cannot access service-c' 403 GET "$TOKEN_SERVICE_B" "$GATEWAY_URL/service-c/api/resources-c"

assert_http 'ADMIN can access service-c' 200 GET "$TOKEN_ADMIN" "$GATEWAY_URL/service-c/api/resources-c"
assert_http 'service-c user can access own resource' 200 GET "$TOKEN_SERVICE_C" "$GATEWAY_URL/service-c/api/resources-c"
assert_http 'service-c user cannot access service-a' 403 GET "$TOKEN_SERVICE_C" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-c user cannot access service-b' 403 GET "$TOKEN_SERVICE_C" "$GATEWAY_URL/service-b/api/resources-b"

echo 'Testing infrastructure...'
curl -fs http://localhost:8761 >/dev/null && echo 'Eureka OK'
curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'

echo 'Testing service-consumer aggregation...'
AGG_RESPONSE=$(curl -s \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  "$GATEWAY_URL/service-consumer/api/aggregate")

AGG_STATUS=$(curl -s -o /tmp/aggregate-response.txt -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  "$GATEWAY_URL/service-consumer/api/aggregate")

if [ "$AGG_STATUS" != "200" ]; then
  echo "FAIL ADMIN aggregate expected 200 got $AGG_STATUS"
  cat /tmp/aggregate-response.txt
  exit 1
fi

echo 'OK ADMIN aggregate -> 200'
assert_contains 'aggregate response' "$AGG_RESPONSE" 'service-a'
assert_contains 'aggregate response' "$AGG_RESPONSE" 'service-b'
assert_contains 'aggregate response' "$AGG_RESPONSE" 'service-c'

echo 'Testing batch jobs...'
assert_http 'BATCH user cannot access service-a' 403 GET "$TOKEN_BATCH" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'BATCH job accepted' 202 POST "$TOKEN_BATCH" "$GATEWAY_URL/service-consumer/api/users/1/batch-jobs"

echo 'Testing refresh token...'
REFRESH_RESPONSE=$(auth_refresh "$OPAQUE_ADMIN")
TOKEN_ADMIN_REFRESHED=$(echo "$REFRESH_RESPONSE" | jq -r '.access_token // empty')
if [ -z "$TOKEN_ADMIN_REFRESHED" ]; then
  echo "FAIL refresh token — no access_token in response"
  echo "$REFRESH_RESPONSE"
  exit 1
fi
echo "OK refresh token -> new access_token received"
assert_http 'Refreshed token works on service-a' 200 GET "$TOKEN_ADMIN_REFRESHED" "$GATEWAY_URL/service-a/api/resources-a"

echo 'Testing logout and blacklist...'
LOGOUT_LOGIN=$(auth_login test-service-a user123)
LOGOUT_ACCESS=$(echo "$LOGOUT_LOGIN" | jq -r '.access_token // empty')
LOGOUT_OPAQUE=$(echo "$LOGOUT_LOGIN" | jq -r '.opaque_refresh_token // empty')

assert_http 'Token works before logout' 200 GET "$LOGOUT_ACCESS" "$GATEWAY_URL/service-a/api/resources-a"

LOGOUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$GATEWAY_URL/auth/logout" \
  -H "Authorization: Bearer $LOGOUT_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"opaque_refresh_token\":\"$LOGOUT_OPAQUE\"}")
if [ "$LOGOUT_STATUS" != "204" ]; then
  echo "FAIL logout expected 204 got $LOGOUT_STATUS"
  exit 1
fi
echo "OK logout -> 204"

assert_http 'Blacklisted token rejected by gateway' 401 GET "$LOGOUT_ACCESS" "$GATEWAY_URL/service-a/api/resources-a"

STALE_REFRESH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$GATEWAY_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"opaque_refresh_token\":\"$LOGOUT_OPAQUE\"}")
if [ "$STALE_REFRESH_STATUS" != "401" ]; then
  echo "FAIL stale refresh expected 401 got $STALE_REFRESH_STATUS"
  exit 1
fi
echo "OK stale refresh token -> 401"

echo 'Testing admin user creation + self password change...'
KC_ADMIN=${KEYCLOAK_ADMIN:-admin}
KC_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}
KC_ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" --data-urlencode "client_id=admin-cli" \
  --data-urlencode "username=$KC_ADMIN" --data-urlencode "password=$KC_ADMIN_PASSWORD" \
  | jq -r '.access_token // empty')
if [ -z "$KC_ADMIN_TOKEN" ]; then echo 'FAIL could not obtain Keycloak master admin token'; exit 1; fi
echo 'OK Keycloak master admin token obtained'

# admin creates user admin2 (idempotent: 201 first run, 409 on re-run)
CREATE_ADMIN2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "$KEYCLOAK_URL/admin/realms/ms-realm/users" \
  -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"username":"admin2","enabled":true,"email":"admin2@example.com","firstName":"Admin","lastName":"Two","credentials":[{"type":"password","value":"admin2pass","temporary":false}]}')
if [ "$CREATE_ADMIN2_STATUS" != "201" ] && [ "$CREATE_ADMIN2_STATUS" != "409" ]; then
  echo "FAIL create admin2 expected 201 or 409 got $CREATE_ADMIN2_STATUS"; exit 1
fi
echo "OK admin2 created by admin (status $CREATE_ADMIN2_STATUS)"

ADMIN2_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/ms-realm/users?username=admin2&exact=true" \
  -H "Authorization: Bearer $KC_ADMIN_TOKEN" | jq -r '.[0].id // empty')
if [ -z "$ADMIN2_ID" ]; then echo 'FAIL could not resolve admin2 id'; exit 1; fi

# reset to a known baseline so this block is re-runnable against a live stack
curl -s -o /dev/null -X PUT "$KEYCLOAK_URL/admin/realms/ms-realm/users/$ADMIN2_ID/reset-password" \
  -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"password","value":"admin2pass","temporary":false}'

# grant realm role ADMIN to admin2 (idempotent)
ADMIN_ROLE_JSON=$(curl -s "$KEYCLOAK_URL/admin/realms/ms-realm/roles/ADMIN" \
  -H "Authorization: Bearer $KC_ADMIN_TOKEN")
curl -s -o /dev/null -X POST "$KEYCLOAK_URL/admin/realms/ms-realm/users/$ADMIN2_ID/role-mappings/realm" \
  -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "[$ADMIN_ROLE_JSON]"
echo 'OK admin2 granted realm role ADMIN'

ADMIN2_LOGIN=$(auth_login admin2 admin2pass)
TOKEN_ADMIN2=$(echo "$ADMIN2_LOGIN" | jq -r '.access_token // empty')
check_token TOKEN_ADMIN2 ADMIN2
assert_http 'admin2 (ADMIN) can access service-a' 200 GET "$TOKEN_ADMIN2" "$GATEWAY_URL/service-a/api/resources-a"

# admin2 changes its own password via the ms-auth self-service endpoint
PWD_CHANGE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/account/password" \
  -H "Authorization: Bearer $TOKEN_ADMIN2" -H "Content-Type: application/json" \
  -d '{"oldPassword":"admin2pass","newPassword":"admin2new"}')
if [ "$PWD_CHANGE_STATUS" != "204" ]; then
  echo "FAIL admin2 self password change expected 204 got $PWD_CHANGE_STATUS"; exit 1
fi
echo 'OK admin2 self password change -> 204'

NEW_PWD_TOKEN=$(auth_login admin2 admin2new | jq -r '.access_token // empty')
if [ -z "$NEW_PWD_TOKEN" ]; then echo 'FAIL admin2 login with new password failed'; exit 1; fi
echo 'OK admin2 logs in with new password'

OLD_PWD_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" -d '{"username":"admin2","password":"admin2pass"}')
if [ "$OLD_PWD_CODE" != "401" ]; then echo "FAIL admin2 old password expected 401 got $OLD_PWD_CODE"; exit 1; fi
echo 'OK admin2 old password rejected -> 401'

WRONG_OLD_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/account/password" \
  -H "Authorization: Bearer $NEW_PWD_TOKEN" -H "Content-Type: application/json" \
  -d '{"oldPassword":"definitelywrong","newPassword":"whatever123"}')
if [ "$WRONG_OLD_CODE" != "422" ]; then echo "FAIL wrong old password expected 422 got $WRONG_OLD_CODE"; exit 1; fi
echo 'OK wrong old password rejected -> 422'

echo 'All tests passed. tokens.env generated.'
