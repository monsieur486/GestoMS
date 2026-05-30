# Phase 2c — `ms-client` CRUD générique (list + create) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à `ms-client` une UI générique list+create sur les resources, pilotée par un catalogue `@ConfigurationProperties` (injecté par le générateur), filtrée par rôle, via `GatewayClient.post` (refresh-retry factorisé).

**Architecture:** `ClientProperties` lit `client.resources` (serviceName/routePrefix/label/role). `ResourceController` filtre le catalogue par rôle (`ResourceAccess`), liste via `GatewayClient.get` et crée via `GatewayClient.post` (POST-redirect-GET). Le générateur réécrit le bloc `client:` de l'`application.yml` ms-client quand `resources[]` est fourni.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Thymeleaf, Jackson, `@ConfigurationProperties` (record) ; tests embarqués JUnit5 + Mockito ; générateur JUnit5 + AssertJ.

---

## Spec
`docs/superpowers/specs/2026-05-30-ms-client-crud-design.md`

## Carte des fichiers

Racine module : `src/main/resources/templates/ms-platform/ms-client/`. Package `com.mr486.msplatform.client`.

**Nouveaux (6) :**
| Fichier | Responsabilité |
|---------|----------------|
| `src/main/java/.../client/config/ClientProperties.java` | `@ConfigurationProperties("client")` record + `ResourceEntry` |
| `src/main/java/.../client/security/ResourceAccess.java` | Filtrage du catalogue par rôle (`accessible`, `find`) |
| `src/main/java/.../client/web/ResourceController.java` | `/resources` index + `/{serviceName}` list + create |
| `src/main/resources/templates/resources.html` | Index des resources accessibles |
| `src/main/resources/templates/resource.html` | Table + formulaire d'ajout |
| `src/test/java/.../client/security/ResourceAccessTest.java` | Test embarqué du filtrage par rôle |

**Modifiés (5) :**
| Fichier | Changement |
|---------|-----------|
| `src/main/java/.../client/service/GatewayClient.java` | refactor `exchangeWithRefresh` + `post()` |
| `src/test/java/.../client/service/GatewayClientTest.java` | `+` tests POST (succès + 401-refresh-retry) |
| `src/main/java/.../client/ClientApplication.java` | `@EnableConfigurationProperties(ClientProperties.class)` |
| `src/main/resources/application.yml` | `+ client.resources:` (défaut) en dernière section |
| `src/main/resources/templates/home.html` | lien « CRUD » réel |

**Générateur :** `CrossCuttingConfigProcessor.java` (réécriture catalogue) + `CrossCuttingConfigProcessorTest.java` + `TemplateLoaderTest.java` (parité 133 → 136 → 139).

## Conventions
- Code template NON compilé par le générateur → **oracle = `mvn package` du projet généré** (Task 5), qui compile ms-client ET exécute les tests embarqués (`ResourceAccessTest`, `GatewayClientTest`). Tasks 1–3 = transcription + parité ; vérif réelle en Task 5.
- **Commits verts** : chaque commit ajoutant des fichiers template met la parité `TemplateLoaderTest` à jour.
- Seule la **Task 4 (générateur)** est en vrai TDD (le test tourne dans le générateur).
- Chemin backend : ms-client appelle `gatewayClient.get/post(session, "/" + serviceName + routePrefix)` (slash initial, comme `/service-consumer/...` en 2b).
- `CrossCuttingConfigProcessor` helpers existants réutilisés : `routePrefix(r)` (défaut `/api/<classe>s`), `roleName(r)` (`USER_<SERVICE_UPPER>`), `hasResources(ctx)`, `containsNullByte`.

---

## Task 1 : Catalogue (`ClientProperties`) + filtrage (`ResourceAccess`) + test

**Files:**
- Create: `.../ms-client/src/main/java/com/mr486/msplatform/client/config/ClientProperties.java`
- Create: `.../ms-client/src/main/java/com/mr486/msplatform/client/security/ResourceAccess.java`
- Create: `.../ms-client/src/test/java/com/mr486/msplatform/client/security/ResourceAccessTest.java`
- Modify: `.../ms-client/src/main/java/com/mr486/msplatform/client/ClientApplication.java`
- Modify: `.../ms-client/src/main/resources/application.yml`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `ClientProperties.java`**

```java
package com.mr486.msplatform.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Catalogue des resources exposées par l'UI CRUD (injecté par le générateur dans application.yml). */
@ConfigurationProperties(prefix = "client")
public record ClientProperties(List<ResourceEntry> resources) {

    public record ResourceEntry(String serviceName, String routePrefix, String label, String role) {}
}
```

- [ ] **Step 2: `ResourceAccess.java`**

```java
package com.mr486.msplatform.client.security;

import com.mr486.msplatform.client.config.ClientProperties.ResourceEntry;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/** Filtre le catalogue selon les rôles : ADMIN voit tout ; sinon seulement les resources dont l'utilisateur a ROLE_<role>. */
public final class ResourceAccess {

    private ResourceAccess() {}

    public static List<ResourceEntry> accessible(List<ResourceEntry> entries,
                                                 Collection<? extends GrantedAuthority> authorities) {
        if (entries == null) return List.of();
        boolean admin = hasAuthority(authorities, "ROLE_ADMIN");
        return entries.stream()
                .filter(e -> admin || hasAuthority(authorities, "ROLE_" + e.role()))
                .toList();
    }

    public static ResourceEntry find(List<ResourceEntry> entries,
                                     Collection<? extends GrantedAuthority> authorities,
                                     String serviceName) {
        return accessible(entries, authorities).stream()
                .filter(e -> e.serviceName().equals(serviceName))
                .findFirst().orElse(null);
    }

    private static boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String role) {
        if (authorities == null) return false;
        for (GrantedAuthority a : authorities) {
            if (role.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
```

- [ ] **Step 3: `ResourceAccessTest.java`** (sous `src/test/java/...`)

```java
package com.mr486.msplatform.client.security;

import com.mr486.msplatform.client.config.ClientProperties.ResourceEntry;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceAccessTest {

    private static final List<ResourceEntry> CATALOG = List.of(
            new ResourceEntry("order-service", "/api/orders", "Order", "USER_ORDER_SERVICE"),
            new ResourceEntry("product-service", "/api/products", "Product", "USER_PRODUCT_SERVICE"));

    @Test
    void admin_sees_all() {
        var result = ResourceAccess.accessible(CATALOG, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(result).hasSize(2);
    }

    @Test
    void user_sees_only_own_resource() {
        var result = ResourceAccess.accessible(CATALOG, List.of(new SimpleGrantedAuthority("ROLE_USER_ORDER_SERVICE")));
        assertThat(result).extracting(ResourceEntry::serviceName).containsExactly("order-service");
    }

    @Test
    void user_without_matching_role_sees_nothing() {
        var result = ResourceAccess.accessible(CATALOG, List.of(new SimpleGrantedAuthority("ROLE_USER_BATCH")));
        assertThat(result).isEmpty();
    }

    @Test
    void find_returns_null_for_inaccessible_resource() {
        var auth = List.of(new SimpleGrantedAuthority("ROLE_USER_ORDER_SERVICE"));
        assertThat(ResourceAccess.find(CATALOG, auth, "product-service")).isNull();
        assertThat(ResourceAccess.find(CATALOG, auth, "order-service")).isNotNull();
    }
}
```

- [ ] **Step 4: `ClientApplication.java` — activer les properties**

Remplacer :
```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {
```
par :
```java
import com.mr486.msplatform.client.config.ClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ClientProperties.class)
public class ClientApplication {
```

- [ ] **Step 5: `application.yml` — catalogue par défaut en dernière section**

Ajouter à la FIN de `ms-client/src/main/resources/application.yml` (après le bloc `management:`) :
```yaml
client:
  resources:
    - serviceName: service-a
      routePrefix: /api/resources-a
      label: Service A
      role: USER_SERVICE_A
    - serviceName: service-b
      routePrefix: /api/resources-b
      label: Service B
      role: USER_SERVICE_B
    - serviceName: service-c
      routePrefix: /api/resources-c
      label: Service C
      role: USER_SERVICE_C
```

- [ ] **Step 6: Parité `TemplateLoaderTest` (→ 136)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 133 ... but was: 136` (3 nouveaux fichiers : ClientProperties, ResourceAccess, ResourceAccessTest).
Remplacer dans `TemplateLoaderTest.java` `hasSize(133)` → `hasSize(136)` (nombre observé).

- [ ] **Step 7: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/config/ClientProperties.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/security/ResourceAccess.java \
        src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/security/ResourceAccessTest.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/ClientApplication.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/application.yml \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client resource catalog (ClientProperties) + role-based ResourceAccess"
```

---

## Task 2 : `GatewayClient.post()` (refresh-retry factorisé) + tests POST

**Files:**
- Modify: `.../ms-client/src/main/java/com/mr486/msplatform/client/service/GatewayClient.java`
- Modify: `.../ms-client/src/test/java/com/mr486/msplatform/client/service/GatewayClientTest.java`

- [ ] **Step 1: Refactor `GatewayClient` — `exchangeWithRefresh` partagé + `post`**

Remplacer le bloc allant de `public String get(HttpSession session, String path) {` jusqu'à la fin de la méthode `doGet(...)` (inclus) par :
```java
    /** GET {@code path} via le gateway avec l'access token de session ; refresh + rejeu sur 401. */
    public String get(HttpSession session, String path) {
        return exchangeWithRefresh(session, path, HttpMethod.GET, null);
    }

    /** POST {@code jsonBody} vers {@code path} via le gateway ; refresh + rejeu sur 401. */
    public String post(HttpSession session, String path, String jsonBody) {
        return exchangeWithRefresh(session, path, HttpMethod.POST, jsonBody);
    }

    private String exchangeWithRefresh(HttpSession session, String path, HttpMethod method, String body) {
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        try {
            return doExchange(path, method, body, accessToken);
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
                return doExchange(path, method, body, fresh.accessToken());
            } catch (UnauthorizedSignal second) {
                throw new SessionExpiredException();
            }
        }
    }

    private String doExchange(String path, HttpMethod method, String body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken == null ? "" : accessToken);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    gatewayUrl + path, method, new HttpEntity<>(body, headers), String.class);
            return resp.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new UnauthorizedSignal();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new BackendForbiddenException();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            throw new BackendUnavailableException();
        }
    }
```

- [ ] **Step 2: Ajouter l'import `MediaType`**

Dans `GatewayClient.java`, ajouter avec les autres imports :
```java
import org.springframework.http.MediaType;
```

- [ ] **Step 3: Ajouter 2 tests POST à `GatewayClientTest.java`** (après `forbidden_maps_to_backend_forbidden`)

```java
    @Test
    void post_returns_body_on_success() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1}"));
        assertThat(gatewayClient.post(session, PATH, "{\"name\":\"x\"}")).isEqualTo("{\"id\":1}");
    }

    @Test
    void post_refreshes_and_retries_once_on_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.ok("created"));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThat(gatewayClient.post(session, PATH, "{}")).isEqualTo("created");
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
    }
```

- [ ] **Step 4: Suite générateur verte (fichiers modifiés, parité inchangée 136)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (toujours 136). Le `GatewayClientTest` ne tourne pas ici (template) — vérifié en Task 5.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/GatewayClient.java \
        src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/service/GatewayClientTest.java
git commit -m "feat(template): ms-client GatewayClient.post() with shared refresh-retry"
```

---

## Task 3 : `ResourceController` + vues + lien d'accueil + parité 139

**Files:**
- Create: `.../ms-client/src/main/java/com/mr486/msplatform/client/web/ResourceController.java`
- Create: `.../ms-client/src/main/resources/templates/resources.html`
- Create: `.../ms-client/src/main/resources/templates/resource.html`
- Modify: `.../ms-client/src/main/resources/templates/home.html`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `ResourceController.java`**

```java
package com.mr486.msplatform.client.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.client.config.ClientProperties;
import com.mr486.msplatform.client.config.ClientProperties.ResourceEntry;
import com.mr486.msplatform.client.security.ResourceAccess;
import com.mr486.msplatform.client.service.GatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/resources")
public class ResourceController {

    private final GatewayClient gatewayClient;
    private final ClientProperties clientProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResourceController(GatewayClient gatewayClient, ClientProperties clientProperties) {
        this.gatewayClient = gatewayClient;
        this.clientProperties = clientProperties;
    }

    @GetMapping
    public String index(Authentication authentication, Model model) {
        model.addAttribute("resources",
                ResourceAccess.accessible(clientProperties.resources(), authentication.getAuthorities()));
        return "resources";
    }

    @GetMapping("/{serviceName}")
    public String list(@PathVariable String serviceName, Authentication authentication,
                       HttpServletRequest request, Model model) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        model.addAttribute("entry", entry);
        HttpSession session = request.getSession(false);
        try {
            String json = gatewayClient.get(session, "/" + entry.serviceName() + entry.routePrefix());
            List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            model.addAttribute("rows", rows);
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            model.addAttribute("error", "Service indisponible.");
        }
        return "resource";
    }

    @PostMapping("/{serviceName}")
    public String create(@PathVariable String serviceName, @RequestParam String name,
                         @RequestParam String description, Authentication authentication,
                         HttpServletRequest request) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        HttpSession session = request.getSession(false);
        try {
            String body = mapper.writeValueAsString(Map.of("name", name, "description", description));
            gatewayClient.post(session, "/" + entry.serviceName() + entry.routePrefix(), body);
            return "redirect:/resources/" + serviceName;
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            return "redirect:/resources/" + serviceName + "?error";
        }
    }
}
```

- [ ] **Step 2: `resources.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Resources — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/}">← Accueil</a></p>
  <h1>Resources</h1>
  <p th:if="${resources.isEmpty()}">Aucune resource accessible.</p>
  <ul class="links">
    <li th:each="r : ${resources}">
      <a th:href="@{/resources/{s}(s=${r.serviceName})}" th:text="${r.label}">label</a>
    </li>
  </ul>
</main>
</body>
</html>
```

- [ ] **Step 3: `resource.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:text="${entry.label} + ' — ms-client'">Resource</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/resources}">← Resources</a></p>
  <h1 th:text="${entry.label}">Resource</h1>
  <p th:if="${param.error}" class="error">Échec de la création.</p>
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <table th:if="${rows}">
    <thead><tr><th>id</th><th>name</th><th>description</th></tr></thead>
    <tbody>
      <tr th:each="row : ${rows}">
        <td th:text="${row.id}">1</td>
        <td th:text="${row.name}">name</td>
        <td th:text="${row.description}">desc</td>
      </tr>
    </tbody>
  </table>
  <h2>Ajouter</h2>
  <form th:action="@{/resources/{s}(s=${entry.serviceName})}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <label>Name <input type="text" name="name" required/></label>
    <label>Description <input type="text" name="description" required/></label>
    <button type="submit">Créer</button>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 4: `home.html` — lien CRUD réel**

Remplacer :
```html
    <li>CRUD <em>(à venir — 2c)</em></li>
```
par :
```html
    <li><a th:href="@{/resources}">CRUD</a></li>
```

- [ ] **Step 5: Parité `TemplateLoaderTest` (→ 139)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 136 ... but was: 139` (3 nouveaux : ResourceController, resources.html, resource.html).
Remplacer `hasSize(136)` → `hasSize(139)`.

- [ ] **Step 6: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/ResourceController.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/resources.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/resource.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/home.html \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): ms-client /resources generic list+create UI + nav link"
```

---

## Task 4 : Générateur — réécriture du catalogue ms-client (TDD)

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: Ajouter un test d'échec dans `CrossCuttingConfigProcessorTest.java`**

Ajouter ces constantes + test (après le test `aggregate_untouched_when_no_resources`, dans la classe) :
```java
    private static final String CLIENT_YML_PATH =
        "ms-platform/ms-client/src/main/resources/application.yml";

    private static final String SAMPLE_CLIENT_YML =
        "server:\n  port: 8090\n" +
        "client:\n" +
        "  resources:\n" +
        "    - serviceName: service-a\n" +
        "      routePrefix: /api/resources-a\n" +
        "      label: Service A\n" +
        "      role: USER_SERVICE_A\n" +
        "    - serviceName: service-b\n" +
        "      routePrefix: /api/resources-b\n" +
        "      label: Service B\n" +
        "      role: USER_SERVICE_B\n";

    @Test
    void client_catalog_rewritten_for_resources() {
        List<GeneratedFile> result = processor.process(
            List.of(file(CLIENT_YML_PATH, SAMPLE_CLIENT_YML)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO)));
        String yml = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-client/src/main/resources/application.yml"))
            .findFirst().orElseThrow());
        assertThat(yml).contains("serviceName: order-service")
                       .contains("routePrefix: /api/orders")
                       .contains("role: USER_ORDER_SERVICE")
                       .contains("label: Order")
                       .contains("serviceName: product-service")
                       .contains("routePrefix: /api/products")
                       .contains("role: USER_PRODUCT_SERVICE");
        assertThat(yml).doesNotContain("service-a").doesNotContain("USER_SERVICE_A");
        assertThat(yml).contains("server:\n  port: 8090"); // section avant client: préservée
    }

    @Test
    void client_catalog_untouched_when_no_resources() {
        List<GeneratedFile> result = processor.process(
            List.of(file(CLIENT_YML_PATH, SAMPLE_CLIENT_YML)), defaultCtx());
        String yml = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-client/src/main/resources/application.yml"))
            .findFirst().orElseThrow());
        assertThat(yml).isEqualTo(SAMPLE_CLIENT_YML);
    }
```

- [ ] **Step 2: Lancer → échec attendu**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|client_catalog'`
Expected: échec sur `client_catalog_rewritten_for_resources` (le catalogue n'est pas encore réécrit).

- [ ] **Step 3: Dispatcher la réécriture dans `process()`**

Dans `CrossCuttingConfigProcessor.process()`, remplacer :
```java
            else if (hasResources && f.path().contains("/service-consumer/") && f.path().endsWith("AggregateController.java"))
                                                    result.add(rewriteAggregate(f, ctx));
```
par :
```java
            else if (hasResources && f.path().contains("/service-consumer/") && f.path().endsWith("AggregateController.java"))
                                                    result.add(rewriteAggregate(f, ctx));
            else if (hasResources && f.path().endsWith("/ms-client/src/main/resources/application.yml"))
                                                    result.add(rewriteClientCatalog(f, ctx));
```

- [ ] **Step 4: Ajouter la méthode `rewriteClientCatalog`**

Ajouter cette méthode dans `CrossCuttingConfigProcessor` (par ex. juste après `rewriteAggregate`) :
```java
    /**
     * Réécrit le bloc {@code client:} (dernière section de l'application.yml de ms-client) avec le
     * catalogue construit depuis {@code resources[]}. Le bloc étant en fin de fichier, on remplace de
     * {@code ^client:} jusqu'à la fin du contenu — pas de chirurgie d'indentation.
     */
    private GeneratedFile rewriteClientCatalog(GeneratedFile f, GenerationContext ctx) {
        if (containsNullByte(f.content())) return f;
        String text = new String(f.content(), StandardCharsets.UTF_8);
        StringBuilder block = new StringBuilder("client:\n  resources:\n");
        for (ResourceModuleRequest r : ctx.getRequest().getResources()) {
            block.append("    - serviceName: ").append(r.getServiceName()).append("\n");
            block.append("      routePrefix: ").append(routePrefix(r)).append("\n");
            block.append("      label: ").append(r.getClassName()).append("\n");
            block.append("      role: ").append(roleName(r)).append("\n");
        }
        String newText = text.replaceAll("(?ms)^client:.*\\z",
                Matcher.quoteReplacement(block.toString().stripTrailing() + "\n"));
        return new GeneratedFile(f.path(), newText.getBytes(StandardCharsets.UTF_8), f.executable());
    }
```

- [ ] **Step 5: Lancer → vert**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 6: Suite complète verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java \
        src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java
git commit -m "feat(generator): rewrite ms-client resource catalog from resources[]"
```

---

## Task 5 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1: Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 139).

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

- [ ] **Step 4: Générer `clientWebUI=true` + vérifier fichiers et catalogue**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":true,"clientWebUI":true}}' \
  -o /tmp/refc.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refcx && mkdir -p /tmp/refcx && unzip -q /tmp/refc.zip -d /tmp/refcx && echo UNZIPPED
echo "=== fichiers 2c présents ==="
ls /tmp/refcx/ms-platform/ms-client/src/main/java/com/acme/shop/client/config/ClientProperties.java \
   /tmp/refcx/ms-platform/ms-client/src/main/java/com/acme/shop/client/security/ResourceAccess.java \
   /tmp/refcx/ms-platform/ms-client/src/main/java/com/acme/shop/client/web/ResourceController.java \
   /tmp/refcx/ms-platform/ms-client/src/main/resources/templates/resources.html \
   /tmp/refcx/ms-platform/ms-client/src/main/resources/templates/resource.html \
   /tmp/refcx/ms-platform/ms-client/src/test/java/com/acme/shop/client/security/ResourceAccessTest.java 2>&1
echo "=== catalogue réécrit (order-service, pas service-a) ==="
grep -E 'serviceName: order-service|routePrefix: /api/orders|role: USER_ORDER_SERVICE|label: Order' /tmp/refcx/ms-platform/ms-client/src/main/resources/application.yml
grep -c 'service-a' /tmp/refcx/ms-platform/ms-client/src/main/resources/application.yml
```
Expected : `HTTP=200`, `UNZIPPED`, les 6 fichiers listés, les 4 lignes de catalogue présentes, et `0` pour `service-a`.

- [ ] **Step 5: Compiler le module généré ET exécuter les tests embarqués (sans skipTests)**

```bash
cd /tmp/refcx/ms-platform && mvn -pl ms-client -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|GatewayClientTest|ResourceAccessTest|ERROR.*\.java' | head -25
```
Expected: `ResourceAccessTest` (4) et `GatewayClientTest` (7) verts, puis `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 6: Compose + `clientWebUI=false` (absence) + arrêt**

```bash
cd /tmp/refcx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
cd /home/mr486/Developpement/Projets/GestoMS
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refc0.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refc0x && mkdir -p /tmp/refc0x && unzip -q /tmp/refc0.zip -d /tmp/refc0x
echo "=== ms-client absent ? ==="; ls /tmp/refc0x/ms-platform/ms-client 2>&1
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; `HTTP=200` ; « No such file or directory » (ms-client absent) ; arbre git propre (tout commité aux Tasks 1–4).

---

## Recovery
- `git log --oneline -6` — commits passés (catalog+access, GatewayClient.post, UI /resources, générateur catalogue).
- `grep hasSize src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` → `139` quand Tasks 1+3 faites.
- `grep -c rewriteClientCatalog src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` → `2` (dispatch + méthode) quand Task 4 faite.
- `mvn test` SUCCESS → générateur vert ; oracle du module = `mvn -pl ms-client -am package` du projet généré (Task 5, exécute `ResourceAccessTest` + `GatewayClientTest`).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Edit / Delete / get-by-id (le backend ne les expose pas).
- Notifs batch (2d), chat (2e), pagination/tri/recherche, champs dynamiques.