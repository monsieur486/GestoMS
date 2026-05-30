# Phase 2b — `ms-client` page consumer + proxy-avec-session + refresh — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter au module template `ms-client` une page `/consumer` (admin-only) qui affiche l'agrégat `service-consumer`, via un `GatewayClient` qui proxifie le backend avec le Bearer de session et rafraîchit le token sur 401 (rotation), retry une fois.

**Architecture:** `ConsumerController` (mince) délègue à `GatewayClient.get(session, path)` qui appelle `GATEWAY_URL/...` avec l'access token de session ; sur 401 il appelle `MsAuthClient.refresh()` (rotation), met à jour les deux tokens en session, rejoue une fois ; échec → `SessionExpiredException` → `/login?expired`. `/consumer` est protégé par `hasRole('ADMIN')` côté ms-client (miroir du backend). La logique refresh est couverte par un test embarqué Mockito hors-ligne.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Spring Session Redis, Thymeleaf, Jackson ; tests embarqués JUnit5 + Mockito + spring-test ; générateur : JUnit5 + AssertJ.

---

## Spec
`docs/superpowers/specs/2026-05-30-ms-client-consumer-page-design.md`

## Carte des fichiers

Racine module : `src/main/resources/templates/ms-platform/ms-client/`. Package `com.mr486.msplatform.client` (réécrit vers le `basePackage` par `PackagePlaceholderProcessor`).

**Nouveaux (4) :**
| Fichier | Responsabilité |
|---------|----------------|
| `src/main/java/.../client/service/GatewayClient.java` | Proxy backend via gateway + Bearer session ; refresh-retry sur 401 ; exceptions imbriquées |
| `src/main/java/.../client/web/ConsumerController.java` | `GET /consumer` : délègue, parse, ré-indente, rend ; mappe les exceptions |
| `src/main/resources/templates/consumer.html` | Vue : bloc par service (JSON indenté), zone d'erreur |
| `src/test/java/.../client/service/GatewayClientTest.java` | Test embarqué Mockito (hors-ligne) de la logique 401/refresh/retry |

**Modifiés (6) :**
| Fichier | Changement |
|---------|-----------|
| `src/main/java/.../client/service/MsAuthClient.java` | `+ refresh(opaqueRefreshToken) → MsAuthTokens` |
| `src/main/java/.../client/configuration/SecurityConfig.java` | `+ /consumer` → `hasRole("ADMIN")` |
| `src/main/resources/templates/home.html` | Lien « Page consumer » réel + conditionnel admin |
| `src/main/resources/templates/login.html` | Message « Session expirée » via `${param.expired}` |
| `pom.xml` | `+ spring-boot-starter-test` (scope test) |
| `src/main/resources/static/css/app.css` | Style `.service-block` / `pre` / `a` |

**Tests générateur :** `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` — parité 129 → 131 (Task 2) → 133 (Task 3).

## Conventions
- Le code Java du template n'est PAS compilé/exécuté par le générateur. **L'oracle du code template est le `mvn package` du projet généré** (Task 4), qui compile ms-client ET exécute `GatewayClientTest`. On écrit donc le contenu complet, on vérifie par génération.
- **Commits verts uniquement.** Chaque commit ajoutant des fichiers template met à jour la parité `TemplateLoaderTest` dans le même commit (sinon rouge).
- `FeatureFilterProcessor` (règle `ms-client/`) et `CrossCuttingConfigProcessor` : **aucune modif** (tout est interne au module).
- `HttpClientErrorException.create(status, ...)` renvoie la sous-classe spécifique (`.Unauthorized` pour 401, `.Forbidden` pour 403) — d'où l'ordre des `catch`.

---

## Task 1 : `MsAuthClient.refresh()` + dépendance de test

**Files:**
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/MsAuthClient.java`
- Modify: `src/main/resources/templates/ms-platform/ms-client/pom.xml`

- [ ] **Step 1: Ajouter `refresh()` après la méthode `login(...)`**

Insérer cette méthode juste après la fin de `login(...)` (avant `logout(...)`) dans `MsAuthClient.java` :
```java
    public MsAuthTokens refresh(String opaqueRefreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("opaque_refresh_token", opaqueRefreshToken == null ? "" : opaqueRefreshToken), headers);
        ResponseEntity<MsAuthTokens> resp =
                restTemplate.postForEntity(gatewayUrl + "/auth/refresh", entity, MsAuthTokens.class);
        MsAuthTokens body = resp.getBody();
        if (body == null) {
            throw new AuthUnavailableException();
        }
        return body;
    }
```
(Aucun nouvel import — tout est déjà importé. `refresh` laisse remonter une éventuelle `HttpClientErrorException` ; `GatewayClient` la traduira en `SessionExpiredException`.)

- [ ] **Step 2: Ajouter la dépendance de test au `pom.xml`**

Dans `ms-client/pom.xml`, après la ligne `spring-boot-starter-actuator`, ajouter :
```xml
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
```

- [ ] **Step 3: Vérifier**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`grep -c 'public MsAuthTokens refresh' src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/MsAuthClient.java ; grep -c 'spring-boot-starter-test' src/main/resources/templates/ms-platform/ms-client/pom.xml`
Expected: `1` puis `1`.

- [ ] **Step 4: Suite générateur verte (fichiers modifiés, parité inchangée)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (toujours 129 — aucun fichier ajouté). Si maven échoue pour cause de sandbox/réseau, le signaler.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/MsAuthClient.java \
        src/main/resources/templates/ms-platform/ms-client/pom.xml
git commit -m "feat(template): ms-client MsAuthClient.refresh() + test dependency"
```

---

## Task 2 : `GatewayClient` + test embarqué + parité 131

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/GatewayClient.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/service/GatewayClientTest.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

> Le test embarqué ne tourne pas dans le générateur (c'est une ressource) ; il est exécuté GREEN au Task 4 dans le projet généré. Ici on écrit impl + test et on met la parité à jour.

- [ ] **Step 1: Créer `GatewayClient.java`**

```java
package com.mr486.msplatform.client.service;

import com.mr486.msplatform.client.dto.MsAuthTokens;
import com.mr486.msplatform.client.security.SessionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Proxy BFF vers le backend via le gateway, avec le Bearer de la session.
 * Sur 401 : refresh (rotation) via ms-auth, mise à jour des deux tokens en session, et UN rejeu.
 */
@Service
public class GatewayClient {

    private final RestTemplate restTemplate;
    private final MsAuthClient msAuthClient;
    private final String gatewayUrl;

    public GatewayClient(RestTemplate restTemplate, MsAuthClient msAuthClient,
                         @Value("${gateway.url}") String gatewayUrl) {
        this.restTemplate = restTemplate;
        this.msAuthClient = msAuthClient;
        this.gatewayUrl = gatewayUrl;
    }

    /** GET {@code path} via le gateway avec l'access token de session ; refresh + rejeu sur 401. */
    public String get(HttpSession session, String path) {
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        try {
            return doGet(path, accessToken);
        } catch (UnauthorizedSignal first) {
            MsAuthTokens fresh;
            try {
                String refreshToken = (String) session.getAttribute(SessionKeys.REFRESH_TOKEN);
                fresh = msAuthClient.refresh(refreshToken);
            } catch (RuntimeException refreshFailed) {
                throw new SessionExpiredException();
            }
            session.setAttribute(SessionKeys.ACCESS_TOKEN, fresh.accessToken());
            session.setAttribute(SessionKeys.REFRESH_TOKEN, fresh.opaqueRefreshToken());
            try {
                return doGet(path, fresh.accessToken());
            } catch (UnauthorizedSignal second) {
                throw new SessionExpiredException();
            }
        }
    }

    private String doGet(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken == null ? "" : accessToken);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    gatewayUrl + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return resp.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new UnauthorizedSignal();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new BackendForbiddenException();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            throw new BackendUnavailableException();
        }
    }

    /** Signal interne : 401 reçu (déclenche le refresh-retry). */
    private static class UnauthorizedSignal extends RuntimeException {}

    public static class SessionExpiredException extends RuntimeException {}

    public static class BackendForbiddenException extends RuntimeException {}

    public static class BackendUnavailableException extends RuntimeException {}
}
```

- [ ] **Step 2: Créer `GatewayClientTest.java`** (sous `src/test/java/...`)

```java
package com.mr486.msplatform.client.service;

import com.mr486.msplatform.client.dto.MsAuthTokens;
import com.mr486.msplatform.client.security.SessionKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GatewayClientTest {

    private static final String PATH = "/service-consumer/api/aggregate";
    private static final String URL = "http://gw" + PATH;

    private RestTemplate restTemplate;
    private MsAuthClient msAuthClient;
    private GatewayClient gatewayClient;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        msAuthClient = Mockito.mock(MsAuthClient.class);
        gatewayClient = new GatewayClient(restTemplate, msAuthClient, "http://gw");
        session = new MockHttpSession();
        session.setAttribute(SessionKeys.ACCESS_TOKEN, "old-access");
        session.setAttribute(SessionKeys.REFRESH_TOKEN, "old-refresh");
    }

    @Test
    void returns_body_on_success() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"service-a\":\"{}\"}"));
        assertThat(gatewayClient.get(session, PATH)).isEqualTo("{\"service-a\":\"{}\"}");
    }

    @Test
    void refreshes_and_retries_once_on_401_then_succeeds() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.ok("OK"));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThat(gatewayClient.get(session, PATH)).isEqualTo("OK");
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
        assertThat(session.getAttribute(SessionKeys.REFRESH_TOKEN)).isEqualTo("new-refresh");
    }

    @Test
    void session_expired_when_refresh_fails() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null));
        when(msAuthClient.refresh(any())).thenThrow(new MsAuthClient.AuthUnavailableException());

        assertThatThrownBy(() -> gatewayClient.get(session, PATH))
                .isInstanceOf(GatewayClient.SessionExpiredException.class);
    }

    @Test
    void session_expired_when_retry_still_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThatThrownBy(() -> gatewayClient.get(session, PATH))
                .isInstanceOf(GatewayClient.SessionExpiredException.class);
    }

    @Test
    void forbidden_maps_to_backend_forbidden() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "403", null, null, null));
        assertThatThrownBy(() -> gatewayClient.get(session, PATH))
                .isInstanceOf(GatewayClient.BackendForbiddenException.class);
    }
}
```

- [ ] **Step 3: Mettre à jour la parité `TemplateLoaderTest` (→ 131)**

Run d'abord pour confirmer le nombre réel :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 129 ... but was: 131`.
Puis remplacer dans `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` :
```java
        assertThat(loader.load()).hasSize(129);
```
par (nombre observé, normalement `131`) :
```java
        assertThat(loader.load()).hasSize(131);
```

- [ ] **Step 4: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Si maven échoue pour cause de sandbox/réseau, le signaler.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/GatewayClient.java \
        src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/service/GatewayClientTest.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client GatewayClient (session proxy + 401 refresh-retry) + test"
```

---

## Task 3 : `ConsumerController` + vue + accès admin + parité 133

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/ConsumerController.java`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/consumer.html`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/SecurityConfig.java`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/login.html`
- Modify: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/static/css/app.css`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: Créer `ConsumerController.java`**

```java
package com.mr486.msplatform.client.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.client.service.GatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class ConsumerController {

    private final GatewayClient gatewayClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsumerController(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @GetMapping("/consumer")
    public String consumer(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        try {
            String json = gatewayClient.get(session, "/service-consumer/api/aggregate");
            Map<String, String> aggregate = mapper.readValue(json, new TypeReference<Map<String, String>>() {});
            Map<String, String> services = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : aggregate.entrySet()) {
                services.put(e.getKey(), prettyOrRaw(e.getValue()));
            }
            model.addAttribute("services", services);
            return "consumer";
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (GatewayClient.BackendForbiddenException e) {
            model.addAttribute("error", "Accès refusé par le service (réservé aux administrateurs).");
            return "consumer";
        } catch (Exception e) {
            model.addAttribute("error", "Service indisponible.");
            return "consumer";
        }
    }

    private String prettyOrRaw(String json) {
        try {
            Object parsed = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return json;
        }
    }
}
```

- [ ] **Step 2: Créer `consumer.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Agrégat consumer — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/}">← Accueil</a></p>
  <h1>Agrégat consumer</h1>
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <div th:each="svc : ${services}" class="service-block">
    <h2 th:text="${svc.key}">service</h2>
    <pre th:text="${svc.value}">{}</pre>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 3: `SecurityConfig` — protéger `/consumer` par `hasRole("ADMIN")`**

Remplacer :
```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
```
par :
```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .requestMatchers("/consumer").hasRole("ADMIN")
                        .anyRequest().authenticated())
```

- [ ] **Step 4: `home.html` — lien consumer réel + conditionnel admin**

Remplacer :
```html
    <li>Page consumer <em>(à venir — 2b)</em></li>
```
par :
```html
    <li th:if="${roles.contains('ROLE_ADMIN')}"><a th:href="@{/consumer}">Page consumer</a></li>
```

- [ ] **Step 5: `login.html` — message « session expirée »**

Remplacer :
```html
  <p th:if="${param.logout}" class="info">Vous êtes déconnecté.</p>
```
par :
```html
  <p th:if="${param.logout}" class="info">Vous êtes déconnecté.</p>
  <p th:if="${param.expired}" class="error">Session expirée, reconnectez-vous.</p>
```

- [ ] **Step 6: `app.css` — style des blocs service**

Ajouter à la fin de `app.css` :
```css
a { color: #3b5bdb; }
.service-block { margin-top: 1.2rem; }
.service-block pre { background: #2d2d34; color: #e6e6e6; padding: .8rem; border-radius: 4px; overflow-x: auto; }
```

- [ ] **Step 7: Mettre à jour la parité `TemplateLoaderTest` (→ 133)**

Run pour confirmer :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 131 ... but was: 133`.
Puis remplacer dans `TemplateLoaderTest.java` :
```java
        assertThat(loader.load()).hasSize(131);
```
par :
```java
        assertThat(loader.load()).hasSize(133);
```

- [ ] **Step 8: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/ConsumerController.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/consumer.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/SecurityConfig.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/login.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/static/css/app.css \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client /consumer page (admin-only) + view + nav link"
```

---

## Task 4 : Build générateur + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (piège du jar périmé : `mvn test` rafraîchit `target/classes` mais pas le jar lancé par `java -jar`). Lancement sandbox désactivé ; `pkill` et lancement en **commandes séparées** (sinon le pkill matche le chemin du jar sur sa propre ligne et se tue).

- [ ] **Step 1: Build complet du générateur (reconstruit aussi le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 133).

- [ ] **Step 2: Tuer un éventuel générateur (commande séparée)**

Run (sandbox désactivé) : `pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null; sleep 2; pgrep -af '[g]enerator-v5' || echo clean`
Expected: `clean`.

- [ ] **Step 3: Lancer le générateur (commande séparée, arrière-plan, sandbox désactivé, SANS pkill)**

```bash
java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 > /tmp/genapp.log 2>&1
```
Puis attendre (commande séparée) :
```bash
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
grep -q "Tomcat started on port 8077" /tmp/genapp.log && echo STARTED
```
Expected: `STARTED`.

- [ ] **Step 4: Générer `clientWebUI=true` + vérifier la présence de la 2b**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":true,"clientWebUI":true}}' \
  -o /tmp/refb.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refbx && mkdir -p /tmp/refbx && unzip -q /tmp/refb.zip -d /tmp/refbx && echo UNZIPPED
echo "=== fichiers 2b présents ==="
ls /tmp/refbx/ms-platform/ms-client/src/main/java/com/acme/shop/client/service/GatewayClient.java \
   /tmp/refbx/ms-platform/ms-client/src/main/java/com/acme/shop/client/web/ConsumerController.java \
   /tmp/refbx/ms-platform/ms-client/src/main/resources/templates/consumer.html \
   /tmp/refbx/ms-platform/ms-client/src/test/java/com/acme/shop/client/service/GatewayClientTest.java
echo "=== refresh + /consumer + lien admin ==="
grep -c 'public MsAuthTokens refresh' /tmp/refbx/ms-platform/ms-client/src/main/java/com/acme/shop/client/service/MsAuthClient.java
grep -c 'hasRole("ADMIN")' /tmp/refbx/ms-platform/ms-client/src/main/java/com/acme/shop/client/configuration/SecurityConfig.java
grep -c "@{/consumer}" /tmp/refbx/ms-platform/ms-client/src/main/resources/templates/home.html
```
Expected : `HTTP=200`, `UNZIPPED`, les 4 fichiers listés sans erreur, puis `1`, `1`, `1`.

- [ ] **Step 5: Compiler le module généré ET exécuter le test embarqué (sans skipTests)**

```bash
cd /tmp/refbx/ms-platform && mvn -pl ms-client -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|ERROR.*\.java' | head -20
```
Expected: `Tests run: 5, Failures: 0, Errors: 0` (le `GatewayClientTest`) puis `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 6: Valider le compose + générer `clientWebUI=false` (absence)**

```bash
cd /tmp/refbx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
cd /home/mr486/Developpement/Projets/GestoMS
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refb0.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refb0x && mkdir -p /tmp/refb0x && unzip -q /tmp/refb0.zip -d /tmp/refb0x
echo "=== dossier ms-client (doit etre absent) ==="; ls /tmp/refb0x/ms-platform/ms-client 2>&1
```
Expected : `COMPOSE_OK` ; `HTTP=200` ; « No such file or directory ».

- [ ] **Step 7: Arrêter le générateur + arbre git propre**

```bash
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected: arbre propre (tout commité aux Tasks 1–3).

---

## Recovery
- `git log --oneline -5` — commits passés (refresh+dep, GatewayClient+test, page consumer).
- `grep -rc 'class GatewayClient' src/main/resources/templates/ms-platform/ms-client/src/main/java/.../service/GatewayClient.java` → présent si Task 2 faite.
- `grep hasSize src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` → `133` quand Tasks 2+3 faites.
- `mvn test` SUCCESS → générateur vert ; oracle du module = `mvn -pl ms-client -am package` du projet généré (Task 4).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- CRUD générique (2c), notifs batch (2d), chat (2e), refresh proactif.
- Câblage `CrossCuttingConfigProcessor` / routes gateway (tout est interne à ms-client).
