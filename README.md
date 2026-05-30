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

### Request fields

`POST /api/generate/platform` accepts a JSON body matching `PlatformGenerationRequest`. Every field is optional; defaults shown below.

| Field | Type | Default | Effect |
| ----- | ---- | ------- | ------ |
| `name` | string | `ms-platform` | Top-level folder name in the ZIP |
| `groupId` | string | `com.mr486` | Maven `<groupId>` in every generated pom |
| `basePackage` | string | `com.mr486.msplatform` | Java root package for all services |
| `javaVersion` | string | `17` | `<java.version>` in root pom |
| `features` | object | see below | Toggle optional components on/off |
| `batch` | object | see below | Configure `service-batch` runtime |
| `resources` | array | `[]` | If non-empty, replaces `service-a/b/c` with custom services |

#### `features`

Keycloak (+ `ms-auth`), Redis, RabbitMQ and WebSocket are **always installed** — they no longer have toggles. `admin-application` (the Keycloak users/roles admin UI, `ROLE_ADMIN`-only) is **always installed** too. Only the two modules below are optional; full observability (loki + promtail + grafana) is driven by `batch.grafana`.

| Field | Default | When `true` includes |
| ----- | ------- | -------------------- |
| `springbootAdmin` | `false` | `ms-admin/` — Spring Boot Admin monitoring UI (`:9100`) |
| `clientWebUI` | `false` | `ms-client/` — Thymeleaf BFF UI (`:8090`): login via ms-auth, consumer aggregate, generic CRUD, live batch notifications, public chat |

Legacy/unknown flags (`keycloak`, `redis`, `rabbitmq`, `websocket`, `admin`, `grafana`, `loki`) are **ignored** — a request carrying them deserializes fine (Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`), it just loses those toggles.

#### `batch` (defaults match the validated platform)

```json
{ "enabled": true, "grafana": false, "replicas": 4, "fileConcurrency": 5,
  "minDelayMs": 500, "maxDelayMs": 1500, "memoryLimit": "768m" }
```

When `enabled: false`, `service-batch/` (module + docker-compose block) is excluded. When `grafana: true`, the full observability stack is installed (`observability/` = loki + promtail + grafana, plus their compose blocks).

#### `resources[]` (`ResourceModuleRequest`)

| Field | Type | Required | Notes |
| ----- | ---- | -------- | ----- |
| `serviceName` | string | yes | kebab-case, used as module name and folder (e.g. `order-service`) |
| `className` | string | yes | PascalCase entity class (e.g. `Order`) |
| `routePrefix` | string | no | Defaults to `/api/{classNameLower}s` (e.g. `Order` → `/api/orders`) |
| `databaseType` | enum | no | `POSTGRES` (default), `H2` (in-memory, no db container), `MONGO` |
| `idType` | enum | no | `LONG` (default), `INTEGER`, `UUID`. Ignored for `MONGO` (always `String`) |

When `resources` is non-empty, the default `service-a/b/c` modules + their docker-compose blocks + volume entries are removed and replaced with one block per resource (POSTGRES/MONGO get a sibling `*-db` block and named volume; H2 gets neither).

### Minimal example — 1 service, default everything

```bash
curl -X POST http://localhost:8080/api/generate/platform \
  -H "Content-Type: application/json" \
  -d '{
    "resources": [
      { "serviceName": "order-service", "className": "Order" }
    ]
  }' \
  --output ms-platform.zip
```

This emits a complete platform with Keycloak + ms-auth + Eureka + Gateway + `admin-application` (always installed) + a single `order-service` backed by PostgreSQL, with `/api/orders` routes. `ms-admin` and `ms-client` are absent (both default off); observability is absent (`batch.grafana` default false).

### Full example — 3 services, all flags explicit

```bash
curl -X POST http://localhost:8080/api/generate/platform \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform",
    "groupId": "com.acme",
    "basePackage": "com.acme.shop",
    "javaVersion": "17",
    "resources": [
      {
        "serviceName": "order-service",
        "className": "Order",
        "routePrefix": "/api/orders",
        "databaseType": "POSTGRES",
        "idType": "LONG"
      },
      {
        "serviceName": "product-service",
        "className": "Product",
        "routePrefix": "/api/products",
        "databaseType": "MONGO"
      },
      {
        "serviceName": "inventory-service",
        "className": "Item",
        "routePrefix": "/api/items",
        "databaseType": "H2",
        "idType": "UUID"
      }
    ],
    "batch": {
      "enabled": true,
      "grafana": true,
      "replicas": 4,
      "fileConcurrency": 5,
      "minDelayMs": 500,
      "maxDelayMs": 1500,
      "memoryLimit": "768m"
    },
    "features": {
      "springbootAdmin": true,
      "clientWebUI": true
    }
  }' \
  --output ms-platform.zip
```

This emits the full platform: permanent core (keycloak/ms-auth/redis/rabbitmq/eureka/gateway/service-consumer + `admin-application`), the three custom services, `service-batch`, full observability (`batch.grafana=true`), plus the two optional modules `ms-admin` and `ms-client`.

## Utilisateurs Keycloak

Le realm `ms-realm` est importé au premier démarrage avec les utilisateurs de test ci-dessous
(définis dans `keycloak/import/ms-realm-realm.json`). Mots de passe **permanents** (non temporaires).

| Utilisateur | Mot de passe | Rôles realm |
| ----------- | ------------ | ----------- |
| `test-admin` | `admin123` | `ADMIN`, `USER_BATCH`, `USER_SERVICE_A`, `USER_SERVICE_B`, `USER_SERVICE_C` |
| `test-batch` | `user123` | `USER_BATCH` |
| `test-service-a` | `user123` | `USER_SERVICE_A` |
| `test-service-b` | `user123` | `USER_SERVICE_B` |
| `test-service-c` | `user123` | `USER_SERVICE_C` |

Rôles realm définis : `ADMIN`, `USER_BATCH`, `USER_SERVICE_A`, `USER_SERVICE_B`, `USER_SERVICE_C`, `SERVICE`.
Seul `test-admin` (rôle `ADMIN`) peut se connecter à `admin-application` (`:9300`) et à `ms-client` (`:8090`).

**Console d'administration Keycloak** (`http://localhost:8089`) — compte master, distinct des utilisateurs du realm :

| Utilisateur | Mot de passe | Source (`.env`) |
| ----------- | ------------ | --------------- |
| `admin` | `admin` | `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` |

> **Mode `resources[]`** : quand `resources` est non vide, `service-a/b/c` disparaissent et le realm est
> réécrit en conséquence — les utilisateurs `test-service-a/b/c` sont remplacés par un `test-<serviceName>`
> par resource (mot de passe `user123`, rôle `USER_<SERVICE_NAME>`), et `test-admin` reçoit le rôle de
> chaque resource. Exemple avec `order-service` : utilisateur `test-order-service` / `user123`, rôle
> `USER_ORDER_SERVICE`.

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
