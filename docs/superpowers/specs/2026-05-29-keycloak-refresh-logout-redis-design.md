# Design : Keycloak — Refresh Token & Logout avec Redis

**Date :** 2026-05-29  
**Projet :** GestoMS — générateur de patterns microservices Spring Boot  
**Scope :** Finalisation Keycloak : gestion des refresh tokens (opaque/Redis) et logout avec blacklist JTI

---

## 1. Contexte

Le template généré (`ms-platform-template.zip`) contient une plateforme microservices avec :
- **ms-gateway** (WebFlux, port 9000) : routage simple, transfert du token Bearer
- **service-a/b/c, service-consumer, service-batch** : resource servers JWT, chacun valide indépendamment via Keycloak JWKS
- **Redis** : déjà présent, utilisé uniquement pour le stockage des batch jobs
- **Keycloak** (port 8089) : realm `ms-realm`, client `ms-gateway`, `directAccessGrantsEnabled=true`

**Manque actuel :**
- Pas d'endpoint de login/refresh/logout géré par la plateforme
- Les tokens sont obtenus directement depuis Keycloak dans `test-all.sh` via `grant_type=password`
- Pas de mécanisme de révocation / blacklist
- Pas de gestion des refresh tokens côté serveur

---

## 2. Objectifs

1. Exposer `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` via un nouveau service `ms-auth`
2. Stocker les refresh tokens Keycloak côté serveur dans Redis (opaque token UUID côté client)
3. Permettre le logout complet : révocation Keycloak + blacklist JTI dans Redis
4. Vérifier la blacklist au niveau gateway (filtre WebFlux réactif) avant tout routage

---

## 3. Architecture

```
Client
  │
  ▼
ms-gateway (9000)
  ├── TokenBlacklistFilter ──→ Redis REACTIVE
  │     └── EXISTS auth:blacklist:{jti} ? → 401 : continue
  │
  ├── /auth/** ─────────────→ ms-auth (9200)  [nouveau service]
  │     ├── POST /auth/login
  │     ├── POST /auth/refresh
  │     └── POST /auth/logout
  │
  ├── /service-a/**  ────────→ service-a
  ├── /service-b/**  ────────→ service-b
  ├── /service-c/**  ────────→ service-c
  └── /service-consumer/** ──→ service-consumer
```

---

## 4. Nouveau service : ms-auth

### 4.1 Caractéristiques

- **Framework :** Spring Boot MVC (non réactif)
- **Port :** `9200` (env `AUTH_PORT`)
- **Dépendances :** spring-boot-starter-web, spring-boot-starter-oauth2-resource-server, spring-boot-starter-data-redis, spring-cloud-starter-netflix-eureka-client, spring-boot-starter-actuator, lombok
- **Enregistrement Eureka :** nom `ms-auth`

### 4.2 Configuration sécurité

- `/auth/login` et `/auth/refresh` : **publics** (pas de JWT requis)
- `/auth/logout` : **authentifié** (Bearer JWT requis — pour extraire le JTI)
- `/actuator/**` : public

### 4.3 Endpoints

#### POST /auth/login
```
Request:  { "username": "...", "password": "..." }
Response: { "access_token": "...", "opaque_refresh_token": "uuid", "expires_in": 300 }
```
Logique :
1. Appelle `POST http://keycloak:8080/realms/ms-realm/protocol/openid-connect/token` avec `grant_type=password`
2. Stocke dans Redis : `auth:refresh:{uuid}` → JSON`{kc_refresh_token, username}` avec TTL = refresh_expires_in Keycloak
3. Retourne access_token + uuid opaque (le client ne voit jamais le refresh_token Keycloak)

#### POST /auth/refresh
```
Request:  { "opaque_refresh_token": "uuid" }
Response: { "access_token": "...", "opaque_refresh_token": "uuid", "expires_in": 300 }
```
Logique :
1. Lit `auth:refresh:{uuid}` dans Redis → erreur 401 si absent/expiré
2. Appelle Keycloak `/token` avec `grant_type=refresh_token` + `refresh_token={kc_refresh_token}`
3. Met à jour Redis : supprime l'ancien UUID, crée un nouvel UUID avec le nouveau kc_refresh_token
4. Retourne le nouveau access_token + nouvel UUID opaque

#### POST /auth/logout
```
Headers: Authorization: Bearer {access_token}
Request:  { "opaque_refresh_token": "uuid" }  [optionnel]
Response: 204 No Content
```
Logique :
1. Spring Security (resource server) valide le JWT et le place dans le `SecurityContext` avant d'entrer dans le controller
2. Extrait le JTI et `exp` directement depuis `((JwtAuthenticationToken) authentication).getToken().getClaims()`
3. Calcule `ttl = exp - now()` en secondes (durée de vie restante du token)
4. Stocke `auth:blacklist:{jti}` → `"1"` dans Redis avec `ttl` (si ttl > 0, sinon no-op)
5. Si `opaque_refresh_token` fourni : supprime `auth:refresh:{uuid}` de Redis
6. Appelle l'endpoint de révocation Keycloak (best-effort, pas de rollback si échec)

### 4.4 Redis keys (ajout dans common-lib)

```java
// Dans RedisKeys.java
public static String authBlacklist(String jti) { return "auth:blacklist:" + jti; }
public static String authRefresh(String opaqueToken) { return "auth:refresh:" + opaqueToken; }
```

---

## 5. Gateway — TokenBlacklistFilter

### 5.1 Dépendance ajoutée

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

### 5.2 Logique du filtre (GlobalFilter, ordre élevé)

```
Pour chaque requête entrante :
1. Lire Authorization header → extraire Bearer token
2. Si absent ou pas de JTI → laisser passer (les resource servers gèreront le 401)
3. Décoder payload JWT (Base64, sans vérification de signature)
4. Extraire claim "jti"
5. ReactiveRedisTemplate.hasKey("auth:blacklist:{jti}") 
6. Si true → retourner 401 { "error": "token_revoked" }
7. Si false → chain.filter(exchange)
```

Le filtre ne rejette pas les requêtes sans token (les resource servers sont responsables de l'authentification). Il ne fait que révoquer les tokens blacklistés.

### 5.3 Route ms-auth dans application.yml gateway

```yaml
- id: ms-auth
  uri: lb://ms-auth
  predicates:
    - Path=/auth/**
  # pas de StripPrefix : ms-auth expose /auth/login, /auth/refresh, /auth/logout
```

---

## 6. Keycloak realm — mise à jour

Rendre explicites dans `ms-realm-realm.json` :
- `accessTokenLifespan: 300` (5 minutes)
- `ssoSessionMaxLifespan: 1800` (30 minutes — durée refresh token)
- `refreshTokenMaxReuse: 0` (rotation des refresh tokens)

---

## 7. docker-compose.yml — ajout ms-auth

```yaml
ms-auth:
  build: ./ms-auth
  env_file: [.env]
  depends_on: [ms-eureka, keycloak, redis]
  environment:
    EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/
    KEYCLOAK_INTERNAL_URL: http://keycloak:8080
    REDIS_HOST: redis
  ports: ["9200:9200"]
```

---

## 8. test-all.sh — mise à jour

Remplacer l'appel direct à Keycloak par :
```bash
login_via_auth() {
  curl -s -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}"
}
```
Ajouter des tests :
- Refresh token : obtenir nouveau access_token via `/auth/refresh`
- Logout : appeler `/auth/logout`, puis vérifier que l'access_token révoqué retourne 401 sur une resource
- Vérifier que le refresh_token est invalidé après logout

---

## 9. Fichiers à créer/modifier dans le template ZIP

### Nouveaux fichiers (ms-auth)
- `ms-auth/Dockerfile`
- `ms-auth/pom.xml`
- `ms-auth/src/main/resources/application.yml`
- `ms-auth/src/main/java/…/auth/AuthApplication.java`
- `ms-auth/src/main/java/…/auth/controller/AuthController.java`
- `ms-auth/src/main/java/…/auth/service/AuthService.java`
- `ms-auth/src/main/java/…/auth/service/TokenBlacklistService.java`
- `ms-auth/src/main/java/…/auth/configuration/SecurityConfig.java`
- `ms-auth/src/main/java/…/auth/configuration/RedisConfig.java`
- `ms-auth/src/main/java/…/auth/dto/LoginRequest.java`
- `ms-auth/src/main/java/…/auth/dto/LoginResponse.java`
- `ms-auth/src/main/java/…/auth/dto/RefreshRequest.java`
- `ms-auth/src/main/java/…/auth/dto/LogoutRequest.java`
- `ms-auth/src/main/java/…/auth/dto/KeycloakTokenResponse.java`

### Fichiers modifiés
- `common-lib/src/main/java/…/common/constants/RedisKeys.java` — ajout clés auth
- `ms-gateway/pom.xml` — ajout Redis reactive
- `ms-gateway/src/main/resources/application.yml` — route ms-auth + Redis config
- `ms-gateway/src/main/java/…/gateway/GatewayApplication.java` → `filter/TokenBlacklistFilter.java`
- `keycloak/import/ms-realm-realm.json` — TTL explicites
- `docker-compose.yml` — service ms-auth
- `pom.xml` (root) — module ms-auth
- `test-all.sh` — utiliser ms-auth + tests refresh/logout
- `README.md` — documenter les nouveaux endpoints

---

## 10. Hors scope

- Interface utilisateur (frontend)
- Gestion de sessions multi-onglets
- PKCE / Authorization Code Flow (le `directAccessGrantsEnabled` suffit pour les tests)
- Rotation automatique des clés Keycloak
- Rate limiting sur `/auth/login`
