# Chat — messages privés éphémères (Lot 2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre une conversation privée 1-à-1 dans le chat de `ms-webui` : cliquer un utilisateur dans « Connectés » ouvre une modale de DM ; les messages sont routés en direct via les *user destinations* STOMP, non persistés.

**Architecture:** On étend le broker STOMP simple avec `/queue` + un préfixe utilisateur `/user`. Un nouveau `@MessageMapping("/chat.private")` reçoit `{to, text}`, signe l'expéditeur depuis le `Principal` et route via `SimpMessagingTemplate.convertAndSendToUser(to, "/queue/private", msg)`. Le payload livré réutilise `ChatMessage` (`user` = expéditeur). Côté client : abonnement à `/user/queue/private`, une modale Bootstrap réutilisée, un tampon JS par interlocuteur (éphémère), un badge « non lu » sur l'expéditeur dans la sidebar.

**Tech Stack:** Java 17, Spring Boot WebSocket/STOMP (user destinations), Thymeleaf, Bootstrap 5.3 (modal), SockJS + @stomp/stompjs, JUnit 5 + AssertJ + Mockito.

---

## Rappels d'environnement (pièges)

- **Template Java NON compilé par `mvn test`** → garde générateur = `mvn test` (layout + `TemplateLoaderTest`) ; vraie validation = générer + compiler `ms-webui` (Task 3).
- **`ChatController` passe à 3 args** (`+SimpMessagingTemplate`) → met à jour les **2** constructions dans `ChatControllerTest` (sinon échec compile du module généré, attrapé en Task 3).
- **Nouveau fichier template** `PrivateMessageRequest.java` → bumper `TemplateLoaderTest` `hasSize` de **+1** (valeur actuelle 190 → 191 ; utiliser le nombre du message d'échec).
- **Layout Java** ≤120 cols, 4 espaces, un import/ligne, javadoc français.
- **Serveur zombie sur :8077** : tuer (`pkill -f springboot-platform-generator` ; vérifier `ps -eo args | grep "[j]ava -jar" | grep platform-generator`) AVANT toute génération.
- Toujours générer avec `webUI:true`.

---

## File Structure

Sous `src/main/resources/templates/ms-platform/ms-webui/` :
- Modify `…/webui/configuration/WebSocketConfig.java` — broker `/topic`+`/queue`, préfixe user `/user`.
- Create `…/webui/dto/PrivateMessageRequest.java` — DTO entrant `{to, text}`.
- Modify `…/webui/web/ChatController.java` — `+SimpMessagingTemplate`, `@MessageMapping("/chat.private")`.
- Modify `…/webui/src/test/java/…/web/ChatControllerTest.java` — constructeur 3-arg + test privé.
- Modify `…/webui/src/main/resources/templates/chat.html` — modale + clic présence + abonnement + tampon.
- Modify `…/webui/src/main/resources/static/css/app.css` — `.unread-badge`, `.presence-name.clickable`.

---

## Task 1 : Backend — broker user-destinations + endpoint privé

**Files:**
- Modify: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/configuration/WebSocketConfig.java`
- Create: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/dto/PrivateMessageRequest.java`
- Modify: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/web/ChatController.java`
- Test: `…/ms-webui/src/test/java/com/mr486/msplatform/webui/web/ChatControllerTest.java`

- [ ] **Step 1 : Mettre à jour le test d'abord (TDD)**

Remplacer le contenu de `ChatControllerTest.java` par :

```java
package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.dto.ChatMessage;
import com.mr486.msplatform.webui.dto.PrivateMessageRequest;
import com.mr486.msplatform.webui.service.ChatHistory;
import com.mr486.msplatform.webui.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ChatController} : auteur depuis le Principal, timestamp serveur,
 * signal de frappe, et routage d'un message privé vers le destinataire.
 */
class ChatControllerTest {

    private ChatController controller(SimpMessagingTemplate template) {
        return new ChatController(
                Mockito.mock(ChatHistory.class), Mockito.mock(PresenceService.class), template);
    }

    private Principal principal(String name) {
        Principal p = Mockito.mock(Principal.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    @Test
    void send_attributes_author_and_server_timestamp() {
        ChatHistory history = Mockito.mock(ChatHistory.class);
        ChatController controller = new ChatController(
                history, Mockito.mock(PresenceService.class), Mockito.mock(SimpMessagingTemplate.class));

        // le client tente d'usurper "bob" et d'imposer un timestamp 0 — les deux sont écrasés serveur
        ChatMessage out = controller.send(new ChatMessage("bob", "hello", 0), principal("alice"));

        assertThat(out.user()).isEqualTo("alice");
        assertThat(out.text()).isEqualTo("hello");
        assertThat(out.timestamp()).isGreaterThan(0);
        verify(history).add(out);
    }

    @Test
    void typing_returns_authenticated_username() {
        assertThat(controller(Mockito.mock(SimpMessagingTemplate.class)).typing(principal("alice")))
                .containsEntry("user", "alice");
    }

    @Test
    void private_message_routed_to_recipient_with_server_fields() {
        SimpMessagingTemplate template = Mockito.mock(SimpMessagingTemplate.class);
        ChatController controller = controller(template);

        controller.privateMessage(new PrivateMessageRequest("bob", "psst"), principal("alice"));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(template).convertAndSendToUser(eq("bob"), eq("/queue/private"), captor.capture());
        ChatMessage sent = captor.getValue();
        assertThat(sent.user()).isEqualTo("alice");
        assertThat(sent.text()).isEqualTo("psst");
        assertThat(sent.timestamp()).isGreaterThan(0);
    }
}
```

- [ ] **Step 2 : Créer le DTO `PrivateMessageRequest`**

```java
package com.mr486.msplatform.webui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Requête d'envoi privé reçue via STOMP : destinataire ({@code to}) et contenu ({@code text}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivateMessageRequest(String to, String text) {}
```

- [ ] **Step 3 : Étendre le broker dans `WebSocketConfig`**

Remplacer le corps de `configureMessageBroker` par :

```java
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
```

Mettre à jour le javadoc de la méthode pour mentionner `/queue` (files privées) et le préfixe `/user`.

- [ ] **Step 4 : `ChatController` — injecter `SimpMessagingTemplate` + endpoint privé**

Ajouter l'import : `import org.springframework.messaging.simp.SimpMessagingTemplate;`
Ajouter l'import : `import com.mr486.msplatform.webui.dto.PrivateMessageRequest;`

Remplacer le champ + constructeur par :
```java
    private final ChatHistory chatHistory;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatHistory chatHistory, PresenceService presenceService,
                          SimpMessagingTemplate messagingTemplate) {
        this.chatHistory = chatHistory;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }
```

Ajouter la méthode (après `typing`) :
```java
    /**
     * Route un message privé vers le destinataire via une user destination
     * ({@code /user/{to}/queue/private}). L'expéditeur est signé par le Principal ;
     * le message n'est pas persisté.
     *
     * @param in        la requête {@code {to, text}}
     * @param principal le Principal de l'expéditeur
     */
    @MessageMapping("/chat.private")
    public void privateMessage(PrivateMessageRequest in, Principal principal) {
        ChatMessage message = new ChatMessage(principal.getName(), in.text(), System.currentTimeMillis());
        messagingTemplate.convertAndSendToUser(in.to(), "/queue/private", message);
    }
```

- [ ] **Step 5 : Bumper le compteur de parité `TemplateLoaderTest`**

`PrivateMessageRequest.java` est 1 nouveau fichier template → ouvrir `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` et augmenter le `hasSize(N)` de 1 (190 → 191, ou le nombre indiqué par l'échec).

- [ ] **Step 6 : Layout + chargement**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD SUCCESS|BUILD FAILURE" | tail -1
```
Expected : BUILD SUCCESS, 125 (après bump). `ChatControllerTest` est un test template (non exécuté ici) — validé en Task 3.

- [ ] **Step 7 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/configuration/WebSocketConfig.java \
        src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/dto/PrivateMessageRequest.java \
        src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/web/ChatController.java \
        src/main/resources/templates/ms-platform/ms-webui/src/test/java/com/mr486/msplatform/webui/web/ChatControllerTest.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "$(cat <<'EOF'
feat(ms-webui): private chat messages via STOMP user destinations

Broker gains /queue + /user prefix; new PrivateMessageRequest DTO and
ChatController.privateMessage route {to,text} to /user/{to}/queue/private
(sender signed by Principal, ephemeral). Tests + parity count updated.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 : Frontend — modale DM + clic présence + tampon

**Files:**
- Modify: `…/ms-webui/src/main/resources/templates/chat.html`
- Modify: `…/ms-webui/src/main/resources/static/css/app.css`

- [ ] **Step 1 : `chat.html` — insérer la modale DM avant les `<script>`**

Juste après la balise fermante `</main>` (et avant le premier `<script src=...>`), insérer :

```html
<div class="modal fade" id="privateModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-dialog-centered">
    <div class="modal-content">
      <div class="modal-header">
        <h6 class="modal-title" id="pmTitle">Privé</h6>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Fermer"></button>
      </div>
      <div class="modal-body">
        <div class="chat-messages mb-3" id="pmMessages"></div>
        <form id="pm-form" class="d-flex gap-2">
          <input class="form-control" type="text" id="pm-text"
                 placeholder="Message privé…" autocomplete="off" required/>
          <button type="submit" class="btn btn-primary px-3">Envoyer</button>
        </form>
      </div>
    </div>
  </div>
</div>
```

- [ ] **Step 2 : `chat.html` — remplacer tout le bloc `<script th:inline="javascript">…</script>`**

Remplacer l'intégralité du `<script th:inline="javascript">` par (refactor `appendMessage`→`appendTo`, clic présence, badge non-lu, modale privée, abonnement `/user/queue/private`) :

```html
<script th:inline="javascript">
  const ME = /*[[${username}]]*/ '';
  const messages = document.getElementById('messages');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-text');
  const presenceList = document.getElementById('presenceList');
  const presenceCount = document.getElementById('presenceCount');
  const presenceCountBtn = document.getElementById('presenceCountBtn');

  const pmModalEl = document.getElementById('privateModal');
  const pmModal = new bootstrap.Modal(pmModalEl);
  const pmTitle = document.getElementById('pmTitle');
  const pmMessages = document.getElementById('pmMessages');
  const pmForm = document.getElementById('pm-form');
  const pmInput = document.getElementById('pm-text');

  const DAYS = ['dim', 'lun', 'mar', 'mer', 'jeu', 'ven', 'sam'];

  function formatTs(ts) {
    if (!ts || ts <= 0) return '';
    const d = new Date(ts);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return DAYS[d.getDay()] + ' ' + hh + ':' + mm;
  }

  function appendTo(container, m) {
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
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
  }

  let lastRoster = [];
  const typingUsers = new Set();
  const typingTimers = {};

  const privateBuffers = {};
  const unreadPrivate = new Set();
  let currentPeer = null;

  function drawPresence() {
    presenceList.innerHTML = '';
    lastRoster.forEach(u => {
      const li = document.createElement('li');
      li.className = 'presence-item';
      const dot = document.createElement('span');
      dot.className = 'online-dot';
      li.appendChild(dot);
      const name = document.createElement('span');
      name.className = 'presence-name';
      name.textContent = u === ME ? u + ' (vous)' : u;
      if (u !== ME) {
        name.classList.add('clickable');
        name.addEventListener('click', () => openPrivate(u));
      }
      li.appendChild(name);
      if (typingUsers.has(u) && u !== ME) {
        const t = document.createElement('span');
        t.className = 'typing-indicator';
        t.textContent = '…';
        t.title = 'écrit…';
        li.appendChild(t);
      }
      if (unreadPrivate.has(u) && u !== ME) {
        const b = document.createElement('span');
        b.className = 'unread-badge';
        b.title = 'message privé';
        li.appendChild(b);
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

  function openPrivate(u) {
    currentPeer = u;
    pmTitle.textContent = 'Privé — ' + u;
    unreadPrivate.delete(u);
    drawPresence();
    pmMessages.innerHTML = '';
    (privateBuffers[u] || []).forEach(m => appendTo(pmMessages, m));
    pmModal.show();
  }

  function receivePrivate(m) {
    const peer = m.user;
    (privateBuffers[peer] = privateBuffers[peer] || []).push(m);
    if (currentPeer === peer && pmModalEl.classList.contains('show')) {
      appendTo(pmMessages, m);
    } else {
      unreadPrivate.add(peer);
      drawPresence();
    }
  }

  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 2000,
    onConnect: () => {
      client.subscribe('/topic/chat', message => appendTo(messages, JSON.parse(message.body)));
      client.subscribe('/topic/presence', message => renderPresence(JSON.parse(message.body)));
      client.subscribe('/topic/typing', message => markTyping(JSON.parse(message.body).user));
      client.subscribe('/user/queue/private', message => receivePrivate(JSON.parse(message.body)));
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

  pmForm.addEventListener('submit', e => {
    e.preventDefault();
    const text = pmInput.value.trim();
    if (!text || !currentPeer) return;
    client.publish({ destination: '/app/chat.private', body: JSON.stringify({ to: currentPeer, text }) });
    const echo = { user: ME, text: text, timestamp: Date.now() };
    (privateBuffers[currentPeer] = privateBuffers[currentPeer] || []).push(echo);
    appendTo(pmMessages, echo);
    pmInput.value = '';
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

- [ ] **Step 3 : `app.css` — styles clic présence + badge non-lu**

Dans le bloc `/* ── Présence ── */`, ajouter après le `@keyframes typing-blink { … }` :

```css
.presence-name.clickable { cursor: pointer; }
.presence-name.clickable:hover { color: #fff; text-decoration: underline; }
.unread-badge {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: #ef4444; box-shadow: 0 0 6px #ef4444; margin-left: .4rem; flex: 0 0 auto;
}
```

- [ ] **Step 4 : Layout + chargement (pas de nouveau fichier)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD SUCCESS|BUILD FAILURE" | tail -1
```
Expected : BUILD SUCCESS, 125 (compteur inchangé — aucun fichier ajouté en Task 2).

- [ ] **Step 5 : Sanity-grep des marqueurs**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui/src/main/resources
grep -nE "privateModal|/user/queue/private|/app/chat.private|openPrivate|receivePrivate|presence-name" templates/chat.html
grep -nE "unread-badge|presence-name\.clickable" static/css/app.css
```
Expected : tous les marqueurs présents (modale, abonnement, destination, fonctions, classe cliquable ; CSS badge + clickable).

- [ ] **Step 6 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/resources/templates/chat.html \
        src/main/resources/templates/ms-platform/ms-webui/src/main/resources/static/css/app.css
git commit -m "$(cat <<'EOF'
feat(ms-webui): private DM modal — click a connected user to chat

Clicking a connected user opens a reusable Bootstrap modal ("Privé — X");
messages sent via /app/chat.private and received on /user/queue/private,
buffered per peer in memory (ephemeral). Unread DMs show a red badge next
to the sender in "Connectés". Reuses chat bubbles + timestamp; textContent.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 : Vérification de bout en bout

- [ ] **Step 1 : Générateur vert + tuer zombies + générer**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD SUCCESS|BUILD FAILURE" | tail -1
ps -eo args | grep "[j]ava -jar" | grep platform-generator && echo "ZOMBIE ABOVE" || echo "no zombie"
pkill -f springboot-platform-generator 2>/dev/null; sleep 1
mvn -q clean package -DskipTests
nohup java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 >/tmp/pm-srv.log 2>&1 &
sleep 12
curl -sf http://localhost:8077/ >/dev/null && echo "UP" || { echo "DOWN"; tail -5 /tmp/pm-srv.log; }
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/pm.zip
rm -rf /tmp/pm && unzip -q /tmp/pm.zip -d /tmp/pm
pkill -9 -f "server.port=8077" 2>/dev/null; true
```
Expected : générateur BUILD SUCCESS ; AUCUN zombie ; server UP ; ZIP généré.

- [ ] **Step 2 : Compiler `ms-webui` généré + lancer `ChatControllerTest` sur la sortie réelle**

```bash
cd /tmp/pm/ms-platform
mvn -pl ms-webui -am test -Dtest=ChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 \
  | grep -E "Running com|Tests run:|BUILD SUCCESS|BUILD FAILURE|COMPILATION ERROR"
```
Expected : `ms-webui` compile ; `ChatControllerTest` 3 tests OK ; BUILD SUCCESS. (Si COMPILATION ERROR sur `new ChatController(` à 2 args, corriger le site dans le template, régénérer, refaire.)

- [ ] **Step 3 : Vérifier les artefacts générés**

```bash
cd /tmp/pm/ms-platform/ms-webui
grep -n "enableSimpleBroker\|setUserDestinationPrefix" src/main/java/com/mr486/msplatform/webui/configuration/WebSocketConfig.java
grep -n "chat.private\|convertAndSendToUser" src/main/java/com/mr486/msplatform/webui/web/ChatController.java
grep -n "record PrivateMessageRequest" src/main/java/com/mr486/msplatform/webui/dto/PrivateMessageRequest.java
grep -nE "privateModal|/user/queue/private|/app/chat.private" src/main/resources/templates/chat.html | head
```
Expected : broker `/topic`+`/queue` + `setUserDestinationPrefix("/user")` ; `chat.private` + `convertAndSendToUser` ; le record ; marqueurs JS dans chat.html.

- [ ] **Step 4 : (Manuel, Docker) Vérification fonctionnelle**

> Documenté pour l'utilisateur — non exécutable ici.
> ```bash
> cd <plateforme générée webUI:true> && docker compose up -d --build ms-webui
> ```
> - Avec **deux** comptes connectés (ex. `test-admin`, `test-service-a`) sur `:8090/chat` :
>   - Chez A, **cliquer** `test-service-a` dans « Connectés » → la modale « Privé — test-service-a » s'ouvre.
>   - Envoyer un message → il apparaît à droite chez A ; chez B (modale fermée) un **badge rouge** apparaît à côté de `test-admin` ; à l'ouverture, le message est là (à gauche) avec horodatage.
>   - Répondre depuis B → arrive en direct dans la modale ouverte de A.
>   - Le chat public reste indépendant (les privés n'y apparaissent pas).

---

## Self-Review

- **Couverture design** : broker user-destinations (Task 1 WebSocketConfig), `PrivateMessageRequest` + `privateMessage` routage `convertAndSendToUser` (Task 1), modale réutilisée + clic présence (Task 2), abonnement `/user/queue/private` + tampon par pair + badge non-lu (Task 2), écho local à l'envoi (Task 2), réutilisation bulles+horodatage (Task 2 `appendTo`). ✅
- **Pas de placeholder** : tout le code (config, DTO, contrôleur, test, modale HTML, JS, CSS) fourni intégralement.
- **Cohérence des types** : `ChatController(ChatHistory, PresenceService, SimpMessagingTemplate)` identique entre contrôleur/test ; `PrivateMessageRequest(to, text)` ↔ JSON `{to,text}` publié par le client ; livraison `convertAndSendToUser(to, "/queue/private", ChatMessage)` ↔ abonnement client `/user/queue/private` lisant `m.user` ; `appendTo`/`drawPresence`/`openPrivate`/`receivePrivate` cohérents.
- **Pièges couverts** : arité `ChatController` (Task 1 met à jour les 2 sites du test ; Task 3 compile pour attraper tout oubli) ; compteur +1 (PrivateMessageRequest) ; zombie tué avant génération ; template non compilé → e2e compile ; textContent (anti-injection) conservé dans `appendTo`.

## Notes pour l'exécutant

- Les *user destinations* fonctionnent avec le broker simple dès que `setUserDestinationPrefix("/user")` est posé ; `convertAndSendToUser(name, "/queue/private", …)` cible toutes les sessions de l'utilisateur `name` (≡ son `preferred_username`, le même nom qu'en présence).
- L'expéditeur n'est PAS ré-écho par le serveur → l'écho est ajouté localement à l'envoi (évite le doublon).
- Éphémère : `privateBuffers` est en mémoire JS, perdu au rechargement (choix retenu). Destinataire déconnecté → message simplement non délivré (best-effort).
- `bootstrap` est chargé (bundle CDN) avant le `<script th:inline>`, donc `new bootstrap.Modal(...)` est disponible.
