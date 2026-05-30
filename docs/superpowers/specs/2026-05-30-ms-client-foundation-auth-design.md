# Phase 2a — Module `ms-client` : Fondation + Auth BFF

**Date:** 2026-05-30
**Statut:** spec validé section par section, prêt pour plan d'implémentation
**Périmètre:** fondation du module `ms-client` uniquement — démarrage, enregistrement Eureka,
authentification BFF via `ms-auth`, shell UI + page d'accueil protégée. Les pages métier
(consumer, CRUD générique, notifs batch, chat) sont hors périmètre (sous-blocs 2b–2e).

## Contexte

Le générateur GestoMS produit une plateforme microservices en transformant un template décompressé
(`src/main/resources/templates/ms-platform/`) via une chaîne de processors ordonnés par `@Order`. La
Phase 1 a réduit `FeatureOptions` à deux bascules : `springbootAdmin` (module `ms-admin`) et
`clientWebUI` (module `ms-client`). La règle de filtrage `ms-client/` existe déjà dans
`FeatureFilterProcessor` mais reste **inerte** tant que le dossier template n'existe pas.

Le module `ms-client` est l'UI Thymeleaf+JS de la plateforme. Son périmètre complet (décrit en Phase 1)
réunit cinq capacités assez indépendantes : auth BFF, CRUD générique runtime, page consumer, chat salon
public, notifs batch temps réel. On le **décompose en sous-blocs**, chacun avec son propre
spec → plan → implémentation, chacun restant vert (`mvn package` du projet généré OK).

### Décomposition de la Phase 2 (validée)

| Sous-bloc | Contenu | Dépend de |
|-----------|---------|-----------|
| **2a (ce spec)** | Fondation + Auth BFF : module qui démarre, Eureka, login/logout BFF (session serveur), shell UI + page d'accueil protégée | Phase 1 |
| **2b** | Page consumer (read-only `/service-consumer/api/aggregate`) ; introduit le proxy-avec-session + le refresh de token | 2a |
| **2c** | CRUD générique runtime (list/create/edit/delete des resource-services) | 2a |
| **2d** | Notifs batch temps réel (WS `/topic/batch`) | 2a |
| **2e** | Chat salon public (backend de chat : broker WS + diffusion) | 2a |

## Décisions de design (validées)

### A. Modèle d'authentification — BFF via `ms-auth`

`ms-client` est un **BFF** (Backend For Frontend). Le pattern de la plateforme centralise l'auth dans
`ms-auth` (grant password Keycloak, rotation de refresh token opaque, blacklist JTI). `ms-client` s'y
conforme :

- Le navigateur ne voit jamais de token — seulement un cookie de session.
- `ms-client` appelle `ms-auth` pour login/logout (et refresh à partir de 2b), stocke les tokens
  **côté serveur** en session, et (à partir de 2b) proxifie les appels backend en injectant le `Bearer`.
- **Aucun nouveau client Keycloak ni modification du realm** : `ms-auth` détient déjà le client et les
  utilisateurs de test (`test-admin/admin123`, `test-<service>/user123`, …).

### B. Exposition — port dédié direct

`ms-client` est exposé sur un **port dédié `:8090`** (précédent : `ms-admin:9100`), atteint directement
par le navigateur. Il n'est **pas** placé derrière le gateway (on évite la friction connue
`StripPrefix` + cookies de session + redirections relatives d'une UI stateful). Côté serveur,
`ms-client` parle au reste de la plateforme **uniquement via le gateway** (URL configurée `GATEWAY_URL`),
ce qui maintient une base d'appel unique réutilisable pour le proxy de 2b/2c.

### C. Session — Spring Session + Redis

La session HTTP (qui porte les tokens) est stockée via **Spring Session + Redis**. Redis est permanent
dans la plateforme depuis la Phase 1. Bénéfices : la session survit à un redémarrage de `ms-client` et
supporte plusieurs instances. Cohérent avec la philosophie « redis toujours installé ».

### D. Protection des pages + login — Spring Security idiomatique + auth custom

`ms-client` garde **Spring Security** : protection des routes via `authorizeHttpRequests`, CSRF activé
par défaut, `/logout` géré. Le login est une **étape d'authentification custom**, sans le filtre
`formLogin` (qui réclamerait `POST /login` et entrerait en conflit avec le controller) : on configure un
`AuthenticationEntryPoint` qui redirige les requêtes non authentifiées vers `/login`, et le
`LoginController` traite lui-même `GET` et `POST /login`. Sur `POST`, il appelle `ms-auth /auth/login`,
décode les rôles realm du JWT, construit l'`Authentication` (authorities `ROLE_*`), le pose dans le
`SecurityContext` **et le sauvegarde explicitement** via le `SecurityContextRepository`
(`HttpSessionSecurityContextRepository`) — Spring Security 6 ne sauvegarde plus automatiquement le
contexte ; Spring Session persiste ensuite la session (donc le contexte + les tokens) dans Redis. Avoir
les rôles dans le `SecurityContext` paie dès le CRUD (2c) et l'admin (Phase 3, `hasRole('ADMIN')`).

### E. Rendu UI — CSS minimal fait main

Un petit `app.css` auto-hébergé, **aucune dépendance externe** (offline, self-contained). Le shell
(`layout.html`, fragment Thymeleaf) est hérité par toutes les pages futures.

## Architecture & flux

`ms-client` = module Spring Boot MVC (Thymeleaf), port `:8090`, client Eureka, BFF.

**Flux d'authentification :**

1. `GET ms-client:8090/` non authentifié → Spring Security redirige vers `/login`.
2. `GET /login` → page Thymeleaf (formulaire username/password + jeton CSRF).
3. `POST /login` → `LoginController` appelle `ms-auth POST /auth/login` (via `GATEWAY_URL`). Réponse =
   `access_token` + `opaque_refresh_token`.
4. Succès → décodage des rôles `realm_access.roles` du JWT (base64url, **sans lib JWT** — comme le
   gateway parse déjà le JTI), construction de l'`Authentication` (authorities `ROLE_*`) posée dans le
   `SecurityContext` puis **sauvegardée** via le `SecurityContextRepository`, stockage des deux tokens en
   attributs de session (persistés Redis), redirect `/`.
5. `GET /` (authentifié) → page d'accueil : salutation + username + liste des rôles + liens « à venir »
   (consumer / CRUD / chat — placeholders inertes).
6. `POST /logout` → `MsAuthLogoutHandler` appelle `ms-auth /auth/logout` (Bearer + refresh token opaque)
   **avant** d'invalider la session, puis redirect `/login?logout`.

**Hors 2a (noté) :** le *refresh* de token n'est pas exercé en 2a (aucun appel backend ne le déclenche).
Les deux tokens sont stockés dès maintenant, prêts pour 2b.

## Structure du module `ms-client/` (template)

Sous `templates/ms-platform/ms-client/`, package `com.mr486.msplatform.client` (réécrit vers le
`basePackage` utilisateur par `PackagePlaceholderProcessor`) :

- `pom.xml` — parent `ms-platform` ; deps : `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`,
  `spring-boot-starter-security`, `spring-session-data-redis`, `spring-boot-starter-data-redis`,
  `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-actuator`.
- `Dockerfile` — calqué sur les autres modules.
- `src/main/resources/application.yml` — `server.port=8090`, eureka, `spring.session.store-type=redis`,
  `spring.data.redis.host/port` (env), `GATEWAY_URL` (env).
- `…/client/ClientApplication.java`
- `…/client/configuration/SecurityConfig.java` — `authorizeHttpRequests` (`/login`, `/css/**`,
  `/actuator/health` → `permitAll` ; reste → `authenticated`), `exceptionHandling` avec un
  `AuthenticationEntryPoint` redirigeant vers `/login` (pas de filtre `formLogin`), `logout()` +
  `MsAuthLogoutHandler`, CSRF activé par défaut. Expose un `SecurityContextRepository`
  (`HttpSessionSecurityContextRepository`) injecté dans le `LoginController`.
- `…/client/web/LoginController.java` — `GET /login` (form), `POST /login` (auth via ms-auth →
  `SecurityContext` + session).
- `…/client/web/HomeController.java` — `GET /` → vue home.
- `…/client/service/MsAuthClient.java` — `RestTemplate` vers `ms-auth` (login/logout ; refresh prêt pour
  2b) via `GATEWAY_URL`.
- `…/client/security/JwtRoles.java` — extraction des rôles realm du JWT (base64url, sans lib).
- `…/client/security/MsAuthLogoutHandler.java`
- `src/main/resources/templates/` — `layout.html` (fragment shell), `login.html`, `home.html`.
- `src/main/resources/static/css/app.css` — CSS minimal fait main.

## Intégration dans le générateur

- **`FeatureFilterProcessor`** : la règle `ms-client/` existe déjà (Phase 1, inerte). Elle devient
  active dès que le dossier existe — **aucune modif**.
- **`PackagePlaceholderProcessor`** (@Order 30) : réécrit `com.mr486.msplatform.client` → `basePackage`
  automatiquement — **aucune modif**.
- **`CrossCuttingConfigProcessor`** (@Order 60), en miroir exact de `ms-admin` :
  - `desiredModules()` : `if (f.isClientWebUI()) modules.add("ms-client");` (après `ms-admin`).
  - `blocksToRemove()` : `if (!f.isClientWebUI()) blocks.add("ms-client");`
  - `rewriteGatewayYml()` : **inchangé** (port direct, aucune route gateway).
  - `rewriteTestAll()` : gated par `clientWebUI` — `wait_for 'ms-client' curl -fs http://localhost:8090/login`
    + smoke `curl -fs http://localhost:8090/login >/dev/null && echo 'Client OK'` dans la section infra.
- **`docker-compose.yml`** (template) : ajout d'un bloc `ms-client:` (build `./ms-client`,
  `depends_on: [ms-eureka, ms-gateway, redis, ms-auth]`, env
  `SERVER_PORT` / `EUREKA…` / `SPRING_DATA_REDIS_HOST=redis` / `GATEWAY_URL`, `ports: ["8090:8090"]`).
  Retiré par `blocksToRemove()` quand `clientWebUI=false` (même mécanique que `ms-admin`).
- **`dist.env` / `dot-env`** : ajout `MS_CLIENT_PORT=8090` (réutilise les vars redis/eureka/gateway
  existantes).
- **Realm Keycloak** : **inchangé** (ms-client passe par ms-auth).

## Gestion d'erreurs

- **Identifiants invalides** (ms-auth → 401) : retour `/login` avec « Identifiants invalides ».
- **ms-auth indisponible** (refus de connexion / 5xx) : `/login` avec « Service d'authentification
  indisponible ».
- **Session expirée / absente** : Spring Security redirige vers `/login`.
- **CSRF** : activé ; les formulaires login & logout portent le jeton (Thymeleaf).
- **Expiration de l'access token en cours de session** : hors 2a (pas d'appel backend) — refresh câblé
  en 2b.

## Tests & vérification

**Tests du générateur (là où la valeur est réelle) :**

- `CrossCuttingConfigProcessorTest` : `clientWebUI=true` → `<module>ms-client</module>` **présent** +
  bloc compose `ms-client:` **présent** ; `clientWebUI=false` → les deux **absents** (miroir des tests
  `ms-admin`).
- `FeatureFilterProcessorTest` : les tests `ms-client` de Phase 1 restent valides (le dossier réel
  existe désormais).
- `TemplateLoaderTest` : **mise à jour du compteur de parité** (112 → 112 + N fichiers `ms-client`).

**Pas de tests unitaires embarqués dans le module `ms-client`** en 2a (parité avec `ms-admin`/`ms-auth`
qui n'en embarquent pas, et pour garder le `mvn test` du projet généré vert sans redis/ms-auth en
marche). Correction vérifiée par les tests générateur + le smoke e2e.

**Vérification end-to-end** (port dédié `8077`, piège connu du serveur zombie sur `:8080`) :

- Génération `clientWebUI=true` → `mvn -DskipTests package` du projet généré **compile** `ms-client` ;
  `docker compose config` **valide** ; module + bloc compose présents.
- Génération `clientWebUI=false` → `ms-client` **absent** partout (module, compose, dossier).
- Montée de stack + login réel : **optionnel/manuel** (lourd) — noté explicitement NON vérifié
  automatiquement si Docker indisponible.

## Hors périmètre (noté, non traité ici)

- Page consumer (2b), CRUD générique runtime (2c), notifs batch temps réel (2d), chat salon public (2e).
- Refresh de token (introduit en 2b avec le premier appel backend proxifié).
- Routes gateway pour `ms-client` (non requises — exposition par port direct).
- Tests unitaires embarqués dans le module `ms-client`.

## Fichiers touchés (Phase 2a)

**Template (nouveaux fichiers `ms-client/`) :** `pom.xml`, `Dockerfile`, `application.yml`,
`ClientApplication.java`, `SecurityConfig.java`, `LoginController.java`, `HomeController.java`,
`MsAuthClient.java`, `JwtRoles.java`, `MsAuthLogoutHandler.java`, `layout.html`, `login.html`,
`home.html`, `app.css`.

**Template (modifiés) :** `docker-compose.yml` (+ bloc `ms-client:`), `dist.env` / `dot-env`
(`MS_CLIENT_PORT`).

**Générateur (modifiés) :**
`src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
(`desiredModules`, `blocksToRemove`, `rewriteTestAll`).

**Tests (modifiés) :** `CrossCuttingConfigProcessorTest.java`, `TemplateLoaderTest.java` (compteur de
parité).
