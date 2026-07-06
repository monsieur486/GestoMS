echo 'Testing admin user creation + self password change...'
KC_ADMIN=${KEYCLOAK_ADMIN:-admin}
KC_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}
KC_ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" -H "Content-Type: application/x-www-form-urlencoded" --data-urlencode "grant_type=password" --data-urlencode "client_id=admin-cli" --data-urlencode "username=$KC_ADMIN" --data-urlencode "password=$KC_ADMIN_PASSWORD" | jq -r '.access_token // empty')
if [ -z "$KC_ADMIN_TOKEN" ]; then echo 'FAIL could not obtain Keycloak master admin token'; exit 1; fi
echo 'OK Keycloak master admin token obtained'

CREATE_ADMIN2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KEYCLOAK_URL/admin/realms/ms-realm/users" -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"username":"admin2","enabled":true,"email":"admin2@example.com","firstName":"Admin","lastName":"Two","credentials":[{"type":"password","value":"admin2pass","temporary":false}]}')
if [ "$CREATE_ADMIN2_STATUS" != "201" ] && [ "$CREATE_ADMIN2_STATUS" != "409" ]; then
  echo "FAIL create admin2 expected 201 or 409 got $CREATE_ADMIN2_STATUS"; exit 1
fi
echo "OK admin2 created by admin (status $CREATE_ADMIN2_STATUS)"

ADMIN2_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/ms-realm/users?username=admin2&exact=true" -H "Authorization: Bearer $KC_ADMIN_TOKEN" | jq -r '.[0].id // empty')
if [ -z "$ADMIN2_ID" ]; then echo 'FAIL could not resolve admin2 id'; exit 1; fi
curl -s -o /dev/null -X PUT "$KEYCLOAK_URL/admin/realms/ms-realm/users/$ADMIN2_ID/reset-password" -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"type":"password","value":"admin2pass","temporary":false}'
ADMIN_ROLE_JSON=$(curl -s "$KEYCLOAK_URL/admin/realms/ms-realm/roles/ADMIN" -H "Authorization: Bearer $KC_ADMIN_TOKEN")
curl -s -o /dev/null -X POST "$KEYCLOAK_URL/admin/realms/ms-realm/users/$ADMIN2_ID/role-mappings/realm" -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H "Content-Type: application/json" -d "[$ADMIN_ROLE_JSON]"
echo 'OK admin2 granted realm role ADMIN'

ADMIN2_LOGIN=$(auth_login admin2 admin2pass)
TOKEN_ADMIN2=$(echo "$ADMIN2_LOGIN" | jq -r '.access_token // empty')
check_token TOKEN_ADMIN2 ADMIN2
assert_http 'admin2 (ADMIN) can access {{FIRST_SERVICE}}' 200 GET "$TOKEN_ADMIN2" "{{FIRST_URL}}"

