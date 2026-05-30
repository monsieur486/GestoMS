# Phase 2d — `ms-client` notifications batch temps réel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à `ms-client` une page `/notifications` (login-gated) affichant en temps réel le flux `BatchNotification` du WebSocket de service-consumer (`/topic/batch`), le navigateur se connectant directement au gateway.

**Architecture:** Le navigateur (page servie par ms-client) ouvre SockJS+STOMP vers `<gateway public>/service-consumer/ws` et s'abonne à `/topic/batch`. On rend `/ws/**` public côté service-consumer (télémétrie non sensible, broker en lecture seule). ms-client ne fait que servir la page et fournir l'URL gateway côté navigateur (`gateway.public-url`). Pas de proxy BFF, pas de token navigateur.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Thymeleaf, SockJS + STOMP (CDN) ; backend service-consumer = Spring Security resource server.

---

## Spec
`docs/superpowers/specs/2026-05-30-ms-client-batch-notifications-design.md`

## Carte des fichiers

**Nouveaux (2, module ms-client) :**
| Fichier | Responsabilité |
|---------|----------------|
| `ms-client/src/main/java/com/mr486/msplatform/client/web/NotificationsController.java` | `GET /notifications` ; injecte `gateway.public-url` ; rend la vue |
| `ms-client/src/main/resources/templates/notifications.html` | Page SockJS+STOMP : abonnement `/topic/batch`, affichage live |

**Modifiés (4) :**
| Fichier | Changement |
|---------|-----------|
| `ms-client/src/main/resources/application.yml` | `+ gateway.public-url` |
| `ms-client/src/main/resources/templates/home.html` | lien « Notifications batch » réel |
| `service-consumer/src/main/java/com/mr486/msplatform/consumer/configuration/SecurityConfig.java` | `+ /ws/** permitAll` |
| `docker-compose.yml` | bloc `ms-client` `+ GATEWAY_PUBLIC_URL` |
| `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` | parité 139 → 141 |

**Générateur :** aucun processor touché (tout est statique). Seule la parité `TemplateLoaderTest` change.

## Conventions
- Code/templates NON compilés par le générateur → **oracle = `mvn package` du projet généré** (Task 3). Pas de test embarqué (comportement WS = runtime/navigateur).
- **Commits verts** : la parité est mise à jour dans le commit qui ajoute les 2 fichiers.
- Libs sockjs/stomp via **CDN** (cohérent avec `batch-notifications.html` existant).
- L'URL gateway côté navigateur passe par un **attribut `data-`** (pas d'inline-JS Thymeleaf).

---

## Task 1 : Backend — rendre `/ws/**` public (service-consumer)

**Files:**
- Modify: `src/main/resources/templates/ms-platform/service-consumer/src/main/java/com/mr486/msplatform/consumer/configuration/SecurityConfig.java`

- [ ] **Step 1: Permettre `/ws/**`**

Remplacer :
```java
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
```
par :
```java
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**", "/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
```

- [ ] **Step 2: Vérifier**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`grep -c '"/actuator/\*\*", "/ws/\*\*"' src/main/resources/templates/ms-platform/service-consumer/src/main/java/com/mr486/msplatform/consumer/configuration/SecurityConfig.java`
Expected: `1`

- [ ] **Step 3: Suite générateur verte (fichier modifié, parité inchangée 139)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (139). Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/ms-platform/service-consumer/src/main/java/com/mr486/msplatform/consumer/configuration/SecurityConfig.java
git commit -m "feat(template): service-consumer permits public /ws (batch notifications stream)"
```

---

## Task 2 : ms-client — page `/notifications` + config + lien + compose + parité

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/NotificationsController.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/notifications.html`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/application.yml`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html`
- Modify: `src/main/resources/templates/ms-platform/docker-compose.yml`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `NotificationsController.java`**

```java
package com.mr486.msplatform.client.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationsController {

    private final String gatewayPublicUrl;

    public NotificationsController(@Value("${gateway.public-url}") String gatewayPublicUrl) {
        this.gatewayPublicUrl = gatewayPublicUrl;
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("gatewayPublicUrl", gatewayPublicUrl);
        return "notifications";
    }
}
```

- [ ] **Step 2: `notifications.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Notifications batch — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
  <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main th:attr="data-gateway-url=${gatewayPublicUrl}">
  <p><a th:href="@{/}">← Accueil</a></p>
  <h1>Notifications batch</h1>
  <p id="status" class="info">Connexion…</p>
  <ul id="events"></ul>
</main>
<script>
  const main = document.querySelector('main');
  const gatewayUrl = main.dataset.gatewayUrl;
  const statusEl = document.getElementById('status');
  const list = document.getElementById('events');
  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS(gatewayUrl + '/service-consumer/ws'),
    reconnectDelay: 2000,
    onConnect: () => {
      statusEl.textContent = 'Connecté — en attente de notifications…';
      client.subscribe('/topic/batch', message => {
        const p = JSON.parse(message.body);
        const li = document.createElement('li');
        li.textContent = `${p.jobId} - ${p.status} - ${p.generatedCount} fichiers - ${p.totalSeconds}s - ${p.instance}`;
        list.prepend(li);
      });
    },
    onWebSocketClose: () => { statusEl.textContent = 'Déconnecté — reconnexion…'; }
  });
  client.activate();
</script>
</body>
</html>
```

- [ ] **Step 3: `application.yml` — `gateway.public-url`**

Remplacer :
```yaml
gateway:
  url: ${GATEWAY_URL:http://localhost:9000}
```
par :
```yaml
gateway:
  url: ${GATEWAY_URL:http://localhost:9000}
  public-url: ${GATEWAY_PUBLIC_URL:http://localhost:9000}
```

- [ ] **Step 4: `home.html` — lien notifications réel**

Remplacer :
```html
    <li>Notifications batch <em>(à venir — 2d)</em></li>
```
par :
```html
    <li><a th:href="@{/notifications}">Notifications batch</a></li>
```

- [ ] **Step 5: `docker-compose.yml` — `GATEWAY_PUBLIC_URL` dans le bloc ms-client**

Remplacer (dans le bloc `ms-client:`) :
```yaml
      GATEWAY_URL: http://ms-gateway:9000
      MS_CLIENT_PORT: 8090
```
par :
```yaml
      GATEWAY_URL: http://ms-gateway:9000
      GATEWAY_PUBLIC_URL: http://localhost:9000
      MS_CLIENT_PORT: 8090
```

- [ ] **Step 6: Parité `TemplateLoaderTest` (→ 141)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 139 ... but was: 141` (2 nouveaux fichiers : NotificationsController, notifications.html).
Remplacer dans `TemplateLoaderTest.java` `hasSize(139)` → `hasSize(141)` (nombre observé).

- [ ] **Step 7: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/NotificationsController.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/notifications.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/application.yml \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html \
        src/main/resources/templates/ms-platform/docker-compose.yml \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client /notifications live batch feed (SockJS+STOMP) + nav link"
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1: Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 141).

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

- [ ] **Step 4: Générer `clientWebUI=true` + vérifier la 2d**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":true,"clientWebUI":true}}' \
  -o /tmp/refd.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refdx && mkdir -p /tmp/refdx && unzip -q /tmp/refd.zip -d /tmp/refdx && echo UNZIPPED
echo "=== fichiers 2d présents ==="
ls /tmp/refdx/ms-platform/ms-client/src/main/java/com/acme/shop/client/web/NotificationsController.java \
   /tmp/refdx/ms-platform/ms-client/src/main/resources/templates/notifications.html 2>&1
echo "=== config + lien + /ws permitAll ==="
grep -c 'public-url' /tmp/refdx/ms-platform/ms-client/src/main/resources/application.yml
grep -c '@{/notifications}' /tmp/refdx/ms-platform/ms-client/src/main/resources/templates/home.html
grep -c 'GATEWAY_PUBLIC_URL' /tmp/refdx/ms-platform/docker-compose.yml
grep -c '"/ws/\*\*"' /tmp/refdx/ms-platform/service-consumer/src/main/java/com/acme/shop/consumer/configuration/SecurityConfig.java
```
Expected : `HTTP=200`, `UNZIPPED`, les 2 fichiers listés, puis `1`, `1`, `1`, `1`.

- [ ] **Step 5: Compiler le module généré (tests embarqués 2b/2c restent verts)**

```bash
cd /tmp/refdx/ms-platform && mvn -pl ms-client -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|ERROR.*\.java' | head -20
```
Expected: `GatewayClientTest` (7) + `ResourceAccessTest` (4) verts, puis `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 6: Compose valide + `clientWebUI=false` (ms-client absent) + arrêt**

```bash
cd /tmp/refdx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
cd /home/mr486/Developpement/Projets/GestoMS
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refd0.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refd0x && mkdir -p /tmp/refd0x && unzip -q /tmp/refd0.zip -d /tmp/refd0x
echo "=== ms-client absent ? ==="; ls /tmp/refd0x/ms-platform/ms-client 2>&1
echo "=== /ws permitAll présent même sans ms-client (backend) ==="; grep -c '"/ws/\*\*"' /tmp/refd0x/ms-platform/service-consumer/src/main/java/com/acme/shop/consumer/configuration/SecurityConfig.java
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; `HTTP=200` ; ms-client « No such file or directory » ; `1` (le /ws permitAll backend reste) ; arbre git propre.

---

## Recovery
- `git log --oneline -4` — commits passés (service-consumer /ws public ; page notifications).
- `grep -c '"/ws/\*\*"' src/main/resources/templates/ms-platform/service-consumer/src/main/java/com/mr486/msplatform/consumer/configuration/SecurityConfig.java` → `1` si Task 1 faite.
- `grep hasSize src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` → `141` si Task 2 faite.
- `mvn test` SUCCESS → générateur vert ; oracle module = `mvn -pl ms-client -am package` du projet généré (Task 3).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Chat (2e), historique persistant, filtrage par jobId, proxy BFF du WebSocket.
