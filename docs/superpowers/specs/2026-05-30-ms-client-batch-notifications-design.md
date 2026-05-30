# Phase 2d — `ms-client` : notifications batch temps réel

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** ajouter au module `ms-client` une page `/notifications` affichant en temps réel le flux
`BatchNotification` du WebSocket de service-consumer (`/topic/batch`). Le chat (2e) reste hors périmètre.

## Contexte

`ms-client` (Phases 2a–2c) est un BFF Spring Boot MVC (login/logout via ms-auth, session Redis, pages
Spring Security, `GatewayClient` proxy + refresh, CRUD générique). service-consumer expose déjà un
WebSocket :
- `WebSocketConfig` : endpoint `/ws` (SockJS), `setAllowedOriginPatterns("*")`, broker simple `/topic`,
  prefixe applicatif `/app`.
- `BatchNotificationListener` (RabbitListener) republie chaque `BatchNotification` sur `/topic/batch`
  (et `/topic/batch/{jobId}`).
- Payload `BatchNotification { String jobId; BatchJobStatus status; int generatedCount;
  double totalSeconds; String instance; }`.

**Incohérence latente constatée :** la SecurityConfig de service-consumer impose
`anyRequest().authenticated()` (resource server JWT ; seul `/actuator/**` est public), donc le handshake
`/ws` exige aujourd'hui un Bearer. Or la page de référence `batch-notifications.html` s'y connecte
**sans token** (SockJS tokenless) — elle ne pourrait donc pas se connecter sous la sécurité actuelle.
Le gateway, lui, ne fait que router (pas de sécurité propre).

## Décisions de design (validées)

### A. Consommation du WS : `/ws` public + connexion navigateur → gateway

Le navigateur se connecte **directement** au WebSocket de service-consumer via le gateway. On rend
`/ws/**` **public** côté service-consumer (petit changement backend) : les notifications sont de la
**télémétrie non sensible** (jobId/statut/compteurs), le broker `/topic` est en lecture seule
(abonnement uniquement ; aucun `@MessageMapping` n'accepte de publication client), et cela aligne le
backend avec l'hypothèse déjà présente dans `batch-notifications.html`. ms-client ne fait que servir la
page (derrière le login) ; **aucun proxy BFF du WS, aucun token exposé au navigateur**.

L'alternative « proxy BFF WebSocket » (ms-client broker + client STOMP serveur) a été écartée :
beaucoup plus de code (relais, gestion connexions/refresh) pour un flux de télémétrie public.

### B. Accès page : tout utilisateur authentifié

`/notifications` est visible par tout utilisateur connecté (lien d'accueil pour tous). Le flux WS est
public ; le login ms-client suffit. Pas de matcher Spring Security spécifique (relève de
`anyRequest().authenticated()`).

### C. URL gateway côté navigateur

Le navigateur ne peut pas utiliser l'URL interne `gateway.url` (`http://ms-gateway:9000`). On ajoute
`gateway.public-url` (`${GATEWAY_PUBLIC_URL:http://localhost:9000}`), injectée dans la page via un
attribut `data-` (pas d'inline-JS Thymeleaf). Le bloc compose `ms-client` fournit
`GATEWAY_PUBLIC_URL: http://localhost:9000` (URL navigateur, distincte de
`GATEWAY_URL: http://ms-gateway:9000`).

## Architecture & flux

1. `GET ms-client:8090/notifications` (tout authentifié) → `NotificationsController` injecte
   `gatewayPublicUrl` → rend `notifications.html`.
2. Le JS (SockJS + STOMP via CDN) lit l'URL depuis l'attribut `data-`, ouvre
   `SockJS("<gatewayPublicUrl>/service-consumer/ws")`, s'abonne à `/topic/batch`, et **préprend** chaque
   notification (`jobId – status – generatedCount fichiers – totalSeconds s – instance`). Reconnexion
   automatique (`reconnectDelay`).
3. Côté backend, service-consumer permet `/ws/**` (handshake public). Le broker `/topic` reste
   diffusion-serveur uniquement.

Pas de token côté navigateur ; la page reste derrière le login ms-client. ms-client n'a aucun rôle WS
serveur (pas de `GatewayClient` ici).

## Composants

**Module `ms-client` — nouveaux fichiers (2) :**
- `…/client/web/NotificationsController.java` — `GET /notifications` ;
  `@Value("${gateway.public-url}")` injecté ; `model.addAttribute("gatewayPublicUrl", ...)` ; rend
  `notifications`.
- `src/main/resources/templates/notifications.html` — CDN sockjs + stomp ; conteneur portant
  `th:attr="data-gateway-url=${gatewayPublicUrl}"` ; JS : lit `dataset.gatewayUrl`,
  `new SockJS(url + "/service-consumer/ws")`, STOMP `subscribe("/topic/batch")`, prépend les events ;
  reconnexion auto ; back-link Accueil.

**Module `ms-client` — modifiés (2) :**
- `src/main/resources/application.yml` — `+ gateway.public-url: ${GATEWAY_PUBLIC_URL:http://localhost:9000}`
  (dans le bloc `gateway:`).
- `src/main/resources/templates/home.html` — lien « Notifications batch » réel
  `<a th:href="@{/notifications}">…</a>` (visible par tout authentifié ; remplace « à venir — 2d »).
- *(pas de modification de `SecurityConfig` ms-client : `/notifications` relève de
  `anyRequest().authenticated()`.)*

**Module `service-consumer` (backend) — modifié (1) :**
- `…/consumer/configuration/SecurityConfig.java` — `+ .requestMatchers("/ws/**").permitAll()` avant
  `anyRequest().authenticated()`.

**`docker-compose.yml` (template) — modifié :**
- Bloc `ms-client:` — `+ GATEWAY_PUBLIC_URL: http://localhost:9000`.

## Intégration générateur

- **`CrossCuttingConfigProcessor` / `FeatureFilterProcessor`** : **aucune modif**. Tout est statique
  (éditions de templates) ; pas de logique par-resource. La règle `ms-client/` couvre les nouveaux
  fichiers ; service-consumer est toujours présent.
- **`TemplateLoaderTest`** : parité **139 → 141** (2 nouveaux fichiers ms-client ;
  `NotificationsController.java`, `notifications.html`).
- Aucun test générateur nouveau (2d ne touche aucun processor).

## Gestion d'erreurs

- **Non authentifié** → redirection `/login` (entry point existant).
- **WS indisponible / coupé** → la lib STOMP retente (`reconnectDelay`) ; la page reste stable, aucun
  blocage.
- **service-consumer indisponible** → handshake échoue, SockJS retente ; ms-client (page statique) ne
  plante pas.
- **Sécurité** → `/ws/**` public n'autorise que l'abonnement à `/topic` (diffusion serveur) ; aucun
  `@MessageMapping` n'accepte de publication client → pas de surface d'écriture ouverte.

## Tests & vérification

- **Pas de test embarqué** : comportement runtime/navigateur (WebSocket) ; page = JS + controller servant
  une vue (parité avec les pages 2a).
- **`TemplateLoaderTest`** parité **141**.
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=true` → `notifications.html` + `NotificationsController` présents ; `gateway.public-url`
    dans l'`application.yml` généré ; `/ws/**` permitAll dans le `SecurityConfig` de service-consumer
    généré ; `mvn -pl ms-client -am package` compile (les tests embarqués 2b/2c — `GatewayClientTest`,
    `ResourceAccessTest` — restent verts) ; `docker compose config` valide (bloc ms-client avec
    `GATEWAY_PUBLIC_URL`).
  - `clientWebUI=false` → ms-client absent ; le changement `/ws` de service-consumer reste (backend),
    sans effet UI.
  - Flux WS réel (lancer un batch → notif live) : **manuel/optionnel** (stack complète) — noté NON
    vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Chat salon public (2e).
- Filtrage par jobId / historique persistant (flux live éphémère uniquement).
- Proxy BFF du WebSocket (écarté au profit de l'option B).

## Fichiers touchés (Phase 2d)

**Template (nouveaux) :** `ms-client/src/main/java/.../web/NotificationsController.java`,
`ms-client/src/main/resources/templates/notifications.html`.

**Template (modifiés) :** `ms-client/src/main/resources/application.yml`,
`ms-client/src/main/resources/templates/home.html`,
`service-consumer/src/main/java/.../consumer/configuration/SecurityConfig.java`,
`docker-compose.yml` (bloc `ms-client`).

**Tests générateur (modifiés) :** `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`
(parité 139 → 141).
