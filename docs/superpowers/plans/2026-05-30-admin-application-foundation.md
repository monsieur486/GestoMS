# Phase 3a — `admin-application` fondation + auth ADMIN + liste users Keycloak — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Créer le module template **toujours installé** `admin-application` : BFF réservé `ROLE_ADMIN` (login via ms-auth, session Redis) qui liste les utilisateurs via la Keycloak Admin REST API (admin master `admin-cli`, RestTemplate brut).

**Architecture:** La fondation BFF est copiée de ms-client (2a) puis réécrite dans le package `com.mr486.msplatform.adminapp`, avec `anyRequest().hasRole("ADMIN")`. Un `KeycloakAdminClient` obtient un token master (`admin-cli` grant password) et appelle `/admin/realms/ms-realm/users`. Le générateur ajoute le module **inconditionnellement** (toujours installé).

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Spring Session Redis, Thymeleaf, RestTemplate ; tests embarqués JUnit5 + Mockito ; générateur JUnit5 + AssertJ.

---

## Spec
`docs/superpowers/specs/2026-05-30-admin-application-foundation-design.md`

## Carte des fichiers

Module : `src/main/resources/templates/ms-platform/admin-application/`, package `com.mr486.msplatform.adminapp`.

**Fondation BFF (11 copiés de ms-client + 6 écrits) = 17 fichiers**, **Keycloak (5 nouveaux)** = **22 nouveaux**.
- **Copiés** (depuis `ms-client`, package réécrit) : `Dockerfile`, `configuration/RestTemplateConfig.java`, `web/LoginController.java`, `service/MsAuthClient.java`, `dto/MsAuthTokens.java`, `security/JwtRoles.java`, `security/MsAuthLogoutHandler.java`, `security/SessionKeys.java`, `templates/layout.html`, `templates/login.html`, `static/css/app.css`.
- **Écrits** (diffèrent) : `pom.xml`, `application.yml`, `AdminAppApplication.java`, `configuration/SecurityConfig.java` (ADMIN-gated), `web/HomeController.java`, `templates/home.html`.
- **Keycloak** : `service/KeycloakAdminClient.java`, `dto/KeycloakUser.java`, `web/UsersController.java`, `templates/users.html`, `src/test/java/.../service/KeycloakAdminClientTest.java`.

**Générateur :** `CrossCuttingConfigProcessor` (`desiredModules` + `rewriteTestAll`), `docker-compose.yml`, `dist.env`/`dot-env`, `CrossCuttingConfigProcessorTest`, `TemplateLoaderTest` (parité 147 → 164 → 169).

## Conventions
- Code/templates NON compilés par le générateur → **oracle = `mvn package` du projet généré** (Task 4).
- **Commits verts** : parité `TemplateLoaderTest` mise à jour dans chaque commit ajoutant des fichiers.
- Fondation **copiée** de ms-client (source canonique éprouvée) puis `sed` package `com.mr486.msplatform.client` → `com.mr486.msplatform.adminapp`.
- `admin-application` **toujours installé** : `desiredModules` l'ajoute sans condition ; jamais dans `blocksToRemove` ; aucune règle `FeatureFilterProcessor`.

---

## Task 1 : Fondation BFF du module (copie + écriture) — parité 164

**Files:** (création du module ; voir steps)
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: Copier les 11 fichiers de fondation depuis ms-client + réécrire le package**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform
SRC=ms-client/src/main/java/com/mr486/msplatform/client
DST=admin-application/src/main/java/com/mr486/msplatform/adminapp
mkdir -p $DST/configuration $DST/web $DST/service $DST/dto $DST/security \
         admin-application/src/main/resources/templates \
         admin-application/src/main/resources/static/css \
         admin-application/src/test/java/com/mr486/msplatform/adminapp/service
cp ms-client/Dockerfile admin-application/Dockerfile
cp $SRC/configuration/RestTemplateConfig.java $DST/configuration/RestTemplateConfig.java
cp $SRC/web/LoginController.java              $DST/web/LoginController.java
cp $SRC/service/MsAuthClient.java             $DST/service/MsAuthClient.java
cp $SRC/dto/MsAuthTokens.java                 $DST/dto/MsAuthTokens.java
cp $SRC/security/JwtRoles.java                $DST/security/JwtRoles.java
cp $SRC/security/MsAuthLogoutHandler.java     $DST/security/MsAuthLogoutHandler.java
cp $SRC/security/SessionKeys.java             $DST/security/SessionKeys.java
cp ms-client/src/main/resources/templates/layout.html admin-application/src/main/resources/templates/layout.html
cp ms-client/src/main/resources/templates/login.html  admin-application/src/main/resources/templates/login.html
cp ms-client/src/main/resources/static/css/app.css    admin-application/src/main/resources/static/css/app.css
grep -rl 'com.mr486.msplatform.client' $DST | xargs sed -i 's/com\.mr486\.msplatform\.client/com.mr486.msplatform.adminapp/g'
```

- [ ] **Step 2: Vérifier la copie + réécriture**

Run: `cd /home/mr486/Developpement/Projets/GestoMS && grep -rl 'com.mr486.msplatform.client' src/main/resources/templates/ms-platform/admin-application/ ; echo "rc=$?"`
Expected: aucune ligne (rc=1) — plus aucune référence à l'ancien package.
Run: `grep -rc 'com.mr486.msplatform.adminapp' src/main/resources/templates/ms-platform/admin-application/src/main/java | grep -c ':0' ; echo done`
Expected: `0` fichiers Java sans le nouveau package (tous réécrits).

- [ ] **Step 3: Écrire `pom.xml`**

Fichier `src/main/resources/templates/ms-platform/admin-application/pom.xml` :
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.mr486</groupId><artifactId>ms-platform</artifactId><version>0.0.1-SNAPSHOT</version></parent>
  <artifactId>admin-application</artifactId>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.session</groupId><artifactId>spring-session-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: Écrire `src/main/resources/application.yml`**

```yaml
server:
  port: ${ADMIN_APP_PORT:9300}
spring:
  application:
    name: admin-application
  session:
    store-type: redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  thymeleaf:
    cache: false
gateway:
  url: ${GATEWAY_URL:http://localhost:9000}
keycloak:
  internal-url: ${KEYCLOAK_INTERNAL_URL:http://keycloak:8080}
  realm: ms-realm
  admin-username: ${KEYCLOAK_ADMIN:admin}
  admin-password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 5: Écrire `…/adminapp/AdminAppApplication.java`**

```java
package com.mr486.msplatform.adminapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdminAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminAppApplication.class, args);
    }
}
```

- [ ] **Step 6: Écrire `…/adminapp/configuration/SecurityConfig.java`** (toute l'app réservée ADMIN)

```java
package com.mr486.msplatform.adminapp.configuration;

import com.mr486.msplatform.adminapp.security.MsAuthLogoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           MsAuthLogoutHandler logoutHandler,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(logoutHandler)
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION"));
        return http.build();
    }
}
```

- [ ] **Step 7: Écrire `…/adminapp/web/HomeController.java`**

```java
package com.mr486.msplatform.adminapp.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        model.addAttribute("roles", roles);
        return "home";
    }
}
```

- [ ] **Step 8: Écrire `src/main/resources/templates/home.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Administration — admin-application</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <h1>Administration</h1>
  <p>Connecté : <span th:text="${username}">user</span></p>
  <h2>Vos rôles</h2>
  <ul>
    <li th:each="role : ${roles}" th:text="${role}">ROLE</li>
  </ul>
  <h2>Gestion</h2>
  <ul class="links">
    <li><a th:href="@{/users}">Utilisateurs</a></li>
    <li>Rôles <em>(à venir — 3c)</em></li>
  </ul>
  <form th:action="@{/logout}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button type="submit">Se déconnecter</button>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: Vérifier le compte de fichiers du module (17)**

Run: `find src/main/resources/templates/ms-platform/admin-application -type f | wc -l`
Expected: `17`.

- [ ] **Step 10: Parité `TemplateLoaderTest` (→ 164)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 147 ... but was: 164` (17 nouveaux fichiers). Si autre nombre, vérifier la copie : `mvn -q process-resources && find target/classes/templates/ms-platform/admin-application -type f | wc -l` doit donner `17`.
Remplacer dans `TemplateLoaderTest.java` `hasSize(147)` → `hasSize(164)` (nombre observé).

- [ ] **Step 11: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 12: Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/admin-application \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): admin-application BFF foundation (ADMIN-gated, copied from ms-client)"
```

---

## Task 2 : Client Keycloak Admin + liste users — parité 169

**Files:**
- Create: `…/admin-application/src/main/java/com/mr486/msplatform/adminapp/dto/KeycloakUser.java`
- Create: `…/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java`
- Create: `…/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java`
- Create: `…/admin-application/src/main/resources/templates/users.html`
- Create: `…/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`

- [ ] **Step 1: `dto/KeycloakUser.java`**

```java
package com.mr486.msplatform.adminapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(String id, String username, String email, String firstName, String lastName, boolean enabled) {}
```

- [ ] **Step 2: `service/KeycloakAdminClient.java`**

```java
package com.mr486.msplatform.adminapp.service;

import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/** Appelle la Keycloak Admin REST API avec un token admin master (admin-cli, grant password). */
@Service
public class KeycloakAdminClient {

    private final RestTemplate restTemplate;
    private final String internalUrl;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakAdminClient(RestTemplate restTemplate,
                               @Value("${keycloak.internal-url}") String internalUrl,
                               @Value("${keycloak.realm}") String realm,
                               @Value("${keycloak.admin-username}") String adminUsername,
                               @Value("${keycloak.admin-password}") String adminPassword) {
        this.restTemplate = restTemplate;
        this.internalUrl = internalUrl;
        this.realm = realm;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public List<KeycloakUser> listUsers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakUser[]> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users?max=100",
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakUser[].class);
            KeycloakUser[] body = resp.getBody();
            return body == null ? List.of() : List.of(body);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    @SuppressWarnings("rawtypes")
    private String adminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    internalUrl + "/realms/master/protocol/openid-connect/token",
                    new HttpEntity<>(form, headers), Map.class);
            Map body = resp.getBody();
            Object token = body == null ? null : body.get("access_token");
            if (token == null) {
                throw new KeycloakUnavailableException();
            }
            return token.toString();
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public static class KeycloakUnavailableException extends RuntimeException {}
}
```

- [ ] **Step 3: `web/UsersController.java`**

```java
package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UsersController {

    private final KeycloakAdminClient keycloakAdminClient;

    public UsersController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @GetMapping("/users")
    public String users(Model model) {
        try {
            model.addAttribute("users", keycloakAdminClient.listUsers());
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "users";
    }
}
```

- [ ] **Step 4: `templates/users.html`**

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
  <table th:if="${users}">
    <thead><tr><th>username</th><th>email</th><th>prénom</th><th>nom</th><th>actif</th></tr></thead>
    <tbody>
      <tr th:each="u : ${users}">
        <td th:text="${u.username}">user</td>
        <td th:text="${u.email}">email</td>
        <td th:text="${u.firstName}">first</td>
        <td th:text="${u.lastName}">last</td>
        <td th:text="${u.enabled}">true</td>
      </tr>
    </tbody>
  </table>
</main>
</body>
</html>
```

- [ ] **Step 5: `src/test/java/.../service/KeycloakAdminClientTest.java`**

```java
package com.mr486.msplatform.adminapp.service;

import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class KeycloakAdminClientTest {

    private RestTemplate restTemplate;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        client = new KeycloakAdminClient(restTemplate, "http://kc", "ms-realm", "admin", "admin");
    }

    @Test
    void lists_users_after_obtaining_admin_token() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        KeycloakUser[] users = {
                new KeycloakUser("1", "alice", "alice@x.io", "Al", "Ice", true),
                new KeycloakUser("2", "bob", "bob@x.io", "Bo", "B", false)
        };
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/users"), eq(HttpMethod.GET), any(), eq(KeycloakUser[].class)))
                .thenReturn(ResponseEntity.ok(users));

        assertThat(client.listUsers()).extracting(KeycloakUser::username).containsExactly("alice", "bob");
    }

    @Test
    void throws_when_token_missing() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of()));
        assertThatThrownBy(() -> client.listUsers())
                .isInstanceOf(KeycloakAdminClient.KeycloakUnavailableException.class);
    }
}
```

- [ ] **Step 6: Parité `TemplateLoaderTest` (→ 169)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run'`
Expected: échec `expected: 164 ... but was: 169` (5 nouveaux).
Remplacer `hasSize(164)` → `hasSize(169)`.

- [ ] **Step 7: Suite générateur verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/dto/KeycloakUser.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClient.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java \
        src/main/resources/templates/ms-platform/admin-application/src/main/resources/templates/users.html \
        src/main/resources/templates/ms-platform/admin-application/src/test/java/com/mr486/msplatform/adminapp/service/KeycloakAdminClientTest.java \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): admin-application KeycloakAdminClient + users list page"
```

---

## Task 3 : Générateur — module toujours installé (TDD)

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
- Modify: `src/main/resources/templates/ms-platform/docker-compose.yml`
- Modify: `src/main/resources/templates/ms-platform/dist.env`
- Modify: `src/main/resources/templates/ms-platform/dot-env`

- [ ] **Step 1: Ajouter le bloc `admin-application:` à `SAMPLE_COMPOSE` du test**

Dans `CrossCuttingConfigProcessorTest.java`, repérer dans `SAMPLE_COMPOSE` les lignes :
```java
        "  ms-auth:\n" +
        "    build: ./ms-auth\n" +
        "\n" +
```
et insérer juste après :
```java
        "  admin-application:\n" +
        "    build: ./admin-application\n" +
        "\n" +
```

- [ ] **Step 2: Ajouter les tests d'échec**

Après `root_pom_includes_all_default_modules_when_all_features_enabled`, ajouter :
```java
    @Test
    void root_pom_always_includes_admin_application() {
        // sans features ni resources
        List<GeneratedFile> a = processor.process(sampleFiles(), defaultCtx());
        assertThat(contentOf(a.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow()))
                .contains("<module>admin-application</module>");
        // avec resources
        List<GeneratedFile> b = processor.process(sampleFiles(),
                ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES)));
        assertThat(contentOf(b.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow()))
                .contains("<module>admin-application</module>");
    }

    @Test
    void compose_always_keeps_admin_application_block() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String compose = contentOf(result.stream().filter(g -> g.path().endsWith("docker-compose.yml")).findFirst().orElseThrow());
        assertThat(compose).contains("  admin-application:");
    }
```

- [ ] **Step 3: Lancer → échec attendu**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|admin_application'`
Expected: échec sur `root_pom_always_includes_admin_application` (le module n'est pas encore ajouté).

- [ ] **Step 4: `desiredModules` — ajout inconditionnel après `ms-auth`**

Dans `CrossCuttingConfigProcessor.java`, remplacer :
```java
        modules.add("ms-auth");                 // keycloak permanent
```
par :
```java
        modules.add("ms-auth");                 // keycloak permanent
        modules.add("admin-application");        // toujours installé
```

- [ ] **Step 5: `rewriteTestAll` — smoke admin-application (inconditionnel)**

Remplacer (le `wait_for` ms-admin et son voisinage) :
```java
        if (feat.isSpringbootAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        if (feat.isClientWebUI()) sb.append("wait_for 'ms-client' curl -fs http://localhost:8090/login\n");
        sb.append("echo 'Stack is ready.'\n");
```
par :
```java
        if (feat.isSpringbootAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        if (feat.isClientWebUI()) sb.append("wait_for 'ms-client' curl -fs http://localhost:8090/login\n");
        sb.append("wait_for 'admin-application' curl -fs http://localhost:9300/login\n"); // toujours installé
        sb.append("echo 'Stack is ready.'\n");
```
Puis remplacer (la section infra "Admin OK"/"Client OK") :
```java
        if (feat.isSpringbootAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
        if (feat.isClientWebUI()) sb.append("curl -fs http://localhost:8090/login >/dev/null && echo 'Client OK'\n");
        sb.append("\n");
```
par :
```java
        if (feat.isSpringbootAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
        if (feat.isClientWebUI()) sb.append("curl -fs http://localhost:8090/login >/dev/null && echo 'Client OK'\n");
        sb.append("curl -fs http://localhost:9300/login >/dev/null && echo 'Admin-app OK'\n"); // toujours installé
        sb.append("\n");
```

- [ ] **Step 6: Lancer → vert**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 7: `docker-compose.yml` (template) — bloc `admin-application` permanent**

Repérer le bloc `ms-auth:` :
```yaml
  ms-auth:
    build: ./ms-auth
    env_file: [.env]
    depends_on: [ms-eureka, keycloak, redis]
    environment:
      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/
      KEYCLOAK_INTERNAL_URL: http://keycloak:8080
      REDIS_HOST: redis
    ports: ["9200:9200"]
```
et insérer immédiatement après (ligne vide de séparation incluse) :
```yaml

  admin-application:
    build: ./admin-application
    env_file: [.env]
    depends_on: [ms-eureka, ms-gateway, keycloak, redis, ms-auth]
    environment:
      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/
      REDIS_HOST: redis
      GATEWAY_URL: http://ms-gateway:9000
      KEYCLOAK_INTERNAL_URL: http://keycloak:8080
      ADMIN_APP_PORT: 9300
    ports: ["9300:9300"]
```

- [ ] **Step 8: `dist.env` et `dot-env` — `ADMIN_APP_PORT`**

Ajouter à la fin de `dist.env` ET de `dot-env` :
```
ADMIN_APP_PORT=9300
```

- [ ] **Step 9: Suite complète verte**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java \
        src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java \
        src/main/resources/templates/ms-platform/docker-compose.yml \
        src/main/resources/templates/ms-platform/dist.env \
        src/main/resources/templates/ms-platform/dot-env
git commit -m "feat(generator): always install admin-application (modules + compose + test-all)"
```

---

## Task 4 : Build + génération end-to-end

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

- [ ] **Step 4: Générer `clientWebUI=false` — admin-application doit être présent (TOUJOURS installé)**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":false,"clientWebUI":false}}' \
  -o /tmp/refa.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refax && mkdir -p /tmp/refax && unzip -q /tmp/refa.zip -d /tmp/refax && echo UNZIPPED
echo "=== admin-application présent malgré clientWebUI=false ==="
grep -c '<module>admin-application</module>' /tmp/refax/ms-platform/pom.xml
grep -c '^  admin-application:' /tmp/refax/ms-platform/docker-compose.yml
ls /tmp/refax/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/service/KeycloakAdminClient.java 2>&1
echo "=== package réécrit + ADMIN gate + Admin-app OK ==="
grep -c 'package com.acme.shop.adminapp' /tmp/refax/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/AdminAppApplication.java
grep -c 'hasRole("ADMIN")' /tmp/refax/ms-platform/admin-application/src/main/java/com/acme/shop/adminapp/configuration/SecurityConfig.java
grep -c 'Admin-app OK' /tmp/refax/ms-platform/test-all.sh
```
Expected : `HTTP=200`, `UNZIPPED`, puis `1`, `1`, le fichier listé, `1`, `1`, `1`.

- [ ] **Step 5: Compiler le module généré + exécuter le test embarqué**

```bash
cd /tmp/refax/ms-platform && mvn -pl admin-application -am package 2>&1 | grep -E 'Tests run|BUILD (SUCCESS|FAILURE)|KeycloakAdminClientTest|ERROR.*\.java' | head -20
```
Expected: `KeycloakAdminClientTest` (2) verts, puis `BUILD SUCCESS`.
Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 6: Compose valide + arrêt**

```bash
cd /tmp/refax/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : `COMPOSE_OK` ; arbre git propre (tout commité aux Tasks 1–3).

---

## Recovery
- `git log --oneline -5` — commits passés (fondation, Keycloak client, générateur).
- `find src/main/resources/templates/ms-platform/admin-application -type f | wc -l` → `22` si Tasks 1+2 faites.
- `grep hasSize src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` → `169` si Tasks 1+2 faites.
- `grep -c 'admin-application' src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` → `3` (desiredModules ×1, rewriteTestAll ×2) si Task 3 faite.
- `mvn test` SUCCESS → générateur vert ; oracle module = `mvn -pl admin-application -am package` du projet généré (Task 4, exécute `KeycloakAdminClientTest`).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Création/suppression d'utilisateurs (3b), gestion des rôles (3c).
- Pagination/recherche users, édition profil, reset password, cache du token admin.