echo 'Testing refresh token...'
REFRESH_RESPONSE=$(auth_refresh "$OPAQUE_ADMIN")
TOKEN_ADMIN_REFRESHED=$(echo "$REFRESH_RESPONSE" | jq -r '.access_token // empty')
if [ -z "$TOKEN_ADMIN_REFRESHED" ]; then
  echo "FAIL refresh token — no access_token in response"
  echo "$REFRESH_RESPONSE"
  exit 1
fi
echo "OK refresh token -> new access_token received"
