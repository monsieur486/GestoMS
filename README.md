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

## Architecture de la plateforme générée

### Schéma global

```
                         ┌──────────────────────┐
                         │      ms-eureka        │
                         │      :8761            │
                         │   Service Discovery   │
                         └──────────┬───────────┘
                                    │  ← tous les services s'enregistrent
     Navigateur / Client            │
            │                       │
            ▼                       │
    ┌───────────────┐               │
    │  ms-gateway   │◄──────────────┘  load balancing dynamique
    │  :8080        │
    │  JWT filter   │  ← vérifie signature + JTI blacklist (Redis)
    └───────┬───────┘
            │
  ┌─────────┼──────────────────────────────────────────────┐
  │         │                                              │
  ▼         ▼              ▼              ▼                ▼
┌──────┐ ┌────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐
│ms-   │ │service │  │service-  │  │service-  │  │admin-         │
│auth  │ │-*      │  │consumer  │  │batch     │  │application    │
│:8081 │ │:8XXX   │  │:8070     │  │:8082     │  │:9300          │
└──┬───┘ └───┬────┘  └────┬─────┘  └────┬─────┘  └───────────────┘
   │         │            │             │
   ▼         ▼            │ WebSocket   ▼
┌──────┐ ┌────────┐       │ /topic/  ┌──────────┐
│Key-  │ │Postgres│       │ batch    │RabbitMQ  │
│cloak │ │MongoDB │       ▼          │(jobs)    │
│:8089 │ │H2      │  ms-client       └──────────┘
└──┬───┘ └────────┘  :8090 (opt.)
   │                 BFF Thymeleaf
   ▼
┌──────┐
│Redis │  ← sessions Spring + JTI blacklist
└──────┘

Optionnels :
  ms-client    :8090  BFF Thymeleaf       (features.clientWebUI: true)
  ms-admin     :9100  Spring Boot Admin   (features.springbootAdmin: true)
  observability       Loki+Promtail+Grafana :3000  (batch.grafana: true)
```

---

### Services — fonctions et possibilités

#### `ms-eureka` — Service Discovery (`:8761`)
- Serveur Eureka : registre central de tous les services
- Tous les modules s'y enregistrent au démarrage ; le gateway s'en sert pour le load balancing
- UI Eureka accessible sur `:8761`

---

#### `ms-gateway` — Point d'entrée unique (`:8080`)
- Reverse proxy **WebFlux** (non-bloquant) — route toutes les requêtes externes
- **TokenBlacklistFilter** : vérifie le JTI du JWT dans Redis avant chaque requête (révocation immédiate sans attendre l'expiration)
- Parse le JWT sans librairie dédiée (extraction du claim `realm_access.roles` en base64)
- Routes générées dynamiquement selon les modules activés (`CrossCuttingConfigProcessor`)

---

#### `ms-auth` — Authentification (`:8081`)
- Wrapping du **Keycloak password grant** — le client n'interagit jamais directement avec Keycloak
- `POST /auth/login` — username + password → access token JWT + refresh token opaque
- `POST /auth/refresh` — rotation atomique du refresh token (Redis `GETDEL`) → nouveaux tokens
- `POST /auth/logout` — blacklist le JTI dans Redis → révocation immédiate sur toute la plateforme
- Refresh token opaque (UUID) stocké dans Redis avec TTL — non décodable côté client

---

#### `keycloak` — Identity Provider (`:8089`)
- Realm `ms-realm` importé automatiquement au premier démarrage (JSON embarqué)
- Gestion des utilisateurs, mots de passe, rôles realm
- Utilisateurs de test pré-créés selon les services générés (`test-admin`, `test-<serviceName>`…)
- Console d'administration master sur `:8089` (compte `admin` / `admin`)

---

#### `admin-application` — UI d'administration (`:9300`)
- Interface **Thymeleaf** réservée au rôle `ROLE_ADMIN`
- **Gestion des utilisateurs** : liste paginée + recherche, création, édition (email/prénom/nom/actif), reset mot de passe, suppression
- **Gestion des rôles** : créer / supprimer des rôles realm Keycloak, assigner / retirer des rôles par utilisateur
- Protections serveur : l'admin connecté ne peut pas se supprimer, ni modifier ses propres rôles, ni supprimer `ROLE_ADMIN`
- Thème Bootstrap 5.3 sombre (violet/cyan)

---

#### `common-lib` — Bibliothèque partagée
- DTOs communs (`BatchJobResult`…) partagés entre services via dépendance Maven
- Importée par `service-consumer`, `service-batch` et les services métier

---

#### `service-*` — Services métier (`:8XXX`, un par `resources[]`)
- **CRUD REST** complet : `GET /api/{resource}s`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`
- **Base de données** configurable par service : PostgreSQL (défaut), MongoDB, H2 in-memory
- **Type d'identifiant** configurable : `LONG` (défaut), `INTEGER`, `UUID` — automatiquement `String` pour MongoDB
- Accès sécurisé par JWT (rôle `USER_<SERVICE_NAME>` requis)
- Conteneur de base de données généré automatiquement dans docker-compose (sauf H2)

---

#### `service-consumer` — Agrégat & WebSocket (`:8070`)
- `GET /api/aggregate` — appelle tous les services métier en parallèle et agrège les réponses (réservé `ROLE_ADMIN`)
- **Broker WebSocket** STOMP : publie les résultats de jobs batch sur `/topic/batch`
- Les clients (ms-client, navigateur) s'y connectent via SockJS pour recevoir les notifications en temps réel

---

#### `service-batch` — Traitement asynchrone (`:8082`)
- Consomme des jobs depuis **RabbitMQ** (messages JSON)
- Traitement configurable : concurrence fichiers (`BATCH_FILE_CONCURRENCY`), délais aléatoires, limite mémoire
- **Scalable horizontalement** : `BATCH_REPLICAS` instances parallèles via docker-compose `--scale`
- Publie le résultat de chaque job (jobId, status, count, durée, instance) via WebSocket → service-consumer
- Scripts fournis : `benchmark-async-batch.sh` (charge), `scale-batch.sh` (redimensionnement)

---

#### `ms-client` — BFF Thymeleaf (`:8090`) *(optionnel — `clientWebUI: true`)*
- **Login / logout** via ms-auth (session Redis, pas de JWT côté navigateur)
- **Dashboard** avec accès conditionnel selon les rôles (CRUD, Notifications, Chat, Consumer si ADMIN)
- **CRUD générique** sur tous les services métier générés (liste, création, édition, suppression)
- **Notifications batch** en temps réel (WebSocket SockJS/STOMP)
- **Chat** temps réel partagé entre tous les utilisateurs connectés
- **Mon compte** : affichage des rôles, lien reset password Keycloak
- Thème Bootstrap 5.3 sombre (violet/cyan)

---

#### `ms-admin` — Spring Boot Admin (`:9100`) *(optionnel — `springbootAdmin: true`)*
- Monitoring de tous les services enregistrés dans Eureka
- Santé, métriques, threads, environnement, logs, JVM par service
- Thème sombre personnalisé (palette violet, dark mode forcé)

---

#### `observability` — Stack de logs *(optionnelle — `batch.grafana: true`)*
- **Loki** — stockage et indexation des logs de tous les conteneurs
- **Promtail** — agent de collecte (monte `/var/lib/docker/containers`)
- **Grafana** (`:3000`) — visualisation, dont le dashboard "Batch Dashboard" pré-configuré
- Timezone `Europe/Paris` configurée sur toute la stack

---

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
