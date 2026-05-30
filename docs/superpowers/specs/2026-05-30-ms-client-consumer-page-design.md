# Phase 2b — `ms-client` : page consumer + proxy-avec-session + refresh

**Date:** 2026-05-30
**Statut:** spec validé section par section, prêt pour plan d'implémentation
**Périmètre:** ajouter au module `ms-client` (posé en 2a) une page `/consumer` read-only qui affiche
l'agrégat `service-consumer`, et introduire pour la première fois le **proxy-avec-session** (appel
backend via le gateway avec le Bearer de session) et le **refresh de token réactif**. CRUD (2c),
notifs (2d) et chat (2e) restent hors périmètre.

## Contexte

`ms-client` (Phase 2a) est un BFF Spring Boot MVC exposé sur `:8090` : login/logout via `ms-auth`,
tokens stockés côté serveur en session (Spring Session + Redis), pages protégées par Spring Security
avec les rôles realm dans le `SecurityContext` (`ROLE_*`). La 2a ne faisait **aucun** appel backend.

La 2b ajoute la première page qui consomme le backend. L'endpoint cible est
`GET /service-consumer/api/aggregate` (via le gateway), **`@PreAuthorize("hasRole('ADMIN')")`** côté
backend, qui renvoie un `Map<String,String>` : clé = nom de service, valeur = le corps JSON brut de ce
service. ms-auth `/auth/refresh` effectue une **rotation** du refresh token opaque : chaque refresh
renvoie un nouvel `access_token` ET un nouvel `opaque_refresh_token`.

## Décisions de design (validées)

### A. Refresh réactif sur 401

ms-client appelle le backend avec l'access token de session ; **sur 401**, il appelle
`ms-auth /auth/refresh` (rotation), met à jour les **deux** tokens en session, et **rejoue une fois**.
Échec du refresh ou 2e 401 → session considérée expirée → redirection `/login`. Choix réactif (vs
proactif sur `expires_in`) : plus simple, robuste, et couvre aussi la révocation côté serveur.

### B. Accès `/consumer` admin-only côté ms-client

ms-client protège `/consumer` par `hasRole('ADMIN')` (miroir du `@PreAuthorize` backend, défense en
profondeur) et n'affiche le lien sur la page d'accueil qu'aux admins. Le backend reste l'autorité ; le
403 backend n'est qu'un backstop. Exploite les rôles mis dans le `SecurityContext` en 2a.

### C. Affichage : bloc par service, JSON ré-indenté

Un titre par nom de service + son corps JSON **ré-indenté** (Jackson pretty-print) dans un `<pre>`.
Si une valeur n'est pas du JSON parsable, on affiche le brut.

### D. Structure : `GatewayClient` dédié + `MsAuthClient.refresh()`

- `MsAuthClient` (BFF auth) gagne uniquement `refresh(opaqueRefreshToken) → MsAuthTokens`.
- Nouveau `GatewayClient` (proxy backend) : porte l'appel Bearer + le retry-sur-401 + la mise à jour de
  session. `ConsumerController` reste mince.
- Responsabilités nettes ; retry + rotation explicites et testables.

### E. Test embarqué hors-ligne pour `GatewayClient`

La logique 401→refresh→retry est la partie risquée et n'a aucune autre couverture automatique (le code
du template n'est pas compilé/testé par le générateur ; un e2e réel exigerait toute la stack). On ajoute
donc un **test unitaire embarqué** dans `ms-client/src/test` (JUnit5 + Mockito, mocks `RestTemplate` +
`MsAuthClient`, `MockHttpSession`) — hors-ligne, tourne vert dans le `mvn test` du projet généré sans
infra. Cela introduit `spring-boot-starter-test` (scope `test`) dans le pom `ms-client` (déviation
assumée de la décision « aucun test embarqué » de 2a, justifiée par la criticité de la logique refresh).

## Architecture & flux

`GET /consumer` (admin seulement) :

1. Spring Security exige `hasRole('ADMIN')` sur `/consumer`. Un non-admin authentifié → 403 Spring
   Security (aucun appel backend).
2. `ConsumerController` lit la session et délègue à
   `GatewayClient.get(session, "/service-consumer/api/aggregate")`.
3. `GatewayClient` : `GET GATEWAY_URL/service-consumer/api/aggregate` avec `Bearer <access token>`.
   - **200** → renvoie le corps (`Map<String,String>` : service → JSON brut).
   - **401** → `MsAuthClient.refresh(<refresh token de session>)` ; met à jour
     `SessionKeys.ACCESS_TOKEN` + `SessionKeys.REFRESH_TOKEN` (rotation) ; **rejoue une fois**. 2e 401 ou
     refresh en échec → `SessionExpiredException`.
   - **403** → `BackendForbiddenException` (backstop).
   - **5xx / connexion** → `BackendUnavailableException`.
4. `ConsumerController` parse le `Map<String,String>`, ré-indente chaque valeur (pretty-print, brut si
   non parsable), met `services` dans le modèle, rend `consumer.html`. `SessionExpiredException` →
   `redirect:/login?expired`. Forbidden / Unavailable → message d'erreur sur la page.

`MsAuthClient` (BFF auth) ne gagne que `refresh()` ; `GatewayClient` (proxy backend) porte le retry et la
mise à jour de session.

## Composants (module `ms-client`)

**Nouveaux fichiers (4) :**
- `…/client/service/GatewayClient.java` — `get(HttpSession, String) → String` ; logique Bearer +
  refresh-retry + mise à jour session. Exceptions en classes imbriquées (comme `MsAuthClient`) :
  `SessionExpiredException`, `BackendForbiddenException`, `BackendUnavailableException` (publiques) et un
  signal interne pour le 401.
- `…/client/web/ConsumerController.java` — `GET /consumer` ; délègue, parse, ré-indente, rend ; mappe les
  exceptions (Section « Gestion d'erreurs »).
- `src/main/resources/templates/consumer.html` — lien `← Accueil`, titre, un bloc par service
  (`<h2>nom</h2><pre>JSON indenté</pre>`), zone d'erreur `${error}`.
- `src/test/java/…/client/service/GatewayClientTest.java` — JUnit5 + Mockito + `MockHttpSession`.

**Fichiers modifiés :**
- `…/client/service/MsAuthClient.java` — `+ refresh(opaqueRefreshToken) → MsAuthTokens` (POST ms-auth
  `/auth/refresh` via `gateway.url` ; garde body null comme `login`).
- `…/client/configuration/SecurityConfig.java` — `+ .requestMatchers("/consumer").hasRole("ADMIN")`
  avant `anyRequest().authenticated()`.
- `src/main/resources/templates/home.html` — lien « Page consumer » réel et conditionnel :
  `th:if="${roles.contains('ROLE_ADMIN')}"` → `<a th:href="@{/consumer}">`. CRUD/chat restent « à venir ».
- `src/main/resources/templates/login.html` — affiche « Session expirée, reconnectez-vous » via
  `th:if="${param.expired}"` (cible de `redirect:/login?expired`).
- `pom.xml` — `+ spring-boot-starter-test` (scope `test`).
- `src/main/resources/static/css/app.css` — petit style `.service-block` / `pre` (minimal).

## Intégration générateur

- **`FeatureFilterProcessor`** : nouveaux fichiers sous `ms-client/` → couverts par la règle existante.
  **Aucune modif.**
- **`CrossCuttingConfigProcessor`** : `/consumer` est interne à ms-client (pas de nouveau module, bloc
  compose, ni route gateway). **Aucune modif.**
- **`TemplateLoaderTest`** : **129 → 133** (4 nouveaux fichiers ; les autres sont modifiés). Mise à jour
  du compteur de parité.
- Aucun test générateur nouveau (la 2b ne touche aucun processor).

## Gestion d'erreurs

- **Non-admin** → 403 Spring Security sur `/consumer` (jamais d'appel backend).
- **401 backend** → refresh + rejoue une fois (transparent).
- **Refresh échoué / 2e 401** → `SessionExpiredException` → `redirect:/login?expired` ; `login.html`
  affiche « Session expirée, reconnectez-vous » via `${param.expired}`.
- **403 backend** (backstop) → message « Accès refusé par le service (réservé aux administrateurs). »
- **5xx / connexion / réponse illisible** → message « Service indisponible. »
- Messages affichés dans `consumer.html` (`${error}`), aucune stacktrace exposée.

## Tests & vérification

**Test embarqué `GatewayClientTest`** (Mockito, hors-ligne) — scénarios :
1. succès (200) → renvoie le corps ;
2. 401 → refresh OK → retry OK → renvoie le corps **et** vérifie la mise à jour des deux tokens en
   session ;
3. 401 → refresh échoue → `SessionExpiredException` ;
4. 401 → retry encore 401 → `SessionExpiredException` ;
5. 403 → `BackendForbiddenException`.

**`TemplateLoaderTest`** — parité **133**.

**Vérification end-to-end** (port dédié 8077 ; **rebuilder le jar avant de générer** — piège connu du
jar périmé : `mvn test`/`process-resources` rafraîchit `target/classes` mais PAS le jar lancé par
`java -jar`) :
- `clientWebUI=true` → `mvn -pl ms-client -am package` du projet généré **compile ET exécute
  `GatewayClientTest` vert** (sans `-DskipTests` pour valider le test embarqué) ; `docker compose config`
  valide.
- `clientWebUI=false` → `ms-client` absent partout (inchangé vs 2a).
- Test runtime complet (login admin → `/consumer` → agrégat ; expiration → refresh) : **manuel/optionnel**
  (nécessite toute la stack : gateway + ms-auth + keycloak + service-consumer) — noté explicitement NON
  vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- CRUD générique runtime (2c), notifs batch temps réel (2d), chat salon public (2e).
- Refresh **proactif** (on garde le réactif sur 401).
- Pagination / filtrage / transformation de l'agrégat (affichage read-only brut, juste ré-indenté).

## Fichiers touchés (Phase 2b)

**Template (nouveaux) :** `ms-client/src/main/java/.../service/GatewayClient.java`,
`.../web/ConsumerController.java`, `ms-client/src/main/resources/templates/consumer.html`,
`ms-client/src/test/java/.../service/GatewayClientTest.java`.

**Template (modifiés) :** `ms-client/src/main/java/.../service/MsAuthClient.java`,
`.../configuration/SecurityConfig.java`, `ms-client/src/main/resources/templates/home.html`,
`ms-client/src/main/resources/templates/login.html` (message « session expirée » via `${param.expired}`),
`ms-client/pom.xml`, `ms-client/src/main/resources/static/css/app.css`.

**Tests générateur (modifiés) :** `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`
(compteur de parité 129 → 133).
