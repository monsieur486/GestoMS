# Chat — horodatage + indicateur de frappe (Lot 1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Afficher un horodatage « lun 14:30 » sur chaque message du chat de `ms-webui`, et un indicateur de frappe (points animés) à côté du nom des utilisateurs qui tapent dans la colonne « Connectés ».

**Architecture:** Le chat utilise STOMP/SockJS (broker simple `/topic`, préfixe `/app`). On ajoute un champ `timestamp` (epoch millis, posé côté serveur) à `ChatMessage` ; le formatage « jour abrégé + HH:mm » est fait côté JS (un seul chemin pour l'historique Thymeleaf et les messages live). La frappe passe par un nouveau canal `/topic/typing` (signal throttlé, signé par le Principal, non persisté) ; le client marque l'utilisateur comme « écrit » et l'efface après ~2,5 s, l'indicateur étant rendu dans la sidebar de présence. **Aucun changement de broker** (les messages privés du Lot 2 viendront ensuite).

**Tech Stack:** Java 17, Spring Boot WebSocket/STOMP, Thymeleaf, Bootstrap 5.3, SockJS + @stomp/stompjs, JUnit 5 + AssertJ + Mockito.

---

## Rappels d'environnement (pièges)

- **Template Java NON compilé par `mvn test`** (ressources) → garde générateur = `mvn test` (layout guards + `TemplateLoaderTest`) ; vraie validation = générer + compiler `ms-webui` (Task 3).
- **`ChatMessage` passe de 2 à 3 composants** → toute construction `new ChatMessage(...)` doit être mise à jour (sinon échec de compilation du module généré, attrapé en Task 3). Sites connus : `ChatController.send()` et `ChatControllerTest`.
- **Layout Java** ≤120 cols, 4 espaces, un import/ligne, javadoc français.
- **Compteur `TemplateLoaderTest`** : ce Lot n'ajoute AUCUN fichier template (on modifie l'existant) → compteur **inchangé** (ne pas y toucher).
- **Serveur zombie sur :8077** : tuer les anciens `java -jar` AVANT toute génération (`pkill -f springboot-platform-generator` ; vérifier `ps -eo args | grep "[s]pringboot-platform-generator"`). Un zombie sert l'ancien jar et produit une sortie périmée.

---

## File Structure

Sous `src/main/resources/templates/ms-platform/ms-webui/` :
- Modify `…/webui/dto/ChatMessage.java` — ajoute `long timestamp`.
- Modify `…/webui/web/ChatController.java` — `send()` pose le timestamp ; nouveau `@MessageMapping("/chat.typing")`.
- Modify `…/webui/src/test/java/…/web/ChatControllerTest.java` — 3-arg `ChatMessage`, assert `timestamp>0`, test `typing()`.
- Modify `…/webui/src/main/resources/templates/chat.html` — `.time` sur les messages, formatage JS, frappe (publish + receive + indicateur sidebar).
- Modify `…/webui/src/main/resources/static/css/app.css` — styles `.time` + `.typing-indicator`.

---

## Task 1 : Backend — timestamp sur ChatMessage + endpoint de frappe

**Files:**
- Modify: `src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/dto/ChatMessage.java`
- Modify: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/web/ChatController.java`
- Test: `…/ms-webui/src/test/java/com/mr486/msplatform/webui/web/ChatControllerTest.java`

- [ ] **Step 1 : Mettre à jour le test d'abord (TDD)**

Remplacer le contenu de `ChatControllerTest.java` par :

```java
package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.dto.ChatMessage;
import com.mr486.msplatform.webui.service.ChatHistory;
import com.mr486.msplatform.webui.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ChatController} : l'auteur vient du Principal, le serveur
 * pose un timestamp, et l'événement de frappe renvoie l'utilisateur authentifié.
 */
class ChatControllerTest {

    @Test
    void send_attributes_author_and_server_timestamp() {
        ChatHistory history = Mockito.mock(ChatHistory.class);
        PresenceService presence = Mockito.mock(PresenceService.class);
        ChatController controller = new ChatController(history, presence);
        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("alice");

        // le client tente d'usurper "bob" et d'imposer un timestamp 0 — les deux sont écrasés serveur
        ChatMessage out = controller.send(new ChatMessage("bob", "hello", 0), principal);

        assertThat(out.user()).isEqualTo("alice");
        assertThat(out.text()).isEqualTo("hello");
        assertThat(out.timestamp()).isGreaterThan(0);
        verify(history).add(out);
    }

    @Test
    void typing_returns_authenticated_username() {
        ChatController controller = new ChatController(
                Mockito.mock(ChatHistory.class), Mockito.mock(PresenceService.class));
        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("alice");

        assertThat(controller.typing(principal)).containsEntry("user", "alice");
    }
}
```

- [ ] **Step 2 : Ajouter `timestamp` à `ChatMessage`**

Remplacer le record par :

```java
package com.mr486.msplatform.webui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message de chat échangé via STOMP : auteur ({@code user}), contenu ({@code text})
 * et horodatage serveur en millisecondes epoch ({@code timestamp}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(String user, String text, long timestamp) {}
```

(Jackson lit/écrit le record à 3 composants ; les anciens messages d'historique sans champ
`timestamp` se désérialisent avec `timestamp = 0` — le frontend ne les horodate pas.)

- [ ] **Step 3 : `ChatController` — poser le timestamp dans `send()` + endpoint `typing`**

Ajouter l'import `java.util.Map` (à côté de `java.util.List`).

Dans `send()`, remplacer la construction du message par :
```java
        ChatMessage message = new ChatMessage(principal.getName(), in.text(), System.currentTimeMillis());
```

Ajouter la méthode (après `send`) :
```java
    /**
     * Diffuse un signal de frappe portant l'utilisateur authentifié (non persisté).
     *
     * @param principal le Principal de l'expéditeur
     * @return une map {@code {"user": <nom>}} diffusée sur {@code /topic/typing}
     */
    @MessageMapping("/chat.typing")
    @SendTo("/topic/typing")
    public Map<String, String> typing(Principal principal) {
        return Map.of("user", principal.getName());
    }
```

(Les imports `MessageMapping`, `SendTo`, `Principal` existent déjà ; ajouter seulement `java.util.Map`.)

- [ ] **Step 4 : Layout + chargement (compteur inchangé)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD SUCCESS|BUILD FAILURE" | tail -1
```
Expected : BUILD SUCCESS, 125 (aucun nouveau fichier template → `TemplateLoaderTest` reste à 190, ne pas le toucher). Le `ChatControllerTest` est un test template (non exécuté par le générateur) — validé en Task 3.

- [ ] **Step 5 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/dto/ChatMessage.java \
        src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/web/ChatController.java \
        src/main/resources/templates/ms-platform/ms-webui/src/test/java/com/mr486/msplatform/webui/web/ChatControllerTest.java
git commit -m "$(cat <<'EOF'
feat(ms-webui): server timestamp on chat messages + typing channel

ChatMessage gains a server-set epoch-millis timestamp; ChatController.send
stamps it. New @MessageMapping("/chat.typing") broadcasts {user} on
/topic/typing (Principal-signed, not persisted). Tests updated.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 : Frontend — horodatage affiché + indicateur de frappe

**Files:**
- Modify: `…/ms-webui/src/main/resources/templates/chat.html`
- Modify: `…/ms-webui/src/main/resources/static/css/app.css`

- [ ] **Step 1 : `chat.html` — ajouter le `.time` aux messages d'historique**

Remplacer le bloc `th:each` de l'historique par (ajout d'un span `.time` avec `data-ts`) :

```html
        <div th:each="m : ${history}" class="chat-message"
             th:classappend="${m.user == username} ? 'own' : 'other'">
          <span class="sender" th:if="${m.user != username}" th:text="${m.user}">user</span>
          <div class="bubble" th:text="${m.text}">text</div>
          <span class="time" th:attr="data-ts=${m.timestamp}"></span>
        </div>
```

- [ ] **Step 2 : `chat.html` — remplacer tout le bloc `<script th:inline="javascript">…</script>`**

Remplacer l'intégralité du `<script th:inline="javascript">` (de cette balise jusqu'à `</script>`) par :

```html
<script th:inline="javascript">
  const ME = /*[[${username}]]*/ '';
  const messages = document.getElementById('messages');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-text');
  const presenceList = document.getElementById('presenceList');
  const presenceCount = document.getElementById('presenceCount');
  const presenceCountBtn = document.getElementById('presenceCountBtn');

  const DAYS = ['dim', 'lun', 'mar', 'mer', 'jeu', 'ven', 'sam'];

  function formatTs(ts) {
    if (!ts || ts <= 0) return '';
    const d = new Date(ts);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return DAYS[d.getDay()] + ' ' + hh + ':' + mm;
  }

  function appendMessage(m) {
    const own = m.user === ME;
    const div = document.createElement('div');
    div.className = 'chat-message ' + (own ? 'own' : 'other');
    if (!own) {
      const sender = document.createElement('span');
      sender.className = 'sender';
      sender.textContent = m.user;
      div.appendChild(sender);
    }
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = m.text;
    div.appendChild(bubble);
    const time = document.createElement('span');
    time.className = 'time';
    time.textContent = formatTs(m.timestamp);
    div.appendChild(time);
    messages.appendChild(div);
    messages.scrollTop = messages.scrollHeight;
  }

  let lastRoster = [];
  const typingUsers = new Set();
  const typingTimers = {};

  function drawPresence() {
    presenceList.innerHTML = '';
    lastRoster.forEach(u => {
      const li = document.createElement('li');
      li.className = 'presence-item';
      const dot = document.createElement('span');
      dot.className = 'online-dot';
      li.appendChild(dot);
      li.appendChild(document.createTextNode(u === ME ? u + ' (vous)' : u));
      if (typingUsers.has(u) && u !== ME) {
        const t = document.createElement('span');
        t.className = 'typing-indicator';
        t.textContent = '…';
        t.title = 'écrit…';
        li.appendChild(t);
      }
      presenceList.appendChild(li);
    });
    presenceCount.textContent = lastRoster.length;
    presenceCountBtn.textContent = lastRoster.length;
  }

  function renderPresence(users) {
    lastRoster = users;
    drawPresence();
  }

  function markTyping(user) {
    if (user === ME) return;
    typingUsers.add(user);
    clearTimeout(typingTimers[user]);
    typingTimers[user] = setTimeout(() => { typingUsers.delete(user); drawPresence(); }, 2500);
    drawPresence();
  }

  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 2000,
    onConnect: () => {
      client.subscribe('/topic/chat', message => appendMessage(JSON.parse(message.body)));
      client.subscribe('/topic/presence', message => renderPresence(JSON.parse(message.body)));
      client.subscribe('/topic/typing', message => markTyping(JSON.parse(message.body).user));
      client.publish({ destination: '/app/presence.hello', body: '{}' });
    }
  });
  client.activate();

  form.addEventListener('submit', e => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    client.publish({ destination: '/app/chat.send', body: JSON.stringify({ text }) });
    input.value = '';
  });

  let lastTypingSent = 0;
  input.addEventListener('input', () => {
    if (!input.value.trim()) return;
    const now = Date.now();
    if (now - lastTypingSent > 1500) {
      lastTypingSent = now;
      client.publish({ destination: '/app/chat.typing', body: '{}' });
    }
  });

  document.querySelectorAll('.chat-message .time[data-ts]').forEach(el => {
    el.textContent = formatTs(Number(el.dataset.ts));
  });
  messages.scrollTop = messages.scrollHeight;
</script>
```

- [ ] **Step 3 : `app.css` — styles `.time` + `.typing-indicator`**

Dans le bloc `/* ── Chat ── */`, ajouter après la règle `.chat-message.own .bubble { … }` :
```css
.chat-message .time { color: #64748b; font-size: .7rem; margin-top: .15rem; }
```

Dans le bloc `/* ── Présence ── */`, ajouter après la règle `.online-dot { … }` :
```css
.typing-indicator {
  margin-left: .4rem; color: #a78bfa; font-weight: 700; letter-spacing: 1px;
  animation: typing-blink 1s steps(1, end) infinite;
}
@keyframes typing-blink { 50% { opacity: .25; } }
```

- [ ] **Step 4 : Layout + chargement**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD SUCCESS|BUILD FAILURE" | tail -1
```
Expected : BUILD SUCCESS, 125 (chat.html/app.css non compilés ni comptés ; aucun fichier ajouté).

- [ ] **Step 5 : Sanity-grep des marqueurs**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui/src/main/resources
grep -nE "formatTs|data-ts|/topic/typing|/app/chat.typing|typing-indicator|markTyping" templates/chat.html
grep -nE "\.chat-message \.time|typing-indicator|typing-blink" static/css/app.css
```
Expected : tous les marqueurs présents (chat.html : formatTs, data-ts, les 2 destinations typing, typing-indicator, markTyping ; app.css : .time, typing-indicator, keyframes).

- [ ] **Step 6 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/resources/templates/chat.html \
        src/main/resources/templates/ms-platform/ms-webui/src/main/resources/static/css/app.css
git commit -m "$(cat <<'EOF'
feat(ms-webui): show "lun HH:mm" timestamps + typing indicator in sidebar

Messages display a server timestamp formatted as "lun 14:30" (3-letter day,
no dot, 24h) — single JS formatter for history (data-ts) and live messages.
Typing: throttled /app/chat.typing on input; receivers mark the user as
typing for ~2.5s and show animated dots next to their name in "Connectés".

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 : Vérification de bout en bout

- [ ] **Step 1 : Générateur vert + tuer les zombies + générer**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD SUCCESS|BUILD FAILURE" | tail -1
ps -eo args | grep "[s]pringboot-platform-generator" | awk '{print "ZOMBIE:"$0}'   # doit être vide
pkill -f springboot-platform-generator 2>/dev/null; sleep 1
mvn -q clean package -DskipTests
nohup java -jar target/*.jar --server.port=8077 >/tmp/tt-srv.log 2>&1 &
sleep 12
curl -sf http://localhost:8077/ >/dev/null && echo "server UP" || { echo "server DOWN"; tail -5 /tmp/tt-srv.log; }
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/tt.zip
rm -rf /tmp/tt && unzip -q /tmp/tt.zip -d /tmp/tt
kill %1 2>/dev/null; pkill -f "server.port=8077" 2>/dev/null; true
```
Expected : générateur BUILD SUCCESS ; AUCUN zombie ; server UP ; ZIP généré.

- [ ] **Step 2 : Compiler `ms-webui` généré + lancer `ChatControllerTest` sur la sortie réelle**

```bash
cd /tmp/tt/ms-platform
mvn -pl ms-webui -am test -Dtest=ChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 \
  | grep -E "Running com|Tests run:|BUILD SUCCESS|BUILD FAILURE|COMPILATION"
```
Expected : `ms-webui` compile ; `ChatControllerTest` 2 tests OK ; BUILD SUCCESS. (Si COMPILATION ERROR sur un `new ChatMessage(...)` non mis à jour, corriger le site fautif dans le template, régénérer, refaire.)

- [ ] **Step 3 : Vérifier les artefacts générés**

```bash
cd /tmp/tt/ms-platform/ms-webui
grep -n "long timestamp" src/main/java/com/mr486/msplatform/webui/dto/ChatMessage.java
grep -n "chat.typing\|System.currentTimeMillis" src/main/java/com/mr486/msplatform/webui/web/ChatController.java
grep -nE "formatTs|data-ts|/topic/typing|typing-indicator" src/main/resources/templates/chat.html | head
```
Expected : timestamp dans le record ; `chat.typing` + `System.currentTimeMillis` dans le contrôleur ; marqueurs JS présents dans chat.html.

- [ ] **Step 4 : (Manuel, Docker) Vérification fonctionnelle**

> Documenté pour l'utilisateur — non exécutable ici.
> ```bash
> cd <plateforme générée webUI:true> && docker compose up -d --build ms-webui
> ```
> - Envoyer des messages → chaque bulle affiche « lun 14:30 » (jour fr 3 lettres, heure 24h) ; l'historique aussi.
> - Avec **deux** comptes : quand l'un tape dans le champ, l'autre voit des **points animés** à côté de son nom dans « Connectés », qui disparaissent ~2,5 s après l'arrêt de la frappe. On ne voit pas sa propre frappe.

---

## Self-Review

- **Couverture design** : timestamp serveur (Task 1 ChatMessage+send), format « lun HH:mm » 3-lettres-sans-point (Task 2 `formatTs`/`DAYS`), un seul chemin de formatage historique+live (Task 2 data-ts + appendMessage), canal frappe `/topic/typing` (Task 1), publish throttlé + receive + auto-clear 2,5 s + indicateur sidebar (Task 2). ✅
- **Pas de placeholder** : tout le code (record, contrôleur, test, HTML/JS, CSS) fourni intégralement.
- **Cohérence des types** : `ChatMessage(String,String,long)` utilisé identiquement dans send/test/JSON ; `typing(Principal)` → `Map<String,String>` cohérent contrôleur↔test↔JS (`JSON.parse(body).user`) ; destinations `/app/chat.typing` ↔ `/topic/typing` cohérentes ; `formatTs`/`drawPresence`/`markTyping`/`renderPresence` cohérents dans chat.html.
- **Pièges couverts** : arité `ChatMessage` (Task 1 met à jour les 2 sites connus ; Task 3 compile pour attraper tout site oublié) ; compteur inchangé ; zombie tué avant génération ; template non compilé → e2e compile.

## Notes pour l'exécutant

- Le timestamp est posé **serveur** (`System.currentTimeMillis()` — Java normal, autorisé dans le template), jamais client, pour la cohérence entre clients.
- L'indicateur de frappe survit aux re-renders de présence car `drawPresence()` lit `typingUsers` + `lastRoster` ; présence et frappe l'appellent toutes deux.
- Pas de changement de `WebSocketConfig` : `/topic/typing` passe par le broker simple existant. Les messages privés (Lot 2) ajouteront `/queue` + user destinations.
