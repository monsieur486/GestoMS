echo 'Testing logout and blacklist...'
LOGOUT_LOGIN=$(auth_login {{FIRST_USER}} user123)
LOGOUT_ACCESS=$(echo "$LOGOUT_LOGIN" | jq -r '.access_token // empty')
LOGOUT_OPAQUE=$(echo "$LOGOUT_LOGIN" | jq -r '.opaque_refresh_token // empty')

assert_http 'Token works before logout' 200 GET "$LOGOUT_ACCESS" "{{FIRST_URL}}"
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
assert_http 'Blacklisted token rejected by gateway' 401 GET "$LOGOUT_ACCESS" "{{FIRST_URL}}"
STALE_REFRESH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$GATEWAY_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"opaque_refresh_token\":\"$LOGOUT_OPAQUE\"}")
if [ "$STALE_REFRESH_STATUS" != "401" ]; then
  echo "FAIL stale refresh expected 401 got $STALE_REFRESH_STATUS"
  exit 1
fi
echo "OK stale refresh token -> 401"

