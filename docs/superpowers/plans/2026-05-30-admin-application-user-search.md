# Phase 5 — `admin-application` recherche + pagination des utilisateurs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter une barre de recherche + une pagination (20/page) à la page `/users` d'`admin-application`, via les params Keycloak `search`/`first`/`max` et l'endpoint `/users/count`.

**Architecture:** `KeycloakAdminClient.listUsers()` devient `listUsers(String search, int first, int max)` (URL construite via `UriComponentsBuilder`, params URL-encodés) et gagne `countUsers(String search)`. `UsersController.users()` accepte `search` + `page`, calcule `first = page*20`, expose `totalPages`/`hasPrev`/`hasNext`. `users.html` gagne un **form GET** de recherche et un **pager** ; table + form de création + colonne actions inchangés.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Thymeleaf, RestTemplate ; tests embarqués JUnit5 + Mockito.

---

## Spec
`docs/superpowers/specs/2026-05-30-admin-application-user-search-design.md`

## Carte des fichiers

Racine template : `src/main/resources/templates/ms-platform/admin-application/`.
- **Modifiés (fichiers existants uniquement, AUCUN nouveau) :**
  - `…/adminapp/service/KeycloakAdminClient.java` (`listUsers` re-signé + `countUsers` + import `UriComponentsBuilder`)
  - `…/adminapp/web/UsersController.java` (`users(...)` : `search`/`page`/pagination)
  - `…/src/main/resources/templates/users.html` (form GET recherche + pager)
  - `…/adminapp/service/KeycloakAdminClientTest.java` (2 tests `listUsers` adaptés + 2 nouveaux tests)
- **Générateur : AUCUNE modification.** `TemplateLoaderTest` parité **173 inchangée** (aucun fichier ajouté → ne PAS toucher ce test).

## Conventions
- Code/templates NON compilés par le générateur → **oracle réel = `mvn -pl admin-application -am package` du projet généré** (Task 3).
- **Commits verts** à chaque tâche.
- `search`/`first`/`max` URL-encodés par `UriComponentsBuilder.encode()`.
- Form de recherche en **GET** (pas de CSRF) ; forms de mutation (create/delete) inchangés.
- **Piège du jar périmé** : rebuilder le jar (`mvn clean package`) AVANT de générer (Task 3) ; `pkill` et lancement en **commandes séparées** ; sandbox désactivé.
- `KeycloakAdminClientTest` ne s'exécute PAS dans le build générateur (c'est une ressource template) → vérifié seulement en Task 3 via le module généré.

---

## Task 1 : `KeycloakAdminClient` — `listUsers(search,first,max)` + `countUsers` + tests

**Files:**
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java`

- [ ] **Step 1 : Ajouter l'import `UriComponentsBuilder`** dans `KeycloakAdminClient.java`

Après la ligne `import org.springframework.web.client.RestTemplate;` (ligne 7), ajouter :
```java
import org.springframework.web.util.UriComponentsBuilder;
```
(`import org.springframework.http.*;` couvre déjà `HttpHeaders`/`HttpMethod`/`HttpEntity`/`ResponseEntity`/`MediaType`.)

- [ ] **Step 2 : Remplacer `listUsers()` par `listUsers(String search, int first, int max)` + ajouter `countUsers(String search)`**

Remplacer **intégralement** la méthode actuelle (de `public List<KeycloakUser> listUsers() {` jusqu'à son `}` fermant) par les **deux** méthodes suivantes :
```java
    public List<KeycloakUser> listUsers(String search, int first, int max) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(internalUrl + "/admin/realms/" + realm + "/users")
                .queryParam("first", first)
                .queryParam("max", max);
        if (search != null && !search.isBlank()) {
            builder.queryParam("search", search);
        }
        String url = builder.encode().toUriString();
        try {
            ResponseEntity<KeycloakUser[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), KeycloakUser[].class);
            KeycloakUser[] body = response.getBody();
            return body == null ? List.of() : List.of(body);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public int countUsers(String search) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(internalUrl + "/admin/realms/" + realm + "/users/count");
        if (search != null && !search.isBlank()) {
            builder.queryParam("search", search);
        }
        String url = builder.encode().toUriString();
        try {
            ResponseEntity<Integer> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Integer.class);
            Integer body = response.getBody();
            return body == null ? 0 : body;
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }
```
(`adminToken()` est appelé hors du `try` — sa `KeycloakUnavailableException` se propage ; le `catch`/re-throw garde le style des autres méthodes de la classe. Pas de `Collections` requis : on réutilise `List.of()`.)

- [ ] **Step 3 : Adapter les 2 tests `listUsers` existants** dans `KeycloakAdminClientTest.java`

Remplacer les **deux** appels `client.listUsers()` (sans argument) :
- dans `lists_users_after_obtaining_admin_token` : `assertThat(client.listUsers())` → `assertThat(client.listUsers(null, 0, 20))`
- dans `throws_when_token_missing` : `assertThatThrownBy(() -> client.listUsers())` → `assertThatThrownBy(() -> client.listUsers(null, 0, 20))`

(Le `restTemplate` mocké est `"http://kc"`/realm `"ms-realm"` → l'URL produite est `http://kc/admin/realms/ms-realm/users?first=0&max=20` ; le matcher `contains("/admin/realms/ms-realm/users")` reste valide.)

- [ ] **Step 4 : Ajouter 2 nouveaux tests** (avant la dernière `}` de la classe)

```java
    @Test
    void list_users_builds_url_with_paging_and_search() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/users"), eq(HttpMethod.GET), any(), eq(KeycloakUser[].class)))
                .thenReturn(ResponseEntity.ok(new KeycloakUser[0]));

        client.listUsers("bob", 20, 20);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(), eq(KeycloakUser[].class));
        assertThat(urlCaptor.getValue())
                .contains("first=20")
                .contains("max=20")
                .contains("search=bob");
    }

    @Test
    void count_users_returns_total() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/users/count"), eq(HttpMethod.GET), any(), eq(Integer.class)))
                .thenReturn(ResponseEntity.ok(42));

        assertThat(client.countUsers("")).isEqualTo(42);
    }
```
(Imports déjà présents : `ArgumentCaptor`, `contains`, `any`, `eq`, `when`, `verify`, `assertThat`, `Map`, `HttpMethod`, `ResponseEntity`.) La classe passe ainsi de **11 → 13** tests.

- [ ] **Step 5 : Suite générateur verte (parité INCHANGÉE 173)**

Run (depuis `/home/mr486/Developpement/Projets/GestoMS`) :
`mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected : vert, **173** (aucun fichier ajouté → ne pas modifier `TemplateLoaderTest`). `KeycloakAdminClientTest` ne tourne PAS ici (ressource template) — vérifié en Task 3. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 6 : Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java \
        src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java
git commit -m "feat(template): admin-application KeycloakAdminClient listUsers paging+search + countUsers"
```

---

## Task 2 : `UsersController` pagination + `users.html` (form recherche + pager)

**Files:**
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java`
- Modify: `src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html`

- [ ] **Step 1 : Re-signer `users(...)` dans `UsersController.java`**

Remplacer **intégralement** la méthode actuelle (de `@GetMapping("/users")` jusqu'à `return "users";` et son `}`) par :
```java
    @GetMapping("/users")
    public String users(@RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") int page,
                        Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        int size = 20;
        int first = page * size;
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("page", page);
        try {
            model.addAttribute("users", keycloakAdminClient.listUsers(search, first, size));
            int total = keycloakAdminClient.countUsers(search);
            int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("hasNext", (page + 1) < totalPages);
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "users";
    }
```
(Imports déjà présents : `GetMapping`, `RequestParam`, `Authentication`, `Model`. Aucun nouvel import — `Math` est `java.lang`.)

- [ ] **Step 2 : `users.html` — form GET de recherche au-dessus de la table**

Repérer la dernière ligne d'erreur puis la table (lignes ~16-17) :
```html
  <p th:if="${param.error != null and param.error[0] != 'conflict'}" class="error">Keycloak indisponible.</p>
  <table th:if="${users}">
```
Insérer le form **entre** ces deux lignes (juste avant `<table th:if="${users}">`) :
```html
  <form th:action="@{/users}" method="get">
    <input type="text" name="search" th:value="${search}" placeholder="Rechercher…"/>
    <button type="submit">Rechercher</button>
  </form>
```

- [ ] **Step 3 : `users.html` — pager sous la table**

Repérer la fermeture de la table (ligne ~35) :
```html
    </tbody>
  </table>
```
Insérer le pager **juste après** `</table>` (avant le `<h2>Créer un utilisateur</h2>`) :
```html

  <p th:if="${users}" class="pager">
    <a th:if="${hasPrev}" th:href="@{/users(search=${search}, page=${page - 1})}">← Précédent</a>
    <span>Page <span th:text="${page + 1}">1</span> / <span th:text="${totalPages}">1</span></span>
    <a th:if="${hasNext}" th:href="@{/users(search=${search}, page=${page + 1})}">Suivant →</a>
  </p>
```

- [ ] **Step 4 : Suite générateur verte (parité INCHANGÉE 173)**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` toujours à **173** — aucun fichier ajouté).

- [ ] **Step 5 : Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html
git commit -m "feat(template): admin-application /users search box + paged navigation"
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuilder le jar AVANT de générer** (piège du jar périmé). `pkill` et lancement en **commandes séparées** ; sandbox désactivé.

- [ ] **Step 1 : Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` à 173).

- [ ] **Step 2 : Tuer un éventuel générateur (commande séparée)**

Run (sandbox désactivé) : `pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null; sleep 2; pgrep -af '[g]enerator-v5' || echo clean`
Expected: `clean`.

- [ ] **Step 3 : Lancer le générateur (commande séparée, arrière-plan, sandbox désactivé, SANS pkill)**

```bash
java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 > /tmp/genapp.log 2>&1
```
Puis (commande séparée) :
```bash
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
grep -q "Tomcat started on port 8077" /tmp/genapp.log && echo STARTED
```
Expected: `STARTED`.

- [ ] **Step 4 : Générer + vérifier le contenu + compiler/tester le module généré**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refe.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refex && mkdir -p /tmp/refex && unzip -q /tmp/refe.zip -d /tmp/refex && echo UNZIPPED
echo "=== contenu P5 ==="
KC=/tmp/refex/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java
echo -n "listUsers(search)="; grep -c 'public List<KeycloakUser> listUsers(String search, int first, int max)' "$KC"
echo -n "countUsers="; grep -c 'public int countUsers(String search)' "$KC"
echo -n "UriComponentsBuilder import="; grep -c 'import org.springframework.web.util.UriComponentsBuilder;' "$KC"
HT=/tmp/refex/ms-platform/admin-application/src/main/resources/templates/users.html
echo -n "form recherche GET="; grep -c 'method="get"' "$HT"
echo -n "pager Précédent="; grep -c '← Précédent' "$HT"
cd /tmp/refex/ms-platform && mvn -pl admin-application -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|list_users_builds_url|count_users_returns|ERROR.*\.java' | head -20
```
Expected : `HTTP=200`, `UNZIPPED` ; `1` pour `listUsers(search)`, `countUsers`, `UriComponentsBuilder import`, `form recherche GET`, `pager Précédent` ; `KeycloakAdminClientTest` vert (contient `list_users_builds_url_with_paging_and_search` + `count_users_returns_total`, aucun échec) ; `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 5 : Compose valide + arrêt + arbre propre**

```bash
cd /tmp/refex/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; arbre git propre (tout commité aux Tasks 1–2).

---

## Recovery
- `git log --oneline -4` — commits passés (client paging+search ; UI search+pager).
- `grep -c 'public int countUsers' src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java` → `1` si Task 1 faite.
- `grep -c 'method="get"' src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html` → `1` si Task 2 faite.
- `mvn test` SUCCESS → générateur vert (parité 173) ; oracle module = `mvn -pl admin-application -am package` du projet généré (Task 3).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Tri par colonne, taille de page configurable (fixe à 20), recherche multi-champs séparés.
- Pagination de la liste des rôles / de l'historique chat (autres modules).
- Aucune modification du générateur ; ne PAS toucher `TemplateLoaderTest` (parité 173 inchangée).
