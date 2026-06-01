# ms-client CRUD complet, pages publique/compte, protection admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corriger le benchmark (re-auth + quoting), ajouter GET/PUT/DELETE par id à service-a, compléter le CRUD ms-client (update+delete), ajouter une page publique et une page "mon compte" dans ms-client, et protéger côté serveur l'auto-suppression et l'auto-modification de rôles dans admin-application.

**Architecture:** Les templates `service-a`, `ms-client` et `admin-application` sont modifiés directement dans `src/main/resources/templates/ms-platform/`. Service-b et service-c héritent automatiquement de service-a par clonage dans le générateur. `GatewayClient` absorbe `put()` et `delete()` via le helper `exchangeWithRefresh` existant. La protection admin repose sur une comparaison username côté serveur (défense en profondeur par rapport au masquage HTML existant).

**Tech Stack:** Spring Boot MVC (Thymeleaf), Spring Security, Lombok, JUnit 5 / Mockito, Bash

---

## Fichiers impactés

| Fichier | Nature |
|---------|--------|
| `src/main/resources/templates/ms-platform/benchmark-async-batch.sh` | modifié |
| `…/service-a/…/service/ResourceAService.java` | modifié |
| `…/service-a/…/service/ResourceNotFoundException.java` | **nouveau** |
| `…/service-a/…/controller/ResourceAController.java` | modifié |
| `…/ms-client/…/service/GatewayClient.java` | modifié |
| `…/ms-client/…/service/GatewayClientTest.java` (test) | modifié |
| `…/ms-client/…/web/ResourceController.java` | modifié |
| `…/ms-client/…/web/PublicController.java` | **nouveau** |
| `…/ms-client/…/web/AccountController.java` | **nouveau** |
| `…/ms-client/…/config/ClientProperties.java` | modifié |
| `…/ms-client/…/configuration/SecurityConfig.java` | modifié |
| `…/ms-client/src/main/resources/application.yml` | modifié |
| `…/ms-client/src/main/resources/templates/resource.html` | modifié |
| `…/ms-client/src/main/resources/templates/resource-edit.html` | **nouveau** |
| `…/ms-client/src/main/resources/templates/public.html` | **nouveau** |
| `…/ms-client/src/main/resources/templates/account.html` | **nouveau** |
| `…/ms-client/src/main/resources/templates/layout.html` | modifié |
| `…/admin-application/…/web/UsersController.java` | modifié |
| `…/admin-application/…/web/RolesController.java` | modifié |
| `…/admin-application/src/main/resources/templates/users.html` | modifié |
| `…/admin-application/src/main/resources/templates/roles.html` | modifié |
| `src/test/java/…/generator/pipeline/TemplateLoaderTest.java` | modifié |

Chemins courts utilisés ci-dessous :
- `TMPL` = `src/main/resources/templates/ms-platform`
- `CLIENT` = `TMPL/ms-client/src/main/java/com/mr486/msplatform/client`
- `CLIENT_RES` = `TMPL/ms-client/src/main/resources`
- `SVC_A` = `TMPL/service-a/src/main/java/com/mr486/msplatform/servicea`
- `ADMIN` = `TMPL/admin-application/src/main/java/com/mr486/msplatform/adminapp`
- `ADMIN_RES` = `TMPL/admin-application/src/main/resources`

---

### Task 1 : Benchmark — re-auth automatique + quoting fix

**Files:**
- Modify: `TMPL/benchmark-async-batch.sh`

- [ ] **Step 1 : Ouvrir le fichier et vérifier l'état actuel**

```bash
head -20 src/main/resources/templates/ms-platform/benchmark-async-batch.sh
```

Expected : la ligne `source tokens.env` est présente, `TOKEN_BATCH` est attendu.

- [ ] **Step 2 : Remplacer le contenu du fichier**

Remplacer l'intégralité du fichier par :

```bash
#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}
BATCH_USER=${BATCH_USER:-test-batch}
BATCH_PASSWORD=${BATCH_PASSWORD:-user123}

# Re-authenticate at each run so the token is always fresh.
echo "Authenticating as ${BATCH_USER}..."
BATCH_LOGIN=$(curl -s -X POST "${GATEWAY_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${BATCH_USER}\",\"password\":\"${BATCH_PASSWORD}\"}")
TOKEN_BATCH=$(echo "$BATCH_LOGIN" | jq -r '.access_token // empty')
if [ -z "$TOKEN_BATCH" ]; then
  echo "Authentication failed. Check GATEWAY_URL / BATCH_USER / BATCH_PASSWORD."
  exit 1
fi

REQUESTS="${1:-10}"
CONCURRENCY="${2:-10}"
FAILURE_RATE=0

if [ "${3:-}" = "--failure-rate" ]; then
  FAILURE_RATE="${4:-0}"
fi

RESULT_DIR="benchmark-results"
rm -rf "$RESULT_DIR"
mkdir -p "$RESULT_DIR/txt" "$RESULT_DIR/json"

create_job() {
  local i="$1"

  curl -s -w "\nHTTP_STATUS:%{http_code}\n" \
    -X POST \
    -H "Authorization: Bearer ${TOKEN_BATCH}" \
    "http://localhost:9000/service-consumer/api/users/${i}/batch-jobs?failureRate=${FAILURE_RATE}" \
    > "$RESULT_DIR/txt/create-${i}.txt"
}

START=$(date +%s)
running=0

for i in $(seq 1 "$REQUESTS"); do
  create_job "$i" &
  running=$((running + 1))

  if [ "$running" -ge "$CONCURRENCY" ]; then
    wait -n
    running=$((running - 1))
  fi
done

wait
CREATE_END=$(date +%s)

for f in "$RESULT_DIR"/txt/create-*.txt; do
  sed '/HTTP_STATUS:/d' "$f" > "$RESULT_DIR/json/$(basename "$f" .txt).json"
done

JOB_IDS=$(jq -r 'select(.jobId != null) | .jobId' "$RESULT_DIR"/json/create-*.json 2>/dev/null || true)

if [ -z "$JOB_IDS" ]; then
  echo "Async requests: $REQUESTS"
  echo "Create concurrency: $CONCURRENCY"
  echo "Create wall time: $((CREATE_END - START)) sec"
  echo "Create HTTP status distribution:"
  grep -h "HTTP_STATUS" "$RESULT_DIR"/txt/create-*.txt | cut -d: -f2 | sort | uniq -c
  echo "No jobs created. Sample response:"
  cat "$RESULT_DIR/json/create-1.json"
  echo
  exit 1
fi

for id in $JOB_IDS; do
  for t in $(seq 1 300); do
    curl -s \
      -H "Authorization: Bearer ${TOKEN_BATCH}" \
      "http://localhost:9000/service-consumer/api/batch-jobs/${id}" \
      > "$RESULT_DIR/json/job-${id}.json"

    status=$(jq -r '.status // empty' "$RESULT_DIR/json/job-${id}.json")

    if [ "$status" = "COMPLETED" ] || [ "$status" = "DEAD" ]; then
      break
    fi

    sleep 1
  done
done

END=$(date +%s)

echo "Async requests: $REQUESTS"
echo "Create concurrency: $CONCURRENCY"
echo "Create wall time: $((CREATE_END - START)) sec"
echo "Total wall time until completion: $((END - START)) sec"

echo "Create HTTP status distribution:"
grep -h "HTTP_STATUS" "$RESULT_DIR"/txt/create-*.txt | cut -d: -f2 | sort | uniq -c

echo "Job status distribution:"
if ls "$RESULT_DIR"/json/job-*.json >/dev/null 2>&1; then
  jq -r '.status // empty' "$RESULT_DIR"/json/job-*.json | sort | uniq -c
else
  echo "No job result files"
fi

echo "Instance distribution:"
if ls "$RESULT_DIR"/json/job-*.json >/dev/null 2>&1; then
  jq -r 'select(.instance != null) | .instance' "$RESULT_DIR"/json/job-*.json | sort | uniq -c
else
  echo "No job result files"
fi

echo "Sample final job:"
if ls "$RESULT_DIR"/json/job-*.json >/dev/null 2>&1; then
  last_job=$(ls "$RESULT_DIR"/json/job-*.json | tail -n 1)
  cat "$last_job"
else
  cat "$RESULT_DIR/json/create-1.json"
fi

echo
```

- [ ] **Step 3 : Vérifier que le script est exécutable**

```bash
ls -la src/main/resources/templates/ms-platform/benchmark-async-batch.sh
```

Expected : permissions `rwxr-xr-x` (ou équivalent avec x). Sinon : `chmod +x src/main/resources/templates/ms-platform/benchmark-async-batch.sh`

- [ ] **Step 4 : Commit**

```bash
git add src/main/resources/templates/ms-platform/benchmark-async-batch.sh
git commit -m "fix(benchmark): re-auth automatique au démarrage + fix quoting cat/ls"
```

---

### Task 2 : service-a — ResourceNotFoundException

**Files:**
- Create: `SVC_A/service/ResourceNotFoundException.java`

- [ ] **Step 1 : Créer l'exception**

```java
package com.mr486.msplatform.servicea.service;
import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException{ public ResourceNotFoundException(Long id){super("Resource not found: "+id);} }
```

Chemin exact : `src/main/resources/templates/ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/service/ResourceNotFoundException.java`

- [ ] **Step 2 : Commit**

```bash
git add src/main/resources/templates/ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/service/ResourceNotFoundException.java
git commit -m "feat(service-a): ResourceNotFoundException @ResponseStatus(404)"
```

---

### Task 3 : service-a — ResourceAService : findById, update, delete

**Files:**
- Modify: `SVC_A/service/ResourceAService.java`

- [ ] **Step 1 : Remplacer le contenu du fichier**

```java
package com.mr486.msplatform.servicea.service;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.entity.ResourceA;
import com.mr486.msplatform.servicea.repository.ResourceARepository;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.List;
@Service @RequiredArgsConstructor
public class ResourceAService{
  private final ResourceARepository repository;
  public List<ResourceADto> findAll(){return repository.findAll().stream().map(this::toDto).toList();}
  public ResourceADto findById(Long id){return toDto(repository.findById(id).orElseThrow(()->new ResourceNotFoundException(id)));}
  public ResourceADto create(ResourceADto dto){ ResourceA entity=ResourceA.builder().name(dto.getName()).description(dto.getDescription()).build(); return toDto(repository.save(entity)); }
  public ResourceADto update(Long id,ResourceADto dto){ ResourceA entity=repository.findById(id).orElseThrow(()->new ResourceNotFoundException(id)); entity.setName(dto.getName()); entity.setDescription(dto.getDescription()); return toDto(repository.save(entity)); }
  public void delete(Long id){ if(!repository.existsById(id)) throw new ResourceNotFoundException(id); repository.deleteById(id); }
  private ResourceADto toDto(ResourceA entity){return ResourceADto.builder().id(entity.getId()).name(entity.getName()).description(entity.getDescription()).build();}
}
```

- [ ] **Step 2 : Commit**

```bash
git add src/main/resources/templates/ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/service/ResourceAService.java
git commit -m "feat(service-a): findById, update, delete dans ResourceAService"
```

---

### Task 4 : service-a — ResourceAController : GET/PUT/DELETE par id

**Files:**
- Modify: `SVC_A/controller/ResourceAController.java`

- [ ] **Step 1 : Remplacer le contenu du fichier**

```java
package com.mr486.msplatform.servicea.controller;
import com.mr486.msplatform.servicea.dto.ResourceADto;import com.mr486.msplatform.servicea.service.ResourceAService;import lombok.RequiredArgsConstructor;import org.springframework.http.HttpStatus;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/resources-a")
public class ResourceAController{
  private final ResourceAService service;
  @GetMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public List<ResourceADto> findAll(){return service.findAll();}
  @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto findById(@PathVariable Long id){return service.findById(id);}
  @PostMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto create(@RequestBody ResourceADto dto){return service.create(dto);}
  @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto update(@PathVariable Long id,@RequestBody ResourceADto dto){return service.update(id,dto);}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public void delete(@PathVariable Long id){service.delete(id);}
}
```

- [ ] **Step 2 : Commit**

```bash
git add src/main/resources/templates/ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/controller/ResourceAController.java
git commit -m "feat(service-a): GET/PUT/DELETE /{id} dans ResourceAController"
```

---

### Task 5 : ms-client — GatewayClient : put() et delete()

**Files:**
- Modify: `CLIENT/service/GatewayClient.java`
- Modify (test): `TMPL/ms-client/src/test/java/com/mr486/msplatform/client/service/GatewayClientTest.java`

- [ ] **Step 1 : Écrire les tests pour put() et delete() — vérifier qu'ils échouent**

Ajouter à la fin de `GatewayClientTest`, avant la dernière `}` :

```java
    @Test
    void put_returns_body_on_success() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.PUT), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1,\"name\":\"updated\"}"));
        assertThat(gatewayClient.put(session, PATH, "{\"name\":\"updated\",\"description\":\"d\"}"))
                .isEqualTo("{\"id\":1,\"name\":\"updated\"}");
    }

    @Test
    void put_refreshes_and_retries_once_on_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.PUT), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.ok("updated"));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThat(gatewayClient.put(session, PATH, "{}")).isEqualTo("updated");
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
    }

    @Test
    void delete_succeeds() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(ResponseEntity.noContent().build());
        gatewayClient.delete(session, PATH); // no exception = success
    }

    @Test
    void delete_refreshes_and_retries_once_on_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.noContent().build());
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        gatewayClient.delete(session, PATH);
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
    }
```

- [ ] **Step 2 : Ajouter put() et delete() dans GatewayClient**

Ajouter après la méthode `post()` existante (avant le helper `exchangeWithRefresh`) :

```java
    /** PUT {@code jsonBody} vers {@code path} via le gateway ; refresh + rejeu sur 401. */
    public String put(HttpSession session, String path, String jsonBody) {
        return exchangeWithRefresh(session, path, HttpMethod.PUT, jsonBody);
    }

    /** DELETE {@code path} via le gateway ; refresh + rejeu sur 401. */
    public void delete(HttpSession session, String path) {
        exchangeWithRefresh(session, path, HttpMethod.DELETE, null);
    }
```

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/service/GatewayClient.java \
        src/main/resources/templates/ms-platform/ms-client/src/test/java/com/mr486/msplatform/client/service/GatewayClientTest.java
git commit -m "feat(ms-client): GatewayClient.put() et delete() + tests"
```

---

### Task 6 : ms-client — ResourceController : routes edit et delete

**Files:**
- Modify: `CLIENT/web/ResourceController.java`
- Create: `CLIENT_RES/templates/resource-edit.html`
- Modify: `CLIENT_RES/templates/resource.html`

- [ ] **Step 1 : Remplacer ResourceController.java**

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

    @GetMapping("/{serviceName}/{id}/edit")
    public String editForm(@PathVariable String serviceName, @PathVariable String id,
                           Authentication authentication, HttpServletRequest request, Model model) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        model.addAttribute("entry", entry);
        model.addAttribute("rowId", id);
        HttpSession session = request.getSession(false);
        try {
            String json = gatewayClient.get(session, "/" + entry.serviceName() + entry.routePrefix() + "/" + id);
            Map<String, Object> row = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            model.addAttribute("row", row);
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            return "redirect:/resources/" + serviceName + "?error";
        }
        return "resource-edit";
    }

    @PostMapping("/{serviceName}/{id}/edit")
    public String edit(@PathVariable String serviceName, @PathVariable String id,
                       @RequestParam String name, @RequestParam String description,
                       Authentication authentication, HttpServletRequest request) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        HttpSession session = request.getSession(false);
        try {
            String body = mapper.writeValueAsString(Map.of("name", name, "description", description));
            gatewayClient.put(session, "/" + entry.serviceName() + entry.routePrefix() + "/" + id, body);
            return "redirect:/resources/" + serviceName;
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            return "redirect:/resources/" + serviceName + "?error";
        }
    }

    @PostMapping("/{serviceName}/{id}/delete")
    public String delete(@PathVariable String serviceName, @PathVariable String id,
                         Authentication authentication, HttpServletRequest request) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        HttpSession session = request.getSession(false);
        try {
            gatewayClient.delete(session, "/" + entry.serviceName() + entry.routePrefix() + "/" + id);
            return "redirect:/resources/" + serviceName;
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            return "redirect:/resources/" + serviceName + "?error";
        }
    }
}
```

- [ ] **Step 2 : Créer resource-edit.html**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:text="'Modifier — ' + ${entry.label} + ' — ms-client'">Modifier</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/resources/{s}(s=${entry.serviceName})}">← Retour</a></p>
  <h1 th:text="'Modifier — ' + ${entry.label}">Modifier</h1>
  <p th:if="${param.error}" class="error">Échec de la modification.</p>
  <form th:if="${row}" th:action="@{/resources/{s}/{id}/edit(s=${entry.serviceName},id=${rowId})}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <label>Name <input type="text" name="name" th:value="${row.name}" required/></label>
    <label>Description <input type="text" name="description" th:value="${row.description}" required/></label>
    <button type="submit">Enregistrer</button>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 3 : Modifier resource.html — ajouter colonnes Modifier / Supprimer**

Remplacer le bloc `<tbody>` existant :

```html
    <tbody>
      <tr th:each="row : ${rows}">
        <td th:text="${row.id}">1</td>
        <td th:text="${row.name}">name</td>
        <td th:text="${row.description}">desc</td>
      </tr>
    </tbody>
```

par :

```html
    <tbody>
      <tr th:each="row : ${rows}">
        <td th:text="${row.id}">1</td>
        <td th:text="${row.name}">name</td>
        <td th:text="${row.description}">desc</td>
        <td>
          <a th:href="@{/resources/{s}/{id}/edit(s=${entry.serviceName},id=${row.id})}">Modifier</a>
          <form th:action="@{/resources/{s}/{id}/delete(s=${entry.serviceName},id=${row.id})}" method="post"
                style="display:inline" onsubmit="return confirm('Supprimer cet élément ?');">
            <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
            <button type="submit">Supprimer</button>
          </form>
        </td>
      </tr>
    </tbody>
```

Ajouter aussi `<th>actions</th>` dans le `<thead>` :

```html
  <thead><tr><th>id</th><th>name</th><th>description</th><th>actions</th></tr></thead>
```

- [ ] **Step 4 : Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/ResourceController.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/resource-edit.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/resource.html
git commit -m "feat(ms-client): ResourceController edit+delete + resource-edit.html"
```

---

### Task 7 : ms-client — page publique /public

**Files:**
- Create: `CLIENT/web/PublicController.java`
- Create: `CLIENT_RES/templates/public.html`
- Modify: `CLIENT/configuration/SecurityConfig.java`

- [ ] **Step 1 : Créer PublicController.java**

```java
package com.mr486.msplatform.client.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicController {

    @GetMapping("/public")
    public String publicPage() {
        return "public";
    }
}
```

- [ ] **Step 2 : Créer public.html**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ms-platform</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<main>
  <h1>ms-platform</h1>
  <p>Plateforme microservices générée par GestoMS.</p>
  <p>Gérez vos ressources métier de façon sécurisée et scalable.</p>
  <a th:href="@{/login}">Se connecter</a>
</main>
</body>
</html>
```

- [ ] **Step 3 : Modifier SecurityConfig — ajouter /public dans permitAll**

Dans `SecurityConfig.java`, remplacer :

```java
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
```

par :

```java
                        .requestMatchers("/login", "/public", "/css/**", "/actuator/health").permitAll()
```

- [ ] **Step 4 : Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/PublicController.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/public.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/configuration/SecurityConfig.java
git commit -m "feat(ms-client): page publique /public (landing marketing)"
```

---

### Task 8 : ms-client — page "mon compte" /account

**Files:**
- Modify: `CLIENT/config/ClientProperties.java`
- Modify: `CLIENT_RES/application.yml`
- Create: `CLIENT/web/AccountController.java`
- Create: `CLIENT_RES/templates/account.html`
- Modify: `CLIENT_RES/templates/layout.html`

- [ ] **Step 1 : Étendre ClientProperties avec keycloakAccountUrl**

Remplacer le contenu de `ClientProperties.java` :

```java
package com.mr486.msplatform.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Catalogue des resources exposées par l'UI CRUD (injecté par le générateur dans application.yml). */
@ConfigurationProperties(prefix = "client")
public record ClientProperties(List<ResourceEntry> resources, String keycloakAccountUrl) {

    public record ResourceEntry(String serviceName, String routePrefix, String label, String role) {}
}
```

- [ ] **Step 2 : Ajouter keycloak-account-url dans application.yml**

Ajouter à la fin du fichier `application.yml` (après la section `client.resources`) :

```yaml
  keycloak-account-url: ${KEYCLOAK_ACCOUNT_URL:http://localhost:8089/realms/ms-platform/account}
```

Le fichier doit ressembler à :

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
  keycloak-account-url: ${KEYCLOAK_ACCOUNT_URL:http://localhost:8089/realms/ms-platform/account}
```

- [ ] **Step 3 : Créer AccountController.java**

```java
package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.config.ClientProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AccountController {

    private final ClientProperties clientProperties;

    public AccountController(ClientProperties clientProperties) {
        this.clientProperties = clientProperties;
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        model.addAttribute("roles", roles);
        model.addAttribute("keycloakAccountUrl", clientProperties.keycloakAccountUrl());
        return "account";
    }
}
```

- [ ] **Step 4 : Créer account.html**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Mon compte — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <h1>Mon compte</h1>
  <p><strong>Nom d'utilisateur :</strong> <span th:text="${username}">user</span></p>
  <p><strong>Rôles :</strong></p>
  <ul>
    <li th:each="role : ${roles}" th:text="${role}">ROLE</li>
  </ul>
  <p>
    <a th:href="${keycloakAccountUrl}" target="_blank" rel="noopener noreferrer">
      Changer mon mot de passe (Keycloak)
    </a>
  </p>
</main>
</body>
</html>
```

- [ ] **Step 5 : Modifier layout.html — ajouter lien "Mon compte"**

Remplacer le contenu de `layout.html` :

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<body>
<header th:fragment="header" class="topbar">
  <span class="brand">ms-client</span>
  <nav>
    <a th:href="@{/}">Accueil</a>
    <a th:href="@{/account}">Mon compte</a>
  </nav>
</header>
</body>
</html>
```

- [ ] **Step 6 : Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/config/ClientProperties.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/application.yml \
        src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/web/AccountController.java \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/account.html \
        src/main/resources/templates/ms-platform/ms-client/src/main/resources/templates/layout.html
git commit -m "feat(ms-client): page /account (mon compte + lien reset password Keycloak)"
```

---

### Task 9 : admin-application — protection serveur auto-suppression

**Files:**
- Modify: `ADMIN/web/UsersController.java`
- Modify: `ADMIN_RES/templates/users.html`

- [ ] **Step 1 : Modifier UsersController.java — méthode delete()**

Remplacer la méthode `delete()` existante :

```java
    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable String id) {
        try {
            keycloakAdminClient.deleteUser(id);
            return "redirect:/users";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }
```

par :

```java
    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable String id, Authentication authentication) {
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            if (user != null && user.username().equals(authentication.getName())) {
                return "redirect:/users?error=self";
            }
            keycloakAdminClient.deleteUser(id);
            return "redirect:/users";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }
```

Ajouter l'import manquant en tête de fichier si absent :

```java
import com.mr486.msplatform.adminapp.dto.KeycloakUser;
```

- [ ] **Step 2 : Modifier users.html — message d'erreur self**

Ajouter après le message `error=conflict` existant :

```html
  <p th:if="${param.error != null and param.error[0] == 'self'}" class="error">Vous ne pouvez pas supprimer votre propre compte.</p>
```

La section des messages d'erreur doit ressembler à :

```html
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <p th:if="${param.error != null and param.error[0] == 'conflict'}" class="error">Nom d'utilisateur déjà pris.</p>
  <p th:if="${param.error != null and param.error[0] == 'self'}" class="error">Vous ne pouvez pas supprimer votre propre compte.</p>
  <p th:if="${param.error != null and param.error[0] != 'conflict' and param.error[0] != 'self'}" class="error">Keycloak indisponible.</p>
```

Note : la dernière ligne remplace le `param.error[0] != 'conflict'` existant pour exclure aussi `'self'`.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html
git commit -m "feat(admin-application): protection serveur auto-suppression ADMIN"
```

---

### Task 10 : admin-application — protection serveur auto-modification de rôles

**Files:**
- Modify: `ADMIN/web/RolesController.java`
- Modify: `ADMIN_RES/templates/roles.html`

- [ ] **Step 1 : Modifier RolesController.java — méthodes add() et remove()**

Remplacer les méthodes `add()` et `remove()` existantes :

```java
    @PostMapping("/users/{id}/roles/add")
    public String add(@PathVariable String id, @RequestParam String roleName) {
        try {
            keycloakAdminClient.addRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }

    @PostMapping("/users/{id}/roles/remove")
    public String remove(@PathVariable String id, @RequestParam String roleName) {
        try {
            keycloakAdminClient.removeRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }
```

par :

```java
    @PostMapping("/users/{id}/roles/add")
    public String add(@PathVariable String id, @RequestParam String roleName,
                      Authentication authentication) {
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            if (user != null && user.username().equals(authentication.getName())) {
                return "redirect:/users/" + id + "/roles?error=self";
            }
            keycloakAdminClient.addRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }

    @PostMapping("/users/{id}/roles/remove")
    public String remove(@PathVariable String id, @RequestParam String roleName,
                         Authentication authentication) {
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            if (user != null && user.username().equals(authentication.getName())) {
                return "redirect:/users/" + id + "/roles?error=self";
            }
            keycloakAdminClient.removeRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }
```

Ajouter l'import si absent :

```java
import com.mr486.msplatform.adminapp.dto.KeycloakUser;
```

- [ ] **Step 2 : Modifier roles.html — masquage des boutons + message d'erreur**

Remplacer le bloc `<h2>Rôles actuels</h2>` et `<h2>Assigner un rôle</h2>` entiers :

```html
    <h2>Rôles actuels</h2>
    <ul>
      <li th:each="r : ${userRoles}">
        <span th:text="${r.name}">ROLE</span>
        <form th:unless="${r.name == 'ADMIN' and user.username == currentUsername}"
              th:action="@{/users/{id}/roles/remove(id=${userId})}" method="post" style="display:inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <input type="hidden" name="roleName" th:value="${r.name}"/>
          <button type="submit">Retirer</button>
        </form>
      </li>
    </ul>

    <h2>Assigner un rôle</h2>
    <ul>
      <li th:each="r : ${assignableRoles}">
        <span th:text="${r.name}">ROLE</span>
        <form th:action="@{/users/{id}/roles/add(id=${userId})}" method="post" style="display:inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <input type="hidden" name="roleName" th:value="${r.name}"/>
          <button type="submit">Assigner</button>
        </form>
      </li>
    </ul>
```

par :

```html
    <p th:if="${param.error != null and param.error[0] == 'self'}" class="error">
      Vous ne pouvez pas modifier vos propres rôles.
    </p>

    <h2>Rôles actuels</h2>
    <ul>
      <li th:each="r : ${userRoles}">
        <span th:text="${r.name}">ROLE</span>
        <form th:unless="${user.username == currentUsername}"
              th:action="@{/users/{id}/roles/remove(id=${userId})}" method="post" style="display:inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <input type="hidden" name="roleName" th:value="${r.name}"/>
          <button type="submit">Retirer</button>
        </form>
      </li>
    </ul>

    <h2>Assigner un rôle</h2>
    <ul>
      <li th:each="r : ${assignableRoles}">
        <span th:text="${r.name}">ROLE</span>
        <form th:unless="${user.username == currentUsername}"
              th:action="@{/users/{id}/roles/add(id=${userId})}" method="post" style="display:inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <input type="hidden" name="roleName" th:value="${r.name}"/>
          <button type="submit">Assigner</button>
        </form>
      </li>
    </ul>
```

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/RolesController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/roles.html
git commit -m "feat(admin-application): protection serveur auto-modification rôles ADMIN"
```

---

### Task 11 : TemplateLoaderTest — mise à jour de la parité (173 → 177)

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

4 nouveaux fichiers template ajoutés :
1. `service-a/.../service/ResourceNotFoundException.java`
2. `ms-client/.../web/PublicController.java`
3. `ms-client/.../web/AccountController.java`
4. `ms-client/src/main/resources/templates/resource-edit.html`

- [ ] **Step 1 : Mettre à jour le count**

Dans `TemplateLoaderTest.java`, remplacer :

```java
        assertThat(loader.load()).hasSize(173);
```

par :

```java
        assertThat(loader.load()).hasSize(177);
```

- [ ] **Step 2 : Lancer les tests générateur**

```bash
mvn test -pl . -Dtest=TemplateLoaderTest -q
```

Expected : `BUILD SUCCESS`

- [ ] **Step 3 : Lancer la suite complète**

```bash
mvn test -q
```

Expected : `BUILD SUCCESS`

- [ ] **Step 4 : Commit**

```bash
git add src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "test(generator): parité template 173 → 177 (ResourceNotFoundException, PublicController, AccountController, resource-edit.html)"
```
