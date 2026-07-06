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
