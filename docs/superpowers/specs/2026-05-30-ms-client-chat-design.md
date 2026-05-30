# Phase 2e — `ms-client` : chat salon public

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** ajouter à `ms-client` un salon de chat public unique en temps réel (WebSocket/STOMP),
réservé aux utilisateurs connectés, avec auteur attribué côté serveur et historique persisté en Redis.
Dernier sous-bloc de la Phase 2.

## Contexte

`ms-client` (Phases 2a–2d) est un BFF Spring Boot MVC (login/logout via ms-auth, session Redis, Spring
Security, CRUD générique, page notifications). Il **n'a pas** `spring-websocket`. Sa SecurityConfig a
CSRF **activé**, session, `anyRequest().authenticated()` (avec `/login`, `/css/**`,
`/actuator/health` en permitAll et `/consumer` en `hasRole("ADMIN")`). service-consumer fournit déjà un
pattern WebSocketConfig (`@EnableWebSocketMessageBroker`, `/ws` SockJS, broker `/topic`, prefixe `/app`)
qu'on réutilise.

Contrairement à 2d (consommer un flux existant), le chat exige un **backend de diffusion** (recevoir les
messages clients et les rediffuser).

## Décisions de design (validées)

### A. Broker WebSocket propre à ms-client

ms-client ajoute `spring-boot-starter-websocket` et héberge **son propre** endpoint `/ws` + broker
`/topic` + `@MessageMapping`. Le navigateur parle à ms-client en **même origine** (cookie de session).
Auto-contenu, authentifié par session, **aucun changement backend**, et n'ouvre **pas** de surface
d'écriture publique sur service-consumer (dont le `/ws` est public depuis 2d, en lecture seule).

### B. Pseudo = utilisateur authentifié (Principal)

Le serveur attribue chaque message au `Principal.getName()` de la session ; le client n'envoie que le
texte (un éventuel `user` côté client est ignoré). Non spoofable, cohérent avec le login ms-client.

### C. Historique persisté en Redis, rendu à l'ouverture de page

Les **50 derniers** messages sont stockés dans une liste Redis (`chat:history`). Le `GET /chat` les lit
et les **rend côté serveur** dans la page ; le WebSocket ne gère que les **nouveaux** messages (plus
simple qu'un replay sur abonnement STOMP). ms-client a déjà `spring-boot-starter-data-redis`
(StringRedisTemplate auto-configuré).

### D. Auth + CSRF du WebSocket

`/ws/**` reste sous `anyRequest().authenticated()` (seuls les connectés chattent ; le handshake porte la
session → Principal disponible). On **désactive CSRF sur `/ws/**` uniquement**
(`.csrf(c -> c.ignoringRequestMatchers("/ws/**"))`) — sinon les transports XHR de SockJS renverraient 403.

## Architecture & flux

1. `GET /chat` (tout authentifié) → `ChatController` lit `chatHistory.recent()` (Redis) → modèle
   `history` → rend `chat.html` (messages existants rendus côté serveur).
2. Le JS (SockJS + STOMP, CDN) se connecte au `/ws` **de ms-client** (relatif, même origine), s'abonne à
   `/topic/chat`.
3. Envoi : le navigateur poste `{text}` à `/app/chat.send` →
   `ChatController.@MessageMapping("/chat.send")` reçoit `(ChatMessage in, Principal p)` → construit
   `ChatMessage m = new ChatMessage(p.getName(), in.text())` → `chatHistory.add(m)` (RPUSH + LTRIM 50) →
   `@SendTo("/topic/chat")` renvoie `m` à tous les abonnés.
4. Chaque abonné ajoute le message reçu à la liste.

**Limite assumée :** `SimpleBroker` en mémoire → diffusion live mono-instance. L'historique (Redis) est
partagé entre instances, mais la diffusion temps réel multi-instance nécessiterait un relais (hors
périmètre).

## Composants (module `ms-client`)

**Nouveaux fichiers (6) :**
- `…/client/configuration/WebSocketConfig.java` — `@EnableWebSocketMessageBroker` ;
  `registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS()` ;
  `enableSimpleBroker("/topic")` ; `setApplicationDestinationPrefixes("/app")`.
- `…/client/dto/ChatMessage.java` — `record ChatMessage(String user, String text)` (entrant : `user`
  ignoré ; sortant : `user` = Principal).
- `…/client/service/ChatHistory.java` — `@Service` sur `StringRedisTemplate` + `ObjectMapper` :
  `add(ChatMessage)` (RPUSH JSON sur `chat:history` + LTRIM aux 50 derniers, best-effort) ;
  `recent() → List<ChatMessage>` (LRANGE + désérialisation, best-effort → liste vide si erreur).
- `…/client/web/ChatController.java` — `@Controller` : `@GetMapping("/chat")` (modèle `history` ←
  `chatHistory.recent()` → vue `chat`) ET `@MessageMapping("/chat.send") @SendTo("/topic/chat")`
  (`(ChatMessage in, Principal p)` → `new ChatMessage(p.getName(), in.text())` → `chatHistory.add(m)` →
  return `m`).
- `src/main/resources/templates/chat.html` — CDN sockjs + stomp ; rend `${history}` (initial) ; input +
  bouton « Envoyer » ; JS : SockJS vers `/ws` (relatif, même origine), subscribe `/topic/chat` (append),
  send `/app/chat.send` `{text}` ; reconnexion auto + bandeau d'état ; back-link Accueil.
- `src/test/java/…/client/web/ChatControllerTest.java` — test embarqué hors-ligne (Mockito) :
  `ChatHistory` mocké, `Principal` stub ; vérifie que `send` renvoie
  `ChatMessage{user = principal.name, text = in.text}` (auteur serveur, **pas** le `user` client) et que
  `chatHistory.add(...)` est appelé.

**Fichiers modifiés (3) :**
- `pom.xml` — `+ spring-boot-starter-websocket`.
- `…/client/configuration/SecurityConfig.java` — `+ .csrf(c -> c.ignoringRequestMatchers("/ws/**"))`
  (le `/ws` reste authentifié via `anyRequest().authenticated()`).
- `src/main/resources/templates/home.html` — lien « Chat » réel `<a th:href="@{/chat}">Chat</a>`
  (visible par tout authentifié ; remplace « à venir — 2e »).

## Intégration générateur

- **`CrossCuttingConfigProcessor` / `FeatureFilterProcessor`** : **aucune modif** (tout est dans
  ms-client ; pas de logique par-resource).
- **`TemplateLoaderTest`** : parité **141 → 147** (6 nouveaux fichiers).
- Aucun test générateur nouveau (2e ne touche aucun processor).

## Gestion d'erreurs

- **Non authentifié** → `/chat` et le handshake `/ws` redirigent vers `/login` (entry point existant).
- **Redis indisponible** : `recent()` → liste vide (page ouverte vide) ; `add()` capture l'erreur et
  **diffuse quand même** le message live (historique best-effort, ne bloque pas le chat).
- **WS coupé** : reconnexion auto (`reconnectDelay`) ; bandeau « déconnecté… ».
- **Message vide** : garde côté JS (pas d'envoi si champ vide) ; côté serveur, texte vide diffusé tel
  quel sans planter (démo, pas de validation lourde).
- **Sécurité** : seuls les connectés se connectent au `/ws` ms-client ; auteur imposé par le serveur
  (Principal) ; CSRF ignoré uniquement sur `/ws/**`.

## Tests & vérification

- **Test embarqué** `ChatControllerTest` (Mockito, hors-ligne) : attribution serveur du pseudo + appel
  `chatHistory.add`.
- **`TemplateLoaderTest`** parité **147**.
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=true` → les 6 nouveaux fichiers présents ; `spring-boot-starter-websocket` dans le
    pom généré ; `mvn -pl ms-client -am package` du projet généré **compile ET exécute les tests
    embarqués verts** (`ChatControllerTest`, plus `GatewayClientTest`/`ResourceAccessTest` de 2b/2c) ;
    `docker compose config` valide.
  - `clientWebUI=false` → ms-client absent.
  - Chat temps réel (2 navigateurs → échange de messages, historique au rechargement) :
    **manuel/optionnel** (stack + Redis) — noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Diffusion live multi-instance (relais STOMP / Redis pub-sub) — l'historique est partagé via Redis, pas
  la diffusion temps réel.
- Salons multiples, messages privés, présence/typing, modération, pagination au-delà des 50 derniers.

## Fichiers touchés (Phase 2e)

**Template (nouveaux) :** `ms-client/src/main/java/.../configuration/WebSocketConfig.java`,
`.../dto/ChatMessage.java`, `.../service/ChatHistory.java`, `.../web/ChatController.java`,
`ms-client/src/main/resources/templates/chat.html`,
`ms-client/src/test/java/.../web/ChatControllerTest.java`.

**Template (modifiés) :** `ms-client/pom.xml`,
`ms-client/src/main/java/.../configuration/SecurityConfig.java`,
`ms-client/src/main/resources/templates/home.html`.

**Tests générateur (modifiés) :** `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`
(parité 141 → 147).
