PWD_CHANGE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/account/password" -H "Authorization: Bearer $TOKEN_ADMIN2" -H "Content-Type: application/json" -d '{"oldPassword":"admin2pass","newPassword":"admin2new"}')
if [ "$PWD_CHANGE_STATUS" != "204" ]; then
  echo "FAIL admin2 self password change expected 204 got $PWD_CHANGE_STATUS"; exit 1
fi
echo 'OK admin2 self password change -> 204'

NEW_PWD_TOKEN=$(auth_login admin2 admin2new | jq -r '.access_token // empty')
if [ -z "$NEW_PWD_TOKEN" ]; then echo 'FAIL admin2 login with new password failed'; exit 1; fi
echo 'OK admin2 logs in with new password'

OLD_PWD_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"admin2","password":"admin2pass"}')
if [ "$OLD_PWD_CODE" != "401" ]; then echo "FAIL admin2 old password expected 401 got $OLD_PWD_CODE"; exit 1; fi
echo 'OK admin2 old password rejected -> 401'

WRONG_OLD_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/account/password" -H "Authorization: Bearer $NEW_PWD_TOKEN" -H "Content-Type: application/json" -d '{"oldPassword":"definitelywrong","newPassword":"whatever123"}')
if [ "$WRONG_OLD_CODE" != "422" ]; then echo "FAIL wrong old password expected 422 got $WRONG_OLD_CODE"; exit 1; fi
echo 'OK wrong old password rejected -> 422'

echo 'All tests passed. tokens.env generated.'
