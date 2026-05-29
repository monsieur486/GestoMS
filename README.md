# springboot-platform-generator V5.5

This generator returns the validated V5.4 microservices platform ZIP.

Generated platform includes:

- Eureka
- Gateway WebFlux (with reactive `TokenBlacklistFilter` for JWT revocation)
- Keycloak
- `ms-auth` — Spring Boot MVC service wrapping Keycloak password grant; exposes `/auth/login`, `/auth/refresh`, `/auth/logout` with opaque refresh tokens and Redis-backed JTI blacklist
- Spring Boot Admin
- `service-a`, `service-b`, `service-c`
- `service-consumer`
- `service-batch`
- `common-lib`
- RabbitMQ JSON messages
- Redis JSON text storage
- WebSocket batch notifications
- configurable batch processing
- `test-all.sh`
- `tokens.env`
- `benchmark-async-batch.sh`
- `scale-batch.sh`

Batch defaults:

```env
BATCH_REPLICAS=4
BATCH_FILE_CONCURRENCY=5
BATCH_MIN_DELAY_MS=500
BATCH_MAX_DELAY_MS=1500
BATCH_MEMORY_LIMIT=768m
```

## Run generator

```bash
mvn clean package
java -jar target/*.jar
```

## Generate platform

MongoDB resources do not need `idType`. When `databaseType` is `MONGO`, the generated ID is `String` automatically. For SQL resources, use `INTEGER`, `LONG`, or `UUID`.

```bash
curl -X POST http://localhost:8080/api/generate/platform \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform",
    "javaVersion": "17",
    "resources": [
      {
        "serviceName": "service-a",
        "className": "ResourceA",
        "routePrefix": "resources-a",
        "databaseType": "POSTGRES",
        "idType": "LONG"
      },
      {
        "serviceName": "service-b",
        "className": "ResourceB",
        "routePrefix": "resources-b",
        "databaseType": "MONGO"
      },
      {
        "serviceName": "service-c",
        "className": "ResourceC",
        "routePrefix": "resources-c",
        "databaseType": "H2",
        "idType": "UUID"
      }
    ],
    "batch": {
      "enabled": true,
      "replicas": 4,
      "fileConcurrency": 5,
      "minDelayMs": 500,
      "maxDelayMs": 1500,
      "memoryLimit": "768m"
    },
    "features": {
      "keycloak": true,
      "redis": true,
      "rabbitmq": true,
      "websocket": true,
      "admin": true,
      "grafana": false,
      "loki": false
    }
  }' \
  --output ms-platform.zip
```

## Test generated platform

```bash
unzip -o ms-platform.zip
cd ms-platform

docker compose down -v
./prod-start.sh
./test-all.sh
source tokens.env
./benchmark-async-batch.sh 10 5
```

Expected result:

```txt
10 HTTP 202
10 COMPLETED
```


## Observability générée

Le projet généré inclut maintenant une stack légère :

- Loki
- Promtail
- Grafana
- Dashboard Batch v2

Après génération du projet :

```bash
cp dist.env .env
./prod-start.sh
./test-all.sh
source tokens.env
./benchmark-async-batch.sh 10 5
```

Puis ouvrir :

```txt
http://localhost:3000
admin / admin
```

Dashboard : `Batch / Batch Dashboard`.


## Timezone

Les projets générés configurent `TZ=Europe/Paris` pour Loki, Promtail et Grafana. Dans Grafana, utiliser de préférence `Browser Time`.


## Project notes (Claude memory)

`docs/claude-memory/` contains a versioned snapshot of the persistent notes
Claude Code maintains while working on this project. Useful when reviewing
PRs or onboarding to understand *why* certain design choices were made
(e.g. the gateway parses JWT JTI without a JWT library, or why
`CrossCuttingConfigProcessor` runs at `@Order(60)`).

Start with [`docs/claude-memory/README.md`](docs/claude-memory/README.md) and
[`docs/claude-memory/MEMORY.md`](docs/claude-memory/MEMORY.md) (the index).
The snapshot is auto-synced from the live Claude memory at
`~/.claude/projects/.../memory/`; the live copy is the source of truth.
