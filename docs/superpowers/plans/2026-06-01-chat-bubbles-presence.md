# Chat — bulles gauche/droite + présence temps réel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dans le chat de `ms-webui`, aligner les messages de l'utilisateur courant à droite et ceux des autres à gauche, et ajouter une colonne « Connectés » mise à jour en temps réel (présence en mémoire).

**Architecture:** `ms-webui` utilise déjà STOMP/SockJS avec un broker simple en mémoire (`/topic`, préfixe `/app`). On ajoute un suivi de présence **en mémoire** (`PresenceService`) piloté par les événements de cycle de vie des sessions STOMP (`SessionConnectedEvent`/`SessionDisconnectEvent`), diffusé sur `/topic/presence`. Le frontend (chat.html + app.css) passe à un layout 2 panneaux (sidebar connectés repliable en offcanvas sous `lg` + chat), avec des bulles alignées selon l'auteur.

**Tech Stack:** Java 17, Spring Boot (WebSocket/STOMP, WebFlux non concerné), Thymeleaf, Bootstrap 5.3 (offcanvas responsive), SockJS + @stomp/stompjs, JUnit 5 + AssertJ.

---

## Rappels d'environnement (pièges)

- **Le code Java des templates n'est PAS compilé par `mvn test`** (ressources). Garde générateur = `mvn test` (layout guards + chargement) ; vraie validation = générer + compiler `ms-webui` (Task 4).
- **Layout Java imposé** : ≤120 colonnes, 4 espaces, un import/ligne, javadoc français.
- **Compteur de parité** : ajouter des fichiers template casse `TemplateLoaderTest` (`hasSize(N)`). 3 nouveaux fichiers (PresenceService, PresenceEventListener, PresenceServiceTest) → bumper le nombre (utiliser le nombre du message d'échec).
- **Zombie :8080** : tuer les vieux `java -jar` avant vérif (`pkill -f springboot-platform-generator` / `--server.port=8077`).
- Toujours générer avec `webUI:true` (sinon le module ms-webui est filtré).

---

## File Structure

Sous `src/main/resources/templates/ms-platform/ms-webui/` :
- Create `…/webui/service/PresenceService.java` — état présence en mémoire (sessionId → username), liste distincte triée.
- Create `…/webui/web/PresenceEventListener.java` — écoute connect/disconnect STOMP, met à jour PresenceService, diffuse le roster sur `/topic/presence`.
- Modify `…/webui/web/ChatController.java` — expose `username` au modèle (GET) + `@MessageMapping("/presence.hello")` qui rediffuse le roster.
- Modify `…/webui/src/main/resources/templates/chat.html` — layout 2 panneaux, bulles, liste de présence, offcanvas.
- Modify `…/webui/src/main/resources/static/css/app.css` — styles bulles own/other + présence.
- Create `…/webui/src/test/java/com/mr486/msplatform/webui/service/PresenceServiceTest.java`.

---

## Task 1 : `PresenceService` (état présence en mémoire)

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/service/PresenceService.java`
- Test: `src/main/resources/templates/ms-platform/ms-webui/src/test/java/com/mr486/msplatform/webui/service/PresenceServiceTest.java`

- [ ] **Step 1 : Écrire le test d'abord (TDD)**

```java
package com.mr486.msplatform.webui.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link PresenceService} : liste distincte triée, dédoublonnage
 * multi-sessions d'un même utilisateur, et retrait au dernier disconnect.
 */
class PresenceServiceTest {

    @Test
    void lists_connected_users_distinct_and_sorted() {
        PresenceService presence = new PresenceService();
        presence.add("s1", "bob");
        presence.add("s2", "alice");
        assertThat(presence.connectedUsers()).containsExactly("alice", "bob");
    }

    @Test
    void dedupes_same_user_across_sessions_and_removes_on_last() {
        PresenceService presence = new PresenceService();
        presence.add("s1", "alice");
        presence.add("s2", "alice");
        assertThat(presence.connectedUsers()).containsExactly("alice");

        presence.remove("s1");
        assertThat(presence.connectedUsers()).containsExactly("alice"); // encore connecté via s2

        presence.remove("s2");
        assertThat(presence.connectedUsers()).isEmpty();
    }

    @Test
    void remove_unknown_session_is_noop() {
        PresenceService presence = new PresenceService();
        presence.remove("ghost");
        assertThat(presence.connectedUsers()).isEmpty();
    }
}
```

- [ ] **Step 2 : Lancer le test (doit échouer — classe absente)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
ls src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/service/PresenceService.java 2>&1
```
Expected : « No such file » (le test ne compile pas encore — classe à créer).

- [ ] **Step 3 : Implémenter `PresenceService`**

```java
package com.mr486.msplatform.webui.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suit en mémoire les utilisateurs connectés au chat (une entrée par session STOMP).
 * Plusieurs onglets d'un même utilisateur comptent comme un seul connecté.
 */
@Service
public class PresenceService {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    /**
     * Enregistre une session STOMP connectée pour un utilisateur.
     *
     * @param sessionId l'identifiant de session STOMP
     * @param username  le nom de l'utilisateur authentifié
     */
    public void add(String sessionId, String username) {
        sessions.put(sessionId, username);
    }

    /**
     * Retire une session déconnectée (no-op si la session est inconnue).
     *
     * @param sessionId l'identifiant de session STOMP
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Retourne la liste des utilisateurs connectés, distincts et triés alphabétiquement.
     *
     * @return les usernames connectés, jamais {@code null}
     */
    public List<String> connectedUsers() {
        return new ArrayList<>(new TreeSet<>(sessions.values()));
    }
}
```

- [ ] **Step 4 : Lancer la suite (layout guards chargent le nouveau fichier)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected : si `TemplateLoaderTest` échoue sur `hasSize`, bumper le nombre (voir Step 5) puis relancer → BUILD SUCCESS. (Le `PresenceServiceTest` lui-même ne s'exécute pas au niveau générateur — c'est un fichier template ; il sera lancé en Task 4 sur la sortie générée.)

- [ ] **Step 5 : Ajuster le compteur de parité `TemplateLoaderTest`**

Le test `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` affirme un `hasSize(N)` exact. Task 1 ajoute 2 fichiers template (PresenceService + PresenceServiceTest) → augmenter `N` de 2 (ou utiliser le nombre attendu indiqué par l'échec). Relancer `mvn -q clean test` → BUILD SUCCESS.

- [ ] **Step 6 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/service/PresenceService.java \
        src/main/resources/templates/ms-platform/ms-webui/src/test/java/com/mr486/msplatform/webui/service/PresenceServiceTest.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "$(cat <<'EOF'
feat(ms-webui): in-memory PresenceService for chat presence

Tracks connected STOMP sessions per user; connectedUsers() returns a
distinct, sorted list (multiple tabs of one user count once). Unit-tested.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 : présence STOMP — listener d'événements + endpoint `presence.hello`

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/web/PresenceEventListener.java`
- Modify: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/web/ChatController.java`

- [ ] **Step 1 : Créer `PresenceEventListener`**

```java
package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Met à jour {@link PresenceService} au gré des connexions/déconnexions STOMP et
 * diffuse la liste des connectés sur {@code /topic/presence} à chaque changement.
 */
@Component
public class PresenceEventListener {

    private static final String PRESENCE_TOPIC = "/topic/presence";

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Construit le listener avec le service de présence et le template de diffusion.
     *
     * @param presenceService   l'état de présence en mémoire
     * @param messagingTemplate le template de diffusion STOMP
     */
    public PresenceEventListener(PresenceService presenceService, SimpMessagingTemplate messagingTemplate) {
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * À la connexion STOMP : enregistre la session de l'utilisateur authentifié et diffuse le roster.
     *
     * @param event l'événement de connexion STOMP
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal user = event.getUser();
        if (sessionId != null && user != null) {
            presenceService.add(sessionId, user.getName());
            broadcast();
        }
    }

    /**
     * À la déconnexion STOMP : retire la session et diffuse le roster mis à jour.
     *
     * @param event l'événement de déconnexion STOMP
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.remove(event.getSessionId());
        broadcast();
    }

    private void broadcast() {
        messagingTemplate.convertAndSend(PRESENCE_TOPIC, presenceService.connectedUsers());
    }
}
```

- [ ] **Step 2 : Modifier `ChatController` — injecter `PresenceService`, exposer `username`, ajouter `presence.hello`**

Lire d'abord le fichier. Remplacer le champ/constructeur et la méthode `chat`, et ajouter `presenceHello`.

Champ + constructeur (ajouter `PresenceService`) :
```java
    private final ChatHistory chatHistory;
    private final PresenceService presenceService;

    public ChatController(ChatHistory chatHistory, PresenceService presenceService) {
        this.chatHistory = chatHistory;
        this.presenceService = presenceService;
    }
```

Méthode GET (ajouter `Principal` + attribut `username`) :
```java
    @GetMapping("/chat")
    public String chat(Model model, Principal principal) {
        model.addAttribute("history", chatHistory.recent());
        model.addAttribute("username", principal.getName());
        return "chat";
    }
```

Nouvelle méthode (un client fraîchement abonné publie ici pour obtenir le roster courant ;
le `@SendTo` rediffuse à `/topic/presence`) :
```java
    /**
     * Répond à un client qui vient de s'abonner en rediffusant la liste des connectés.
     *
     * @return la liste des utilisateurs connectés
     */
    @MessageMapping("/presence.hello")
    @SendTo("/topic/presence")
    public java.util.List<String> presenceHello() {
        return presenceService.connectedUsers();
    }
```

Ajouter les imports manquants : `com.mr486.msplatform.webui.service.PresenceService` (à côté de `ChatHistory`). `MessageMapping`, `SendTo`, `Principal`, `Model`, `GetMapping` existent déjà dans ce fichier.

- [ ] **Step 3 : Layout + chargement des templates**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected : si `TemplateLoaderTest` casse sur `hasSize` (1 nouveau fichier : PresenceEventListener), bumper de 1. → BUILD SUCCESS.

- [ ] **Step 4 : Ajuster le compteur de parité (PresenceEventListener = +1 fichier)**

Augmenter le `hasSize(N)` de `TemplateLoaderTest.java` de 1. Relancer `mvn -q clean test` → BUILD SUCCESS.

- [ ] **Step 5 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/web/PresenceEventListener.java \
        src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui/web/ChatController.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "$(cat <<'EOF'
feat(ms-webui): broadcast chat presence on STOMP connect/disconnect

PresenceEventListener updates PresenceService on SessionConnected/
SessionDisconnect and pushes the roster to /topic/presence. ChatController
exposes the current username and answers /app/presence.hello (re-broadcast
for freshly subscribed clients).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 : frontend — bulles gauche/droite + colonne « Connectés »

**Files:**
- Modify: `…/ms-webui/src/main/resources/templates/chat.html`
- Modify: `…/ms-webui/src/main/resources/static/css/app.css`

- [ ] **Step 1 : Remplacer `chat.html` par le layout 2 panneaux**

Remplacer tout le bloc `<main>…</main>` ET le `<script>` final (jusqu'à `</script>`) par :

```html
<main class="container py-5">

  <h1 class="fw-bold mb-3">Chat</h1>

  <div class="d-flex align-items-center gap-2 mb-3">
    <span class="status-dot disconnected" id="statusDot"></span>
    <span id="statusText" class="text-muted small">Connexion…</span>
  </div>

  <button class="btn btn-outline-secondary btn-sm d-lg-none mb-2" type="button"
          data-bs-toggle="offcanvas" data-bs-target="#presencePanel">
    Connectés (<span id="presenceCountBtn">0</span>)
  </button>

  <div class="row g-3">
    <div class="col-lg-3">
      <div class="offcanvas-lg offcanvas-start" tabindex="-1" id="presencePanel">
        <div class="offcanvas-header">
          <h6 class="offcanvas-title mb-0">Connectés</h6>
          <button type="button" class="btn-close d-lg-none"
                  data-bs-dismiss="offcanvas" data-bs-target="#presencePanel"></button>
        </div>
        <div class="offcanvas-body">
          <div class="text-muted small mb-2 d-none d-lg-block">Connectés (<span id="presenceCount">0</span>)</div>
          <ul class="presence-list" id="presenceList"></ul>
        </div>
      </div>
    </div>

    <div class="col-lg-9">
      <div class="chat-messages mb-3" id="messages">
        <div th:each="m : ${history}" class="chat-message"
             th:classappend="${m.user == username} ? 'own' : 'other'">
          <span class="sender" th:if="${m.user != username}" th:text="${m.user}">user</span>
          <div class="bubble" th:text="${m.text}">text</div>
        </div>
      </div>

      <form id="chat-form" class="d-flex gap-2">
        <input class="form-control" type="text" id="chat-text"
               placeholder="Votre message…" autocomplete="off" required/>
        <button type="submit" class="btn btn-primary px-4">Envoyer</button>
      </form>
    </div>
  </div>

</main>
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script th:inline="javascript">
  const ME = /*[[${username}]]*/ '';
  const messages = document.getElementById('messages');
  const dot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-text');
  const presenceList = document.getElementById('presenceList');
  const presenceCount = document.getElementById('presenceCount');
  const presenceCountBtn = document.getElementById('presenceCountBtn');

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
    messages.appendChild(div);
    messages.scrollTop = messages.scrollHeight;
  }

  function renderPresence(users) {
    presenceList.innerHTML = '';
    users.forEach(u => {
      const li = document.createElement('li');
      li.className = 'presence-item';
      const d = document.createElement('span');
      d.className = 'online-dot';
      li.appendChild(d);
      li.appendChild(document.createTextNode(u === ME ? u + ' (vous)' : u));
      presenceList.appendChild(li);
    });
    presenceCount.textContent = users.length;
    presenceCountBtn.textContent = users.length;
  }

  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 2000,
    onConnect: () => {
      dot.className = 'status-dot connected';
      statusText.textContent = 'Connecté';
      client.subscribe('/topic/chat', message => appendMessage(JSON.parse(message.body)));
      client.subscribe('/topic/presence', message => renderPresence(JSON.parse(message.body)));
      client.publish({ destination: '/app/presence.hello', body: '{}' });
    },
    onWebSocketClose: () => {
      dot.className = 'status-dot disconnected';
      statusText.textContent = 'Déconnecté — reconnexion…';
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
</script>
```

(Note : `appendMessage`/`renderPresence` utilisent `textContent` — corrige au passage l'injection HTML de l'ancien `innerHTML`.)

- [ ] **Step 2 : Ajouter les styles dans `app.css`**

Remplacer le bloc `/* ── Chat ── */` existant (les règles `.chat-message`, `.chat-message .sender`, `.chat-message .text`) par :

```css
/* ── Chat ── */
.chat-messages {
  height: 340px;
  overflow-y: auto;
  background: #0d0d15;
  border: 1px solid #2d2d42;
  border-radius: 8px;
  padding: 1rem;
  scrollbar-width: thin;
  scrollbar-color: #2d2d42 transparent;
}
.chat-message { display: flex; flex-direction: column; margin-bottom: .75rem; max-width: 75%; }
.chat-message .sender { color: #a78bfa; font-weight: 600; font-size: .75rem; margin-bottom: .15rem; }
.chat-message .bubble { padding: .4rem .7rem; border-radius: 12px; word-break: break-word; }
.chat-message.other { align-items: flex-start; margin-right: auto; }
.chat-message.other .bubble { background: #1e1e2e; color: #cbd5e1; border-bottom-left-radius: 3px; }
.chat-message.own { align-items: flex-end; margin-left: auto; }
.chat-message.own .bubble { background: #6d28d9; color: #fff; border-bottom-right-radius: 3px; }

/* ── Présence ── */
.presence-list { list-style: none; padding: 0; margin: 0; }
.presence-item { display: flex; align-items: center; padding: .35rem .25rem; color: #cbd5e1; font-size: .9rem; }
.online-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: #10b981; box-shadow: 0 0 6px #10b981; margin-right: .5rem; flex: 0 0 auto;
}
```

- [ ] **Step 3 : Layout + chargement (pas de nouveau fichier Java → compteur inchangé)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected : BUILD SUCCESS (chat.html/app.css ne sont pas du Java compilé ni comptés différemment — aucun nouveau fichier).

- [ ] **Step 4 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui/src/main/resources/templates/chat.html \
        src/main/resources/templates/ms-platform/ms-webui/src/main/resources/static/css/app.css
git commit -m "$(cat <<'EOF'
feat(ms-webui): chat bubbles (own right / others left) + connected sidebar

chat.html: 2-pane layout, own messages right (primary), others left (grey)
with sender label; left "Connectés" panel (offcanvas-lg, collapsible on
mobile) fed by /topic/presence; injects current username; switches to
textContent (no HTML injection). app.css: bubble + presence styles.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 : Vérification de bout en bout

- [ ] **Step 1 : Générateur vert + générer la plateforme**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
pkill -f springboot-platform-generator 2>/dev/null; true
mvn -q clean package -DskipTests
nohup java -jar target/*.jar --server.port=8077 >/tmp/chat-server.log 2>&1 &
sleep 12
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/chat.zip
rm -rf /tmp/chat && unzip -q /tmp/chat.zip -d /tmp/chat
pkill -f "server.port=8077" 2>/dev/null; true
```
Expected : générateur BUILD SUCCESS ; ZIP généré.

- [ ] **Step 2 : Compiler `ms-webui` généré + lancer `PresenceServiceTest` sur la sortie réelle**

```bash
cd /tmp/chat/ms-platform
mvn -q -pl ms-webui -am test -Dtest=PresenceServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 \
  | grep -E "Running com|Tests run:|BUILD"
```
Expected : `ms-webui` compile ; `PresenceServiceTest` 3 tests OK ; BUILD SUCCESS.

- [ ] **Step 3 : Vérifier les artefacts clés dans le ms-webui généré**

```bash
cd /tmp/chat/ms-platform/ms-webui
grep -n "presence.hello\|/topic/presence\|class=\"chat-message\"\|offcanvas-lg\|ME ===\|presence-list" \
  src/main/resources/templates/chat.html
grep -rl "PresenceService\|PresenceEventListener" src/main/java
grep -n "presenceHello\|username" src/main/java/com/mr486/msplatform/webui/web/ChatController.java
```
Expected : topic/hello/offcanvas/own-other présents dans chat.html ; les 2 classes Java présentes ; `presenceHello` + `username` dans ChatController.

- [ ] **Step 4 : (Manuel, Docker) Vérification fonctionnelle**

> Documenté pour l'utilisateur — non exécutable ici.
> ```bash
> cd <plateforme générée avec webUI:true> && docker compose up -d --build ms-webui
> ```
> - Ouvrir `:8090` dans **deux** navigateurs/onglets avec **deux** comptes (ex. `test-admin`, `test-service-a`) → chacun voit l'autre dans « Connectés » (+ « (vous) »).
> - Les messages envoyés par soi s'affichent **à droite** (violet), ceux des autres **à gauche** (gris, avec le nom).
> - Fermer un onglet → l'utilisateur disparaît de la liste de l'autre en temps réel.
> - Sur mobile (largeur < lg) : la liste « Connectés » est derrière le bouton (offcanvas).

---

## Self-Review

- **Couverture design** : alignement bulles (Task 3), colonne présence temps réel (Task 1 service + Task 2 listener/broadcast + Task 3 UI), offcanvas mobile (Task 3), « (vous) » + pastille verte (Task 3). ✅
- **Pas de placeholder** : tout le code (service, listener, contrôleur, HTML, CSS, test) fourni intégralement.
- **Cohérence des types** : `PresenceService.add(String,String)/remove(String)/connectedUsers():List<String>` identiques entre service, listener, contrôleur et test ; topic `/topic/presence` et destination `/app/presence.hello` cohérents backend↔frontend ; `ME`/`username` cohérents.
- **Pièges couverts** : compteur `TemplateLoaderTest` (Tasks 1 & 2), layout ≤120, template non compilé → compile e2e (Task 4), `textContent` anti-injection.

## Notes pour l'exécutant

- Présence **en mémoire, mono-instance** (choix retenu) — pas de Redis. Suffisant pour la plateforme (une instance ms-webui).
- `SessionConnectedEvent`/`SessionDisconnectEvent` : `event.getUser()` donne le `Principal` authentifié ; le sessionId vient de `StompHeaderAccessor.wrap(event.getMessage()).getSessionId()` (connect) ou `event.getSessionId()` (disconnect).
- Le `/ws` exige déjà une session authentifiée (SecurityConfig : `anyRequest().authenticated()`), donc le `Principal` est présent dans les événements STOMP.
