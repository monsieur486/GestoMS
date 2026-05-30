#!/usr/bin/env bash
set -euo pipefail
[ -f .env ] || cp dist.env .env
mvn clean package -DskipTests
docker compose --env-file .env up --build -d --scale service-batch="${BATCH_REPLICAS:-4}"
