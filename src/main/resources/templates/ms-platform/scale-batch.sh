#!/usr/bin/env bash
set -euo pipefail

[ -f .env ] || cp dist.env .env
# shellcheck disable=SC1091
source .env

REPLICAS="${1:-${BATCH_REPLICAS:-4}}"

echo "Scaling service-batch to ${REPLICAS} replicas..."
docker compose --env-file .env up -d --build --scale service-batch="${REPLICAS}"
docker compose ps service-batch
