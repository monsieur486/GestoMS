#!/usr/bin/env bash
set -euo pipefail
docker compose --env-file .env down -v --remove-orphans --rmi local || true
docker network prune -f
docker volume prune -f
docker image prune -f
