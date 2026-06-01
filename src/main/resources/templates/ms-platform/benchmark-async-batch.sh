#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}
BATCH_USER=${BATCH_USER:-test-batch}
BATCH_PASSWORD=${BATCH_PASSWORD:-user123}

# Re-authenticate at each run so the token is always fresh.
echo "Authenticating as ${BATCH_USER}..."
BATCH_LOGIN=$(curl -s -X POST "${GATEWAY_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${BATCH_USER}\",\"password\":\"${BATCH_PASSWORD}\"}")
TOKEN_BATCH=$(echo "$BATCH_LOGIN" | jq -r '.access_token // empty')
if [ -z "$TOKEN_BATCH" ]; then
  echo "Authentication failed. Check GATEWAY_URL / BATCH_USER / BATCH_PASSWORD."
  exit 1
fi

REQUESTS="${1:-10}"
CONCURRENCY="${2:-10}"
FAILURE_RATE=0

if [ "${3:-}" = "--failure-rate" ]; then
  FAILURE_RATE="${4:-0}"
fi

RESULT_DIR="benchmark-results"
rm -rf "$RESULT_DIR"
mkdir -p "$RESULT_DIR/txt" "$RESULT_DIR/json"

create_job() {
  local i="$1"

  curl -s -w "\nHTTP_STATUS:%{http_code}\n" \
    -X POST \
    -H "Authorization: Bearer ${TOKEN_BATCH}" \
    "http://localhost:9000/service-consumer/api/users/${i}/batch-jobs?failureRate=${FAILURE_RATE}" \
    > "$RESULT_DIR/txt/create-${i}.txt"
}

START=$(date +%s)
running=0

for i in $(seq 1 "$REQUESTS"); do
  create_job "$i" &
  running=$((running + 1))

  if [ "$running" -ge "$CONCURRENCY" ]; then
    wait -n
    running=$((running - 1))
  fi
done

wait
CREATE_END=$(date +%s)

for f in "$RESULT_DIR"/txt/create-*.txt; do
  sed '/HTTP_STATUS:/d' "$f" > "$RESULT_DIR/json/$(basename "$f" .txt).json"
done

JOB_IDS=$(jq -r 'select(.jobId != null) | .jobId' "$RESULT_DIR"/json/create-*.json 2>/dev/null || true)

if [ -z "$JOB_IDS" ]; then
  echo "Async requests: $REQUESTS"
  echo "Create concurrency: $CONCURRENCY"
  echo "Create wall time: $((CREATE_END - START)) sec"
  echo "Create HTTP status distribution:"
  grep -h "HTTP_STATUS" "$RESULT_DIR"/txt/create-*.txt | cut -d: -f2 | sort | uniq -c
  echo "No jobs created. Sample response:"
  cat "$RESULT_DIR/json/create-1.json"
  echo
  exit 1
fi

for id in $JOB_IDS; do
  for t in $(seq 1 300); do
    curl -s \
      -H "Authorization: Bearer ${TOKEN_BATCH}" \
      "http://localhost:9000/service-consumer/api/batch-jobs/${id}" \
      > "$RESULT_DIR/json/job-${id}.json"

    status=$(jq -r '.status // empty' "$RESULT_DIR/json/job-${id}.json")

    if [ "$status" = "COMPLETED" ] || [ "$status" = "DEAD" ]; then
      break
    fi

    sleep 1
  done
done

END=$(date +%s)

echo "Async requests: $REQUESTS"
echo "Create concurrency: $CONCURRENCY"
echo "Create wall time: $((CREATE_END - START)) sec"
echo "Total wall time until completion: $((END - START)) sec"

echo "Create HTTP status distribution:"
grep -h "HTTP_STATUS" "$RESULT_DIR"/txt/create-*.txt | cut -d: -f2 | sort | uniq -c

echo "Job status distribution:"
if ls "$RESULT_DIR"/json/job-*.json >/dev/null 2>&1; then
  jq -r '.status // empty' "$RESULT_DIR"/json/job-*.json | sort | uniq -c
else
  echo "No job result files"
fi

echo "Instance distribution:"
if ls "$RESULT_DIR"/json/job-*.json >/dev/null 2>&1; then
  jq -r 'select(.instance != null) | .instance' "$RESULT_DIR"/json/job-*.json | sort | uniq -c
else
  echo "No job result files"
fi

echo "Sample final job:"
if ls "$RESULT_DIR"/json/job-*.json >/dev/null 2>&1; then
  last_job=$(ls "$RESULT_DIR"/json/job-*.json | tail -n 1)
  cat "$last_job"
else
  cat "$RESULT_DIR/json/create-1.json"
fi

echo
