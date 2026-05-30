# Phase 2e — `ms-client` chat salon public — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à `ms-client` un salon de chat public temps réel (STOMP broker propre à ms-client), réservé aux connectés, auteur attribué côté serveur (Principal), historique des 50 derniers messages persisté en Redis.

**Architecture:** ms-client ajoute `spring-websocket` : endpoint `/ws` + broker `/topic` + `ChatController` (`@MessageMapping("/chat.send")` → `@SendTo("/topic/chat")`). Le navigateur parle à ms-client en même origine (session). `ChatHistory` (StringRedisTemplate) stocke/relit les 50 derniers messages, rendus au `GET /chat`. CSRF ignoré sur `/ws/**`.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring WebSocket (STOMP), Spring Security 6, Spring Data Redis, Thymeleaf, SockJS+STOMP (CDN) ; tests embarqués JUnit5 + Mockito.

---

## Spec
`docs/superpowers/specs/2026-05-30-ms-client-chat-design.md`

## Carte des fichiers

Racine module : `src/main/resources/templates/ms-platform/ms-client/`. Package `com.mr486.msplatform.client`.

**Nouveaux (6) :**
| Fichier | Responsabilité |
|---------|----------------|
| `…/client/configuration/WebSocketConfig.java` | `@EnableWebSocketMessageBroker` : endpoint `/ws`, broker `/topic`, prefixe `/app` |
| `…/client/dto/ChatMessage.java` | `record ChatMessage(String user, String text)` |
| `…/client/service/ChatHistory.java` | Historique Redis (50 derniers), best-effort |
| `…/client/web/ChatController.java` | `GET /chat` + `@MessageMapping("/chat.send")` |
| `src/main/resources/templates/chat.html` | Vue : historique + saisie + flux live |
| `src/test/java/.../client/web/ChatControllerTest.java` | Test embarqué (attribution auteur + persistance) |

**Modifiés (3) :**
| Fichier | Changement |
|---------|-----------|
| `pom.xml` | `+ spring-boot-starter-websocket` |
| `…/client/configuration/SecurityConfig.java` | `+ .csrf(... ignoringRequestMatchers("/ws/**"))` |
| `src/main/resources/templates/home.html` | lien « Chat » réel |

**Générateur :** aucun processor touché. Seule la parité `TemplateLoaderTest` change (141 → 146 → 147).

## Conventions
- Code/templates NON compilés par le générateur → **oracle = `mvn package` du projet généré** (Task 3), qui compile ms-client ET exécute les tests embarqués.
- **Commits verts** : la parité est mise à jour dans le commit qui ajoute des fichiers.
- Libs sockjs/stomp via **CDN** (cohérent avec 2d). Le `/ws` du chat est celui de **ms-client** (relatif, même origine) — pas le gateway.
- Sécurité XSS de la vue : l'historique est rendu via `th:text` (échappé) ; le live est inséré via `textContent`/`createTextNode` (pas d'`innerHTML`).

---

## Task 1 : Backend chat (broker + history + controller + test)

**Files:**
- Modify: `src/main/resources/templates/ms-platform/ms-client/pom.xml`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/WebSocketConfig.java`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/SecurityConfig.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/dto/ChatMessage.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/ChatHistory.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/ChatController.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/web/ChatControllerTest.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `pom.xml` — dépendance websocket**

Après la ligne `spring-boot-starter-actuator`, ajouter :
```xml
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>
```

- [ ] **Step 2: `WebSocketConfig.java`**

```java
package com.mr486.msplatform.client.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

- [ ] **Step 3: `SecurityConfig.java` — ignorer CSRF sur `/ws/**`**

Remplacer :
```java
        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
```
par :
```java
        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .authorizeHttpRequests(auth -> auth
```

- [ ] **Step 4: `ChatMessage.java`**

```java
package com.mr486.msplatform.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(String user, String text) {}
```

- [ ] **Step 5: `ChatHistory.java`**

```java
package com.mr486.msplatform.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.client.dto.ChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Historique du chat persisté en Redis : les 50 derniers messages (best-effort). */
@Service
public class ChatHistory {

    private static final String KEY = "chat:history";
    private static final long MAX = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatHistory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void add(ChatMessage message) {
        try {
            String json = mapper.writeValueAsString(message);
            redis.opsForList().rightPush(KEY, json);
            redis.opsForList().trim(KEY, -MAX, -1);
        } catch (Exception ignored) {
            // historique best-effort : ne bloque pas la diffusion live
        }
    }

    public List<ChatMessage> recent() {
        List<ChatMessage> result = new ArrayList<>();
        try {
            List<String> raw = redis.opsForList().range(KEY, 0, -1);
            if (raw != null) {
                for (String json : raw) {
                    result.add(mapper.readValue(json, ChatMessage.class));
                }
            }
        } catch (Exception ignored) {
            // Redis indisponible : page ouverte vide
        }
        return result;
    }
}
```

- [ ] **Step 6: `ChatController.java`**

```java
package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.dto.ChatMessage;
import com.mr486.msplatform.client.service.ChatHistory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class ChatController {

    private final ChatHistory chatHistory;

    public ChatController(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("history", chatHistory.recent());
        return "chat";
    }

    @MessageMapping("/chat.send")
    @SendTo("/topic/chat")
    public ChatMessage send(ChatMessage in, Principal principal) {
        ChatMessage message = new ChatMessage(principal.getName(), in.text());
        chatHistory.add(message);
        return message;
    }
}
```

- [ ] **Step 7: `ChatControllerTest.java`** (sous `src/test/java/...`)

```java
package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.dto.ChatMessage;
import com.mr486.msplatform.client.service.ChatHistory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void send_attributes_author_from_principal_and_persists() {
        ChatHistory history = Mockito.mock(ChatHistory.class);
        ChatController controller = new ChatController(history);
        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("alice");

        // le client tente d'usurper "bob" — seul le texte doit être retenu, l'auteur vient du Principal
        ChatMessage out = controller.send(new ChatMessage("bob", "hello"), principal);

        assertThat(out.user()).isEqualTo("alice");
        assertThat(out.text()).isEqualTo("hello");
        verify(history).add(out);
    }
}
```

- [ ] **Step 8: Parité `TemplateLoaderTest` (→ 146)**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 141 ... but was: 146` (5 nouveaux fichiers : WebSocketConfig, ChatMessage, ChatHistory, ChatController, ChatControllerTest).
Remplacer dans `TemplateLoaderTest.java` `hasSize(141)` → `hasSize(146)` (nombre observé).

- [ ] **Step 9: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/pom.xml \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/WebSocketConfig.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/SecurityConfig.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/dto/ChatMessage.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/ChatHistory.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/ChatController.java \
        src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/web/ChatControllerTest.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client chat backend (STOMP broker + Redis history + controller)"
```

---

## Task 2 : UI chat + lien d'accueil + parité 147

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/chat.html`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `chat.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Chat — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
  <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/}">← Accueil</a></p>
  <h1>Chat</h1>
  <p id="status" class="info">Connexion…</p>
  <ul id="messages">
    <li th:each="m : ${history}"><strong th:text="${m.user}">user</strong>: <span th:text="${m.text}">text</span></li>
  </ul>
  <form id="chat-form">
    <input type="text" id="chat-text" placeholder="Votre message" autocomplete="off" required/>
    <button type="submit">Envoyer</button>
  </form>
</main>
<script>
  const list = document.getElementById('messages');
  const statusEl = document.getElementById('status');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-text');
  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 2000,
    onConnect: () => {
      statusEl.textContent = 'Connecté';
      client.subscribe('/topic/chat', message => {
        const m = JSON.parse(message.body);
        const li = document.createElement('li');
        const who = document.createElement('strong');
        who.textContent = m.user;
        li.appendChild(who);
        li.appendChild(document.createTextNode(': ' + m.text));
        list.appendChild(li);
      });
    },
    onWebSocketClose: () => { statusEl.textContent = 'Déconnecté — reconnexion…'; }
  });
  client.activate();
  form.addEventListener('submit', e => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    client.publish({ destination: '/app/chat.send', body: JSON.stringify({ text }) });
    input.value = '';
  });
</script>
</body>
</html>
```

- [ ] **Step 2: `home.html` — lien chat réel**

Remplacer :
```html
    <li>Chat <em>(à venir — 2e)</em></li>
```
par :
```html
    <li><a th:href="@{/chat}">Chat</a></li>
```

- [ ] **Step 3: Parité `TemplateLoaderTest` (→ 147)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 146 ... but was: 147` (1 nouveau : chat.html).
Remplacer `hasSize(146)` → `hasSize(147)`.

- [ ] **Step 4: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/chat.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client /chat live room UI + nav link"
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1: Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 147).

- [ ] **Step 2: Tuer un éventuel générateur (commande séparée)**

Run (sandbox désactivé) : `pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null; sleep 2; pgrep -af '[g]enerator-v5' || echo clean`
Expected: `clean`.

- [ ] **Step 3: Lancer le générateur (commande séparée, arrière-plan, sandbox désactivé, SANS pkill)**

```bash
java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 > /tmp/genapp.log 2>&1
```
Puis (commande séparée) :
```bash
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
grep -q "Tomcat started on port 8077" /tmp/genapp.log && echo STARTED
```
Expected: `STARTED`.

- [ ] **Step 4: Générer `clientWebUI=true` + vérifier la 2e**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":true,"clientWebUI":true}}' \
  -o /tmp/refe.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refex && mkdir -p /tmp/refex && unzip -q /tmp/refe.zip -d /tmp/refex && echo UNZIPPED
echo "=== fichiers 2e ==="
ls /tmp/refex/ms-platform/ms-client/src/main/java/com/acme/shop/client/configuration/WebSocketConfig.java \
   /tmp/refex/ms-platform/ms-client/src/main/java/com/acme/shop/client/service/ChatHistory.java \
   /tmp/refex/ms-platform/ms-client/src/main/java/com/acme/shop/client/web/ChatController.java \
   /tmp/refex/ms-platform/ms-client/src/main/resources/templates/chat.html \
   /tmp/refex/ms-platform/ms-client/src/test/java/com/acme/shop/client/web/ChatControllerTest.java 2>&1
echo -n "websocket dep="; grep -c 'spring-boot-starter-websocket' /tmp/refex/ms-platform/ms-client/pom.xml
echo -n "csrf /ws ignore="; grep -c 'ignoringRequestMatchers("/ws/\*\*")' /tmp/refex/ms-platform/ms-client/src/main/java/com/acme/shop/client/configuration/SecurityConfig.java
echo -n "home chat link="; grep -c '@{/chat}' /tmp/refex/ms-platform/ms-client/src/main/resources/templates/home.html
```
Expected : `HTTP=200`, `UNZIPPED`, les 5 fichiers listés, puis `1`, `1`, `1`.

- [ ] **Step 5: Compiler le module généré + exécuter les tests embarqués (sans skipTests)**

```bash
cd /tmp/refex/ms-platform && mvn -pl ms-client -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|ChatControllerTest|GatewayClientTest|ResourceAccessTest|ERROR.*\.java' | head -25
```
Expected: `ChatControllerTest` (1) + `GatewayClientTest` (7) + `ResourceAccessTest` (4) verts, puis `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 6: Compose valide + `clientWebUI=false` (absence) + arrêt**

```bash
cd /tmp/refex/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
cd /home/mr486/Developpement/Projets/GestoMS
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refe0.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refe0x && mkdir -p /tmp/refe0x && unzip -q /tmp/refe0.zip -d /tmp/refe0x
echo "=== ms-client absent ? ==="; ls /tmp/refe0x/ms-platform/ms-client 2>&1
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; `HTTP=200` ; ms-client « No such file or directory » ; arbre git propre.

---

## Recovery
- `git log --oneline -4` — commits passés (backend chat ; UI chat).
- `grep hasSize src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` → `147` quand Tasks 1+2 faites.
- `grep -rl 'EnableWebSocketMessageBroker' src/main/resources/templates/ms-platform/ms-client/` → présent si Task 1 faite.
- `mvn test` SUCCESS → générateur vert ; oracle module = `mvn -pl ms-client -am package` du projet généré (Task 3, exécute `ChatControllerTest`).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Diffusion live multi-instance (relais STOMP/Redis pub-sub), salons multiples, messages privés, présence/typing, modération, pagination au-delà de 50.
