# Phase 3a — `admin-application` : fondation + auth ADMIN + liste des users Keycloak

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** créer le module **toujours installé** `admin-application` — un BFF réservé `ROLE_ADMIN` qui
liste les utilisateurs via la Keycloak Admin REST API. Premier sous-bloc de la Phase 3. Création/
suppression d'utilisateurs (3b) et gestion des rôles (3c) hors périmètre.

## Contexte

Le générateur GestoMS produit une plateforme microservices. Keycloak (realm `ms-realm`) est permanent :
admin bootstrap `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` (`admin`/`admin`, realm `master`), URL interne
`http://keycloak:8080`, externe `http://localhost:8089`. Le realm a un seul client `ms-gateway`
(confidentiel, service account activé mais **sans** rôles `realm-management`). ms-auth/ms-client appellent
Keycloak/le gateway en **RestTemplate**. La Phase 2 a livré `ms-client` (BFF : login via ms-auth, session
Redis, Spring Security, rôles realm dans le `SecurityContext`).

La Phase 1 a décidé : `admin-application` est un **module séparé, toujours installé** (pas de feature
flag, contrairement à ms-client/ms-admin).

## Décisions de design (validées)

### A. Module séparé, toujours installé

`admin-application` est un nouveau module, présent quel que soit `clientWebUI` (toujours installé,
conforme Phase 1). On assume la **duplication de la fondation BFF** de ms-client (auth/session/security/
layout) plutôt qu'une factorisation transverse (gros chantier) ou un repli dans ms-client (qui le rendrait
optionnel).

### B. Décomposition de la Phase 3

| Sous-bloc | Contenu |
|-----------|---------|
| **3a (ce spec)** | Fondation + auth BFF réservée ADMIN + `KeycloakAdminClient` + **liste des users** (read-only) |
| **3b** | Création / suppression d'utilisateurs |
| **3c** | Gestion des rôles (liste, assignation/retrait) |

### C. Auth de l'UI : BFF via ms-auth, toute l'app réservée ADMIN

Login via ms-auth (`GATEWAY_URL/auth/login`), tokens en session (Spring Session + Redis), rôles realm dans
le `SecurityContext` — comme ms-client 2a. Mais `anyRequest().hasRole("ADMIN")` : un utilisateur
authentifié **sans** `ROLE_ADMIN` reçoit 403 partout. `/login`, `/css/**`, `/actuator/health` en permitAll.

### D. Accès Admin API : admin master via `admin-cli` (grant password)

`KeycloakAdminClient` obtient un token via `POST {keycloak.internal-url}/realms/master/protocol/openid-connect/token`,
`grant_type=password`, `client_id=admin-cli`, `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`. Ce token (admin
master) peut gérer `ms-realm`. Marche sans changement de realm. Écarté : un service account scopé
(mapping de rôles `realm-management` via un user `service-account-*` dans `users[]`, en conflit avec la
régénération de `users[]` par `rewriteRealm`).

### E. Client Admin API : RestTemplate brut

`KeycloakAdminClient` maison sur RestTemplate (cohérent avec ms-auth/ms-client, léger, sans dépendance
couplée à la version de Keycloak). Pas de `keycloak-admin-client`.

### F. Exposition : port dédié direct

Port `:9300` (`ADMIN_APP_PORT`), atteint directement par le navigateur (comme ms-admin/ms-client). Pas de
route gateway. Client Eureka (cohérent).

## Architecture & flux

Deux chemins serveur distincts :
- **Login (BFF)** : `MsAuthClient` → `GATEWAY_URL/auth/login` ; tokens en session ; rôles → `SecurityContext`.
- **Admin API** : `KeycloakAdminClient` → `KEYCLOAK_INTERNAL_URL` (`http://keycloak:8080`, **pas** le
  gateway) → token `admin-cli` (master) → `GET /admin/realms/ms-realm/users`.

1. `GET admin-application:9300/` non authentifié → redirect `/login`.
2. `POST /login` → ms-auth → `SecurityContext` (rôles). Authentifié sans `ROLE_ADMIN` → 403 partout.
3. `GET /` (ADMIN) → home (nav : Users ; Roles « à venir — 3c »).
4. `GET /users` (ADMIN) → `KeycloakAdminClient.listUsers()` → table (`username`, `email`, `prénom/nom`,
   `enabled`).

L'admin master n'est utilisé **que côté serveur** (jamais rendu dans la page).

## Composants (module `admin-application`, package `com.mr486.msplatform.adminapp`)

**Fondation BFF (calquée sur ms-client 2a, package `adminapp`, ADMIN-gated) — 17 fichiers :**
`pom.xml`, `Dockerfile`, `src/main/resources/application.yml`, `…/AdminAppApplication.java`,
`…/configuration/SecurityConfig.java` (`anyRequest().hasRole("ADMIN")`, AuthenticationEntryPoint `/login`,
SecurityContextRepository, logout handler), `…/configuration/RestTemplateConfig.java`,
`…/web/LoginController.java` (GET/POST `/login`, peuple + sauvegarde le `SecurityContext`),
`…/web/HomeController.java` (`GET /`), `…/service/MsAuthClient.java` (login/logout via `gateway.url`),
`…/dto/MsAuthTokens.java`, `…/security/JwtRoles.java` (rôles realm base64url),
`…/security/MsAuthLogoutHandler.java`, `…/security/SessionKeys.java`,
`templates/{layout,login,home}.html`, `static/css/app.css`.

**Spécifique Keycloak — 5 nouveaux fichiers :**
- `…/service/KeycloakAdminClient.java` — RestTemplate ; `adminToken()` (grant password `realms/master`,
  `admin-cli`, creds env) ; `listUsers() → List<KeycloakUser>` (`GET /admin/realms/{realm}/users`, Bearer).
  Exceptions → `KeycloakUnavailableException` (imbriquée).
- `…/dto/KeycloakUser.java` — `record KeycloakUser(String id, String username, String email,
  String firstName, String lastName, boolean enabled)` (`@JsonIgnoreProperties(ignoreUnknown=true)`).
- `…/web/UsersController.java` — `GET /users` → `listUsers()` → modèle `users` → vue `users` ;
  exception → modèle `error` « Keycloak indisponible ».
- `templates/users.html` — table (username, email, prénom/nom, enabled) + lien Accueil + zone d'erreur.
- `src/test/java/…/service/KeycloakAdminClientTest.java` — test embarqué Mockito : stub token + users →
  `listUsers()` parse correctement ; échec token → `KeycloakUnavailableException`.

**`application.yml` (clés) :** `server.port=${ADMIN_APP_PORT:9300}`, `spring.application.name=admin-application`,
session redis (`spring.session.store-type=redis`, `spring.data.redis.{host,port}`), `thymeleaf.cache=false`,
`gateway.url=${GATEWAY_URL:http://localhost:9000}`,
`keycloak.internal-url=${KEYCLOAK_INTERNAL_URL:http://keycloak:8080}`, `keycloak.realm=ms-realm`,
`keycloak.admin-username=${KEYCLOAK_ADMIN:admin}`, `keycloak.admin-password=${KEYCLOAK_ADMIN_PASSWORD:admin}`,
eureka, actuator health/info.

## Intégration générateur

`admin-application` est **toujours installé** :
- **`CrossCuttingConfigProcessor.desiredModules()`** : ajout **inconditionnel** de `admin-application`
  (après `ms-auth`).
- **`blocksToRemove()`** : **rien** (jamais retiré).
- **`docker-compose.yml`** (template) : bloc `admin-application:` permanent (build `./admin-application`,
  `env_file [.env]`, `depends_on [ms-eureka, ms-gateway, keycloak, redis, ms-auth]`, env
  `EUREKA_DEFAULT_ZONE`/`REDIS_HOST`/`GATEWAY_URL`/`KEYCLOAK_INTERNAL_URL`/`ADMIN_APP_PORT` — les creds
  `KEYCLOAK_ADMIN`/`_PASSWORD` viennent de `.env`), `ports ["9300:9300"]`. **Pas** dans `blocksToRemove`.
- **`dist.env`/`dot-env`** : `+ ADMIN_APP_PORT=9300` (creds Keycloak admin déjà présents).
- **`rewriteTestAll()`** : `wait_for 'admin-application' curl -fs http://localhost:9300/login` + smoke
  `curl -fs http://localhost:9300/login >/dev/null && echo 'Admin-app OK'` (inconditionnel).
- **`FeatureFilterProcessor`** : aucune règle (pas de flag → toujours conservé).
- **`TemplateLoaderTest`** : parité **147 → 169** (22 nouveaux fichiers).
- **`CrossCuttingConfigProcessorTest`** : +tests — `admin-application` toujours dans `<modules>` + bloc
  compose présent (avec et sans `resources[]`).

## Gestion d'erreurs

- **Non authentifié** → `/login` ; **authentifié non-ADMIN** → 403 (toute l'app `hasRole ADMIN`).
- **ms-auth indisponible** (login) → message « Service d'authentification indisponible ».
- **Keycloak Admin API indisponible / token refusé** → `KeycloakUnavailableException` ; `UsersController`
  capture → message « Keycloak indisponible » sur la page.
- **Session expirée** → redirection `/login`.
- **Creds admin master** : utilisés uniquement côté serveur (jamais rendus dans la page).

## Tests & vérification

- **Tests embarqués (hors-ligne)** : `KeycloakAdminClientTest` (token + parse users ; échec token →
  exception). La fondation BFF n'embarque pas de test (parité ms-client 2a).
- **Test générateur** : `CrossCuttingConfigProcessorTest` — `admin-application` toujours dans les modules +
  bloc compose présent (avec et sans `resources[]`).
- **`TemplateLoaderTest`** parité **169**.
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=true` **et** `clientWebUI=false` → `admin-application` **toujours présent** (module pom,
    bloc compose, dossier) — point clé « toujours installé ». `mvn -pl admin-application -am package` du
    projet généré **compile ET exécute `KeycloakAdminClientTest` vert** ; `docker compose config` valide.
  - Runtime (login admin → `/users` → liste réelle Keycloak) : **manuel/optionnel** (stack + Keycloak) —
    noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Création / suppression d'utilisateurs (3b), gestion des rôles (3c).
- Pagination / recherche des users, édition de profil, reset password.
- Mise en cache du token admin (récupéré par appel en 3a ; optimisation possible plus tard).
- Factorisation de la fondation BFF commune avec ms-client (duplication assumée).

## Fichiers touchés (Phase 3a)

**Template (nouveaux, 22) :** les 17 de la fondation BFF + `KeycloakAdminClient.java`, `KeycloakUser.java`,
`UsersController.java`, `templates/users.html`, `KeycloakAdminClientTest.java` — tous sous
`templates/ms-platform/admin-application/`.

**Template (modifiés) :** `docker-compose.yml` (+ bloc `admin-application`), `dist.env` + `dot-env`
(`ADMIN_APP_PORT`).

**Générateur (modifiés) :**
`src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
(`desiredModules` + `rewriteTestAll`),
`src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`,
`src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` (parité 147 → 169).
