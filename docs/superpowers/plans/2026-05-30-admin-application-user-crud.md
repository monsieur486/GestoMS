# Phase 3b — `admin-application` création / suppression d'utilisateurs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Étendre `admin-application` (3a) pour créer (mot de passe permanent) et supprimer des utilisateurs Keycloak depuis la page `/users`.

**Architecture:** `KeycloakAdminClient` gagne `createUser` (POST users + credentials temporary=false) et `deleteUser` (DELETE users/{id}). `UsersController` gagne `POST /users` + `POST /users/{id}/delete` (POST-redirect-GET) et expose `currentUsername`. `users.html` gagne un formulaire de création + un bouton supprimer par ligne (masqué pour soi, confirm JS). Que des modifications de fichiers 3a — parité inchangée (169), aucun changement générateur.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Thymeleaf, RestTemplate ; tests embarqués JUnit5 + Mockito.

---

## Spec
`docs/superpowers/specs/2026-05-30-admin-application-user-crud-design.md`

## Carte des fichiers (tous modifiés — aucun nouveau)

Racine : `src/main/resources/templates/ms-platform/admin-application/`.
- `src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java` — `+ createUser`, `+ deleteUser`, `+ UserConflictException`.
- `src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java` — `currentUsername` + `POST /users` + `POST /users/{id}/delete`.
- `src/main/resources/templates/users.html` — formulaire création + bouton supprimer conditionnel + bandeaux d'erreur.
- `src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java` — `+` 3 tests.

**Générateur :** aucun changement. `TemplateLoaderTest` inchangé (169).

## Conventions
- Code/templates NON compilés par le générateur → **oracle = `mvn package` du projet généré** (Task 3), qui exécute `KeycloakAdminClientTest`.
- Aucun fichier ajouté → **parité 169 inchangée** ; ne PAS toucher `TemplateLoaderTest`.
- POST-redirect-GET partout ; CSRF activé (formulaires portent le jeton) ; garde anti-auto-suppression au niveau UI.

---

## Task 1 : `KeycloakAdminClient.createUser` + `deleteUser` + tests embarqués

**Files:**
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java`

- [ ] **Step 1: Ajouter les imports manquants à `KeycloakAdminClient.java`**

Après `import org.springframework.web.client.RestTemplate;`, ajouter :
```java
import org.springframework.web.client.HttpClientErrorException;
```
Et remplacer `import java.util.List;` + `import java.util.Map;` par :
```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;
```

- [ ] **Step 2: Ajouter `createUser`, `deleteUser` et `UserConflictException`**

Dans `KeycloakAdminClient.java`, juste après la méthode `listUsers()` (avant `adminToken()`), insérer :
```java
    public void createUser(String username, String email, String firstName, String lastName, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", false);
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("enabled", true);
        body.put("credentials", List.of(credential));
        try {
            restTemplate.postForEntity(internalUrl + "/admin/realms/" + realm + "/users",
                    new HttpEntity<>(body, headers), Void.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new UserConflictException();
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void deleteUser(String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            restTemplate.exchange(internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }
```
Puis, juste après la ligne `public static class KeycloakUnavailableException extends RuntimeException {}`, ajouter :
```java

    public static class UserConflictException extends RuntimeException {}
```

- [ ] **Step 3: Ajouter les imports de test à `KeycloakAdminClientTest.java`**

Après `import org.mockito.Mockito;`, ajouter :
```java
import org.mockito.ArgumentCaptor;
```
Après `import org.springframework.http.HttpMethod;`, ajouter :
```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
```
Après `import org.springframework.web.client.RestTemplate;`, ajouter :
```java
import org.springframework.web.client.HttpClientErrorException;
```
Après `import java.util.Map;`, ajouter :
```java
import java.util.List;
```
Après `import static org.mockito.Mockito.when;`, ajouter :
```java
import static org.mockito.Mockito.verify;
```

- [ ] **Step 4: Ajouter 3 tests** (avant la dernière `}` de la classe)

```java
    @Test
    @SuppressWarnings("rawtypes")
    void create_user_posts_representation_with_permanent_password() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));

        client.createUser("carol", "carol@x.io", "Ca", "Rol", "secret");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("/admin/realms/ms-realm/users"), captor.capture(), eq(Void.class));
        Map body = (Map) captor.getValue().getBody();
        assertThat(body.get("username")).isEqualTo("carol");
        assertThat(body.get("enabled")).isEqualTo(true);
        List creds = (List) body.get("credentials");
        Map cred = (Map) creds.get(0);
        assertThat(cred.get("temporary")).isEqualTo(false);
        assertThat(cred.get("value")).isEqualTo("secret");
    }

    @Test
    void create_user_conflict_maps_to_user_conflict_exception() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.postForEntity(contains("/admin/realms/ms-realm/users"), any(), eq(Void.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "409", null, null, null));

        assertThatThrownBy(() -> client.createUser("dup", "d@x.io", "D", "Up", "pw"))
                .isInstanceOf(KeycloakAdminClient.UserConflictException.class);
    }

    @Test
    void delete_user_sends_delete_to_user_id() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));

        client.deleteUser("123");

        verify(restTemplate).exchange(contains("/admin/realms/ms-realm/users/123"),
                eq(HttpMethod.DELETE), any(), eq(Void.class));
    }
```

- [ ] **Step 5: Suite générateur verte (fichiers modifiés, parité inchangée 169)**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (169 — aucun fichier ajouté). Le `KeycloakAdminClientTest` ne tourne pas ici (template) — vérifié en Task 3. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java \
        src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java
git commit -m "feat(template): admin-application KeycloakAdminClient create/delete user"
```

---

## Task 2 : `UsersController` (create/delete) + `users.html`

**Files:**
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html`

- [ ] **Step 1: Réécrire `UsersController.java`**

```java
package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UsersController {

    private final KeycloakAdminClient keycloakAdminClient;

    public UsersController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @GetMapping("/users")
    public String users(Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        try {
            model.addAttribute("users", keycloakAdminClient.listUsers());
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "users";
    }

    @PostMapping("/users")
    public String create(@RequestParam String username, @RequestParam(required = false) String email,
                         @RequestParam(required = false) String firstName, @RequestParam(required = false) String lastName,
                         @RequestParam String password) {
        try {
            keycloakAdminClient.createUser(username, email, firstName, lastName, password);
            return "redirect:/users";
        } catch (KeycloakAdminClient.UserConflictException e) {
            return "redirect:/users?error=conflict";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }

    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable String id) {
        try {
            keycloakAdminClient.deleteUser(id);
            return "redirect:/users";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }
}
```

- [ ] **Step 2: Réécrire `users.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Utilisateurs — admin-application</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/}">← Accueil</a></p>
  <h1>Utilisateurs</h1>
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <p th:if="${param.error != null and param.error[0] == 'conflict'}" class="error">Nom d'utilisateur déjà pris.</p>
  <p th:if="${param.error != null and param.error[0] != 'conflict'}" class="error">Keycloak indisponible.</p>
  <table th:if="${users}">
    <thead><tr><th>username</th><th>email</th><th>prénom</th><th>nom</th><th>actif</th><th>actions</th></tr></thead>
    <tbody>
      <tr th:each="u : ${users}">
        <td th:text="${u.username}">user</td>
        <td th:text="${u.email}">email</td>
        <td th:text="${u.firstName}">first</td>
        <td th:text="${u.lastName}">last</td>
        <td th:text="${u.enabled}">true</td>
        <td>
          <form th:if="${u.username != currentUsername}" th:action="@{/users/{id}/delete(id=${u.id})}" method="post"
                onsubmit="return confirm('Supprimer cet utilisateur ?');">
            <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
            <button type="submit">Supprimer</button>
          </form>
        </td>
      </tr>
    </tbody>
  </table>
  <h2>Créer un utilisateur</h2>
  <form th:action="@{/users}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <label>Username <input type="text" name="username" required/></label>
    <label>Email <input type="email" name="email"/></label>
    <label>Prénom <input type="text" name="firstName"/></label>
    <label>Nom <input type="text" name="lastName"/></label>
    <label>Mot de passe <input type="password" name="password" required/></label>
    <button type="submit">Créer</button>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 3: Suite générateur verte (parité inchangée 169)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (169).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html
git commit -m "feat(template): admin-application /users create form + per-row delete (self-guard)"
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1: Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 169).

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

- [ ] **Step 4: Générer + compiler le module généré + exécuter les tests embarqués (5)**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refb.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refbx && mkdir -p /tmp/refbx && unzip -q /tmp/refb.zip -d /tmp/refbx && echo UNZIPPED
echo "=== create/delete présents dans le généré ==="
grep -c 'public void createUser' /tmp/refbx/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java
grep -c 'public void deleteUser' /tmp/refbx/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java
grep -c '/users/{id}/delete' /tmp/refbx/ms-platform/admin-application/src/main/resources/templates/users.html
grep -c 'Créer un utilisateur' /tmp/refbx/ms-platform/admin-application/src/main/resources/templates/users.html
cd /tmp/refbx/ms-platform && mvn -pl admin-application -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|KeycloakAdminClientTest|ERROR.*\.java' | head -20
```
Expected : `HTTP=200`, `UNZIPPED`, puis `1`,`1`,`1`,`1` ; `KeycloakAdminClientTest` **Tests run: 5** verts ; `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 5: Compose valide + arrêt + arbre propre**

```bash
cd /tmp/refbx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; arbre git propre (tout commité aux Tasks 1–2).

---

## Recovery
- `git log --oneline -3` — commits passés (client create/delete ; controller+vue).
- `grep -c 'public void createUser\|public void deleteUser' src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java` → `2` si Task 1 faite.
- `grep -c '/users/{id}/delete' src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html` → `1` si Task 2 faite.
- `mvn test` SUCCESS → générateur vert (parité 169 inchangée) ; oracle module = `mvn -pl admin-application -am package` du projet généré (Task 3, exécute `KeycloakAdminClientTest` × 5).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Édition user / reset password / activation, recherche/pagination, gestion des rôles (3c), garde serveur stricte anti-auto-suppression.
