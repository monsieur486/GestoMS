# Phase 3c — `admin-application` gestion des rôles — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre d'assigner / retirer des rôles realm à un utilisateur depuis `admin-application`, via une page `/users/{id}/roles` et la Keycloak Admin API.

**Architecture:** `KeycloakAdminClient` gagne `getUser`, `listRealmRoles` (built-ins filtrés), `listUserRealmRoles`, `addRealmRole`/`removeRealmRole` (résolution `GET /roles/{name}` → POST/DELETE `role-mappings/realm` corps `[{id,name}]`). `RolesController` rend `/users/{id}/roles` (POST-redirect-GET) avec garde anti-retrait-ADMIN-de-soi. `roles.html` liste actuels (retrait) + assignables (ajout). Lien « rôles » ajouté à `/users`.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Thymeleaf, RestTemplate ; tests embarqués JUnit5 + Mockito.

---

## Spec
`docs/superpowers/specs/2026-05-30-admin-application-roles-design.md`

## Carte des fichiers

Racine : `src/main/resources/templates/ms-platform/admin-application/`.
- **Nouveaux (3) :** `…/adminapp/dto/KeycloakRole.java`, `…/adminapp/web/RolesController.java`, `src/main/resources/templates/roles.html`.
- **Modifiés (3) :** `…/adminapp/service/KeycloakAdminClient.java` (+méthodes rôles), `src/main/resources/templates/users.html` (+lien rôles), `…/adminapp/service/KeycloakAdminClientTest.java` (+tests).
- **Tests générateur :** `TemplateLoaderTest.java` (parité 169 → 170 → 172).

## Conventions
- Code/templates NON compilés par le générateur → **oracle = `mvn package` du projet généré** (Task 3).
- **Commits verts** : parité mise à jour dans chaque commit ajoutant un fichier.
- POST-redirect-GET ; CSRF activé ; garde anti-verrouillage UI (`role==ADMIN && user==soi`).
- Représentations rôle = `{id,name}` (suffisant pour l'API role-mapping Keycloak).

---

## Task 1 : `KeycloakRole` + méthodes rôles dans `KeycloakAdminClient` + tests — parité 170

**Files:**
- Create: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/dto/KeycloakRole.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `KeycloakRole.java`**

```java
package com.mr486.msplatform.adminapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name) {}
```

- [ ] **Step 2: Imports dans `KeycloakAdminClient.java`**

Après `import com.mr486.msplatform.adminapp.dto.KeycloakUser;`, ajouter :
```java
import com.mr486.msplatform.adminapp.dto.KeycloakRole;
```
Après `import java.util.HashMap;`, ajouter :
```java
import java.util.ArrayList;
```

- [ ] **Step 3: Ajouter les méthodes rôles** (dans `KeycloakAdminClient.java`, juste après `deleteUser(...)` et avant `adminToken()`)

```java
    public KeycloakUser getUser(String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakUser> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakUser.class);
            return resp.getBody();
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public List<KeycloakRole> listRealmRoles() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakRole[]> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/roles",
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole[].class);
            KeycloakRole[] body = resp.getBody();
            if (body == null) return List.of();
            List<KeycloakRole> result = new ArrayList<>();
            for (KeycloakRole r : body) {
                String n = r.name();
                if (n == null || n.equals("offline_access") || n.equals("uma_authorization") || n.startsWith("default-roles-")) {
                    continue;
                }
                result.add(r);
            }
            return result;
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public List<KeycloakRole> listUserRealmRoles(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakRole[]> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole[].class);
            KeycloakRole[] body = resp.getBody();
            return body == null ? List.of() : List.of(body);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void addRealmRole(String userId, String roleName) {
        KeycloakRole role = roleByName(roleName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        try {
            restTemplate.postForEntity(
                    internalUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    new HttpEntity<>(List.of(role), headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void removeRealmRole(String userId, String roleName) {
        KeycloakRole role = roleByName(roleName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        try {
            restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.DELETE, new HttpEntity<>(List.of(role), headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    private KeycloakRole roleByName(String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakRole> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/roles/" + name,
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole.class);
            KeycloakRole role = resp.getBody();
            if (role == null) {
                throw new KeycloakUnavailableException();
            }
            return role;
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }
```

- [ ] **Step 4: Import de test**

Dans `KeycloakAdminClientTest.java`, après `import com.mr486.msplatform.adminapp.dto.KeycloakUser;`, ajouter :
```java
import com.mr486.msplatform.adminapp.dto.KeycloakRole;
```

- [ ] **Step 5: Ajouter 4 tests** (avant la dernière `}` de la classe)

```java
    @Test
    void list_realm_roles_filters_builtins() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        KeycloakRole[] roles = {
                new KeycloakRole("a", "ADMIN"),
                new KeycloakRole("b", "USER_SERVICE_A"),
                new KeycloakRole("c", "offline_access"),
                new KeycloakRole("d", "uma_authorization"),
                new KeycloakRole("e", "default-roles-ms-realm")
        };
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/roles"), eq(HttpMethod.GET), any(), eq(KeycloakRole[].class)))
                .thenReturn(ResponseEntity.ok(roles));

        assertThat(client.listRealmRoles()).extracting(KeycloakRole::name).containsExactly("ADMIN", "USER_SERVICE_A");
    }

    @Test
    void list_user_realm_roles_parses() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/users/uid/role-mappings/realm"), eq(HttpMethod.GET), any(), eq(KeycloakRole[].class)))
                .thenReturn(ResponseEntity.ok(new KeycloakRole[]{new KeycloakRole("a", "ADMIN")}));

        assertThat(client.listUserRealmRoles("uid")).extracting(KeycloakRole::name).containsExactly("ADMIN");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void add_realm_role_resolves_then_posts_representation() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/roles/ADMIN"), eq(HttpMethod.GET), any(), eq(KeycloakRole.class)))
                .thenReturn(ResponseEntity.ok(new KeycloakRole("rid", "ADMIN")));

        client.addRealmRole("uid", "ADMIN");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("/users/uid/role-mappings/realm"), captor.capture(), eq(Void.class));
        List body = (List) captor.getValue().getBody();
        assertThat(body).hasSize(1);
        KeycloakRole sent = (KeycloakRole) body.get(0);
        assertThat(sent.id()).isEqualTo("rid");
        assertThat(sent.name()).isEqualTo("ADMIN");
    }

    @Test
    void remove_realm_role_sends_delete_with_representation() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/roles/USER_BATCH"), eq(HttpMethod.GET), any(), eq(KeycloakRole.class)))
                .thenReturn(ResponseEntity.ok(new KeycloakRole("rid", "USER_BATCH")));

        client.removeRealmRole("uid", "USER_BATCH");

        verify(restTemplate).exchange(contains("/users/uid/role-mappings/realm"),
                eq(HttpMethod.DELETE), any(), eq(Void.class));
    }
```

- [ ] **Step 6: Parité `TemplateLoaderTest` (→ 170)**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 169 ... but was: 170` (1 nouveau fichier : KeycloakRole).
Remplacer `hasSize(169)` → `hasSize(170)`.

- [ ] **Step 7: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/dto/KeycloakRole.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java \
        src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): admin-application KeycloakAdminClient realm-role mapping ops"
```

---

## Task 2 : `RolesController` + `roles.html` + lien dans `users.html` — parité 172

**Files:**
- Create: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/RolesController.java`
- Create: `src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/roles.html`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `RolesController.java`**

```java
package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.dto.KeycloakRole;
import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class RolesController {

    private final KeycloakAdminClient keycloakAdminClient;

    public RolesController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @GetMapping("/users/{id}/roles")
    public String roles(@PathVariable String id, Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        model.addAttribute("userId", id);
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            List<KeycloakRole> userRoles = keycloakAdminClient.listUserRealmRoles(id);
            Set<String> assigned = userRoles.stream().map(KeycloakRole::name).collect(Collectors.toSet());
            List<KeycloakRole> assignable = keycloakAdminClient.listRealmRoles().stream()
                    .filter(r -> !assigned.contains(r.name()))
                    .toList();
            model.addAttribute("user", user);
            model.addAttribute("userRoles", userRoles);
            model.addAttribute("assignableRoles", assignable);
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "roles";
    }

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
}
```

- [ ] **Step 2: `roles.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Rôles — admin-application</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/users}">← Utilisateurs</a></p>
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <p th:if="${param.error}" class="error">Keycloak indisponible.</p>
  <div th:if="${user}">
    <h1>Rôles de <span th:text="${user.username}">user</span></h1>

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
  </div>
</main>
</body>
</html>
```

- [ ] **Step 3: `users.html` — lien « rôles » dans la colonne actions**

Repérer le `<td>` actions :
```html
        <td>
          <form th:if="${u.username != currentUsername}" th:action="@{/users/{id}/delete(id=${u.id})}" method="post"
                onsubmit="return confirm('Supprimer cet utilisateur ?');">
```
et insérer une ligne juste après `        <td>` (avant le `<form ... delete ...`) :
```html
          <a th:href="@{/users/{id}/roles(id=${u.id})}">rôles</a>
```

- [ ] **Step 4: Parité `TemplateLoaderTest` (→ 172)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 170 ... but was: 172` (2 nouveaux : RolesController, roles.html).
Remplacer `hasSize(170)` → `hasSize(172)`.

- [ ] **Step 5: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/RolesController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/roles.html \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): admin-application /users/{id}/roles page (assign/remove, self-guard)"
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1: Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 172).

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

- [ ] **Step 4: Générer + vérifier + compiler le module généré (tests embarqués)**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refc.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refcx && mkdir -p /tmp/refcx && unzip -q /tmp/refc.zip -d /tmp/refcx && echo UNZIPPED
echo "=== fichiers 3c présents ==="
ls /tmp/refcx/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/dto/KeycloakRole.java \
   /tmp/refcx/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/web/RolesController.java \
   /tmp/refcx/ms-platform/admin-application/src/main/resources/templates/roles.html 2>&1
echo -n "lien rôles dans users.html="; grep -c '/users/{id}/roles' /tmp/refcx/ms-platform/admin-application/src/main/resources/templates/users.html
echo -n "addRealmRole="; grep -c 'public void addRealmRole' /tmp/refcx/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java
cd /tmp/refcx/ms-platform && mvn -pl admin-application -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|KeycloakAdminClientTest|ERROR.*\.java' | head -20
```
Expected : `HTTP=200`, `UNZIPPED`, les 3 fichiers listés, `1` (lien), `1` (addRealmRole) ; `KeycloakAdminClientTest` **Tests run: 9** verts ; `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 5: Compose valide + arrêt + arbre propre**

```bash
cd /tmp/refcx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; arbre git propre (tout commité aux Tasks 1–2).

---

## Recovery
- `git log --oneline -3` — commits passés (rôles client+tests ; page rôles).
- `grep -c 'public void addRealmRole\|public void removeRealmRole' src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java` → `2` si Task 1 faite.
- `grep hasSize src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` → `172` si Tasks 1+2 faites.
- `mvn test` SUCCESS → générateur vert ; oracle module = `mvn -pl admin-application -am package` du projet généré (Task 3, `KeycloakAdminClientTest` × 9).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Création/suppression de rôles realm, rôles client/composites/groupes, garde serveur stricte anti-verrouillage.
