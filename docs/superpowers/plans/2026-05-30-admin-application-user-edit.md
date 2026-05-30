# Phase 4 — `admin-application` édition d'utilisateur + reset password — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre d'éditer un utilisateur (email/prénom/nom/actif) et de réinitialiser son mot de passe depuis `admin-application`, via une page `/users/{id}/edit` et la Keycloak Admin API.

**Architecture:** `KeycloakAdminClient` gagne `updateUser` (GET représentation `Map` → fusion → PUT, préserve `username`) et `resetPassword` (PUT `reset-password`, `temporary=false`). `UsersController` gagne `GET /users/{id}/edit` + `POST /users/{id}/edit` + `POST /users/{id}/password` (POST-redirect-GET). `edit.html` porte les deux formulaires avec garde anti-auto-désactivation. Lien « éditer » ajouté à `/users`.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Thymeleaf, RestTemplate ; tests embarqués JUnit5 + Mockito.

---

## Spec
`docs/superpowers/specs/2026-05-30-admin-application-user-edit-design.md`

## Carte des fichiers

Racine : `src/main/resources/templates/ms-platform/admin-application/`.
- **Nouveau (1) :** `src/main/resources/templates/edit.html`.
- **Modifiés (3) :** `…/adminapp/service/KeycloakAdminClient.java` (+updateUser/resetPassword), `…/adminapp/web/UsersController.java` (+3 endpoints), `src/main/resources/templates/users.html` (+lien éditer), `…/adminapp/service/KeycloakAdminClientTest.java` (+2 tests).
- **Tests générateur :** `TemplateLoaderTest.java` (parité 172 → 173).

## Conventions
- Code/templates NON compilés par le générateur → **oracle = `mvn package` du projet généré** (Task 3).
- **Commits verts** : parité mise à jour dans le commit qui ajoute `edit.html`.
- POST-redirect-GET ; CSRF activé ; garde anti-auto-désactivation UI.
- `updateUser` GET-puis-PUT pour **préserver** `username` et les autres attributs Keycloak.
- **Piège test** : le GET de `updateUser` est mocké avec une `HashMap` **mutable** (pas `Map.of`, car `updateUser` fait `put`).

---

## Task 1 : `updateUser` + `resetPassword` dans `KeycloakAdminClient` + tests

**Files:**
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java`

- [ ] **Step 1: Ajouter `updateUser` et `resetPassword`** (dans `KeycloakAdminClient.java`, juste après la méthode `deleteUser(...)` et avant `getUser(...)`)

```java
    @SuppressWarnings("rawtypes")
    public void updateUser(String id, String email, String firstName, String lastName, boolean enabled) {
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(adminToken());
        try {
            ResponseEntity<Map> getResp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.GET, new HttpEntity<>(getHeaders), Map.class);
            Map body = getResp.getBody();
            if (body == null) {
                throw new KeycloakUnavailableException();
            }
            body.put("email", email);
            body.put("firstName", firstName);
            body.put("lastName", lastName);
            body.put("enabled", enabled);
            HttpHeaders putHeaders = new HttpHeaders();
            putHeaders.setContentType(MediaType.APPLICATION_JSON);
            putHeaders.setBearerAuth(adminToken());
            restTemplate.exchange(internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.PUT, new HttpEntity<>(body, putHeaders), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void resetPassword(String id, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", false);
        try {
            restTemplate.exchange(internalUrl + "/admin/realms/" + realm + "/users/" + id + "/reset-password",
                    HttpMethod.PUT, new HttpEntity<>(credential, headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }
```
(Aucun nouvel import : `HttpHeaders`, `HttpMethod`, `MediaType`, `HttpEntity`, `ResponseEntity`, `Map` déjà présents.)

- [ ] **Step 2: Ajouter l'import `HashMap` au test**

Dans `KeycloakAdminClientTest.java`, après `import java.util.List;`, ajouter :
```java
import java.util.HashMap;
```

- [ ] **Step 3: Ajouter 2 tests** (avant la dernière `}` de la classe)

```java
    @Test
    @SuppressWarnings("rawtypes")
    void update_user_merges_fields_and_preserves_username() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        Map<String, Object> existing = new HashMap<>();
        existing.put("username", "carol");
        existing.put("email", "old@x.io");
        existing.put("enabled", true);
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/users/uid"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(existing));

        client.updateUser("uid", "new@x.io", "Ca", "Rol", false);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/admin/realms/ms-realm/users/uid"), eq(HttpMethod.PUT), captor.capture(), eq(Void.class));
        Map put = (Map) captor.getValue().getBody();
        assertThat(put.get("username")).isEqualTo("carol");   // préservé (GET puis PUT)
        assertThat(put.get("email")).isEqualTo("new@x.io");   // modifié
        assertThat(put.get("firstName")).isEqualTo("Ca");
        assertThat(put.get("enabled")).isEqualTo(false);      // modifié
    }

    @Test
    @SuppressWarnings("rawtypes")
    void reset_password_puts_permanent_credential() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));

        client.resetPassword("uid", "secret");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/users/uid/reset-password"), eq(HttpMethod.PUT), captor.capture(), eq(Void.class));
        Map body = (Map) captor.getValue().getBody();
        assertThat(body.get("type")).isEqualTo("password");
        assertThat(body.get("value")).isEqualTo("secret");
        assertThat(body.get("temporary")).isEqualTo(false);
    }
```

- [ ] **Step 4: Suite générateur verte (fichiers modifiés, parité inchangée 172)**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (172 — aucun fichier ajouté). Le `KeycloakAdminClientTest` ne tourne pas ici (template) — vérifié en Task 3. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java \
        src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java
git commit -m "feat(template): admin-application KeycloakAdminClient updateUser + resetPassword"
```

---

## Task 2 : `UsersController` (edit/password) + `edit.html` + lien dans `users.html` — parité 173

**Files:**
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java`
- Create: `src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/edit.html`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: Ajouter 3 endpoints à `UsersController.java`** (avant la dernière `}` de la classe)

```java
    @GetMapping("/users/{id}/edit")
    public String editForm(@PathVariable String id, Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        try {
            model.addAttribute("user", keycloakAdminClient.getUser(id));
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "edit";
    }

    @PostMapping("/users/{id}/edit")
    public String edit(@PathVariable String id, @RequestParam(required = false) String email,
                       @RequestParam(required = false) String firstName, @RequestParam(required = false) String lastName,
                       @RequestParam(defaultValue = "false") boolean enabled) {
        try {
            keycloakAdminClient.updateUser(id, email, firstName, lastName, enabled);
            return "redirect:/users/" + id + "/edit?updated";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/edit?error";
        }
    }

    @PostMapping("/users/{id}/password")
    public String resetPassword(@PathVariable String id, @RequestParam String password) {
        try {
            keycloakAdminClient.resetPassword(id, password);
            return "redirect:/users/" + id + "/edit?pwd";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/edit?error";
        }
    }
```
(Imports déjà présents : `Authentication`, `GetMapping`, `PathVariable`, `PostMapping`, `RequestParam`, `Controller`, `Model`.)

- [ ] **Step 2: Créer `edit.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Éditer un utilisateur — admin-application</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <p><a th:href="@{/users}">← Utilisateurs</a></p>
  <p th:if="${param.updated}" class="info">Utilisateur mis à jour.</p>
  <p th:if="${param.pwd}" class="info">Mot de passe réinitialisé.</p>
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <p th:if="${param.error}" class="error">Keycloak indisponible.</p>
  <div th:if="${user}">
    <h1>Éditer <span th:text="${user.username}">user</span></h1>
    <form th:action="@{/users/{id}/edit(id=${user.id})}" method="post">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <label>Username <input type="text" th:value="${user.username}" disabled/></label>
      <label>Email <input type="email" name="email" th:value="${user.email}"/></label>
      <label>Prénom <input type="text" name="firstName" th:value="${user.firstName}"/></label>
      <label>Nom <input type="text" name="lastName" th:value="${user.lastName}"/></label>
      <label th:if="${user.username == currentUsername}">
        Actif <input type="checkbox" checked disabled/>
        <input type="hidden" name="enabled" value="true"/>
      </label>
      <label th:unless="${user.username == currentUsername}">
        Actif <input type="checkbox" name="enabled" value="true" th:checked="${user.enabled}"/>
      </label>
      <button type="submit">Enregistrer</button>
    </form>

    <h2>Réinitialiser le mot de passe</h2>
    <form th:action="@{/users/{id}/password(id=${user.id})}" method="post">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <label>Nouveau mot de passe <input type="password" name="password" required/></label>
      <button type="submit">Réinitialiser</button>
    </form>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 3: `users.html` — lien « éditer » dans la colonne actions**

Repérer :
```html
        <td>
          <a th:href="@{/users/{id}/roles(id=${u.id})}">rôles</a>
```
et insérer une ligne juste après `        <td>` (avant le lien rôles) :
```html
          <a th:href="@{/users/{id}/edit(id=${u.id})}">éditer</a>
```

- [ ] **Step 4: Parité `TemplateLoaderTest` (→ 173)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 172 ... but was: 173` (1 nouveau : edit.html).
Remplacer `hasSize(172)` → `hasSize(173)`.

- [ ] **Step 5: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/edit.html \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): admin-application /users/{id}/edit page (update + reset password, self-guard)"
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1: Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 173).

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
  -o /tmp/refe.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refex && mkdir -p /tmp/refex && unzip -q /tmp/refe.zip -d /tmp/refex && echo UNZIPPED
echo "=== fichiers P4 ==="
ls /tmp/refex/ms-platform/admin-application/src/main/resources/templates/edit.html 2>&1
echo -n "updateUser="; grep -c 'public void updateUser' /tmp/refex/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java
echo -n "resetPassword="; grep -c 'public void resetPassword' /tmp/refex/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java
echo -n "lien éditer users.html="; grep -c '/users/{id}/edit' /tmp/refex/ms-platform/admin-application/src/main/resources/templates/users.html
cd /tmp/refex/ms-platform && mvn -pl admin-application -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|KeycloakAdminClientTest|ERROR.*\.java' | head -20
```
Expected : `HTTP=200`, `UNZIPPED`, `edit.html` listé, `1` (updateUser), `1` (resetPassword), `1` (lien) ; `KeycloakAdminClientTest` **Tests run: 11** verts ; `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 5: Compose valide + arrêt + arbre propre**

```bash
cd /tmp/refex/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; arbre git propre (tout commité aux Tasks 1–2 ; + le commit README docs déjà présent).

---

## Recovery
- `git log --oneline -4` — commits passés (client update/reset ; page edit).
- `grep -c 'public void updateUser\|public void resetPassword' src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java` → `2` si Task 1 faite.
- `grep -c '/users/{id}/edit' src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html` → `1` si Task 2 faite.
- `mvn test` SUCCESS → générateur vert ; oracle module = `mvn -pl admin-application -am package` du projet généré (Task 3, `KeycloakAdminClientTest` × 11).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Édition du username, rôles (3c), attributs custom/groupes, garde serveur stricte anti-auto-désactivation.
