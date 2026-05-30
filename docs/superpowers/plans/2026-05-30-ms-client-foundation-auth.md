# Phase 2a — `ms-client` Fondation + Auth BFF — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Créer le module template `ms-client` (UI Thymeleaf, BFF d'authentification via ms-auth, session Redis) et le câbler dans le générateur, piloté par `features.clientWebUI`.

**Architecture:** `ms-client` est une app Spring Boot MVC exposée sur le port dédié `:8090` (comme `ms-admin:9100`), cliente Eureka. C'est un BFF : le navigateur ne voit qu'un cookie de session (Spring Session + Redis) ; les tokens (obtenus auprès de `ms-auth` via le gateway) vivent côté serveur. Spring Security protège les pages ; une étape d'auth custom (sans filtre `formLogin`) traite le POST `/login`. Le générateur câble le module en miroir exact de `ms-admin` (module pom + bloc compose + smoke test-all gated), **sans route gateway** (port direct).

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6, Spring Session Data Redis, Thymeleaf, Maven ; côté générateur : Spring, JUnit 5 + AssertJ.

---

## Spec
`docs/superpowers/specs/2026-05-30-ms-client-foundation-auth-design.md`

## Carte des fichiers

**Nouveau module template** (`src/main/resources/templates/ms-platform/ms-client/`, package `com.mr486.msplatform.client` réécrit vers le `basePackage` par `PackagePlaceholderProcessor`) — **17 fichiers** :

| Fichier | Responsabilité |
|---------|----------------|
| `pom.xml` | Module Maven (parent `ms-platform`) ; deps web/thymeleaf/security/data-redis/session-redis/eureka/actuator |
| `Dockerfile` | Image JRE 17 (identique aux autres modules) |
| `src/main/resources/application.yml` | Port 8090, eureka, session redis, `gateway.url` |
| `…/client/ClientApplication.java` | Point d'entrée Spring Boot |
| `…/client/configuration/SecurityConfig.java` | Filtre Spring Security : routes, entry point `/login`, logout, CSRF |
| `…/client/configuration/RestTemplateConfig.java` | Bean `RestTemplate` (timeouts) |
| `…/client/web/LoginController.java` | `GET/POST /login` — auth via ms-auth, peuple le SecurityContext |
| `…/client/web/HomeController.java` | `GET /` — page d'accueil (username + rôles) |
| `…/client/service/MsAuthClient.java` | Appels REST à ms-auth (login/logout) via `gateway.url` |
| `…/client/dto/MsAuthTokens.java` | Désérialise la réponse login (`access_token`, …) |
| `…/client/security/JwtRoles.java` | Extrait `realm_access.roles` du JWT (base64url, sans lib) |
| `…/client/security/MsAuthLogoutHandler.java` | LogoutHandler appelant ms-auth `/auth/logout` |
| `…/client/security/SessionKeys.java` | Constantes des clés d'attributs de session |
| `src/main/resources/templates/layout.html` | Fragment shell (header) |
| `src/main/resources/templates/login.html` | Page de connexion |
| `src/main/resources/templates/home.html` | Page d'accueil |
| `src/main/resources/static/css/app.css` | CSS minimal fait main |

**Template modifié :** `docker-compose.yml` (+ bloc `ms-client:`), `dist.env` + `dot-env` (`MS_CLIENT_PORT`).

**Générateur (main) modifié :** `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` (`desiredModules`, `blocksToRemove`, `rewriteTestAll`).

**Tests modifiés :** `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`, `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` (compteur de parité 112 → 129).

## Conventions
- Les fichiers Java du **template** ne sont PAS compilés par le générateur (ce sont des ressources). On ne peut donc pas les TDD dans le projet générateur : leur **oracle est le `mvn package` du projet généré** (Task 9). On écrit donc leur contenu complet, puis on vérifie par génération.
- Les changements du **générateur** (CrossCutting) suivent le TDD via `CrossCuttingConfigProcessorTest`.
- **Commits verts uniquement.** Les Tasks 1–3 ajoutent des fichiers template et rendent `TemplateLoaderTest` rouge (compteur) : **ne pas committer** avant la Task 4 qui met à jour le compteur et rend la suite verte.
- `FeatureFilterProcessor` possède déjà la règle `ms-client/` (Phase 1) → **aucune modif**. `PackagePlaceholderProcessor` réécrit le package automatiquement → **aucune modif**.

---

## Task 1 : Squelette Maven du module `ms-client`

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-client/pom.xml`
- Create: `src/main/resources/templates/ms-platform/ms-client/Dockerfile`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/resources/application.yml`
- Create: `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/ClientApplication.java`

> **Ne pas committer à la fin de cette task** (TemplateLoaderTest rouge jusqu'à la Task 4).

- [ ] **Step 1: `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.mr486</groupId><artifactId>ms-platform</artifactId><version>0.0.1-SNAPSHOT</version></parent>
  <artifactId>ms-client</artifactId>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.session</groupId><artifactId>spring-session-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: `Dockerfile`** (identique aux autres modules)

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

- [ ] **Step 3: `application.yml`**

```yaml
server:
  port: ${MS_CLIENT_PORT:8090}
spring:
  application:
    name: ms-client
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

- [ ] **Step 4: `ClientApplication.java`**

```java
package com.mr486.msplatform.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }
}
```

- [ ] **Step 5: Vérifier la présence des 4 fichiers**

Run: `find src/main/resources/templates/ms-platform/ms-client -type f | sort`
Expected: les 4 chemins ci-dessus.

---

## Task 2 : Classes backend du module `ms-client`

**Files:**
- Create: `…/client/security/SessionKeys.java`
- Create: `…/client/dto/MsAuthTokens.java`
- Create: `…/client/security/JwtRoles.java`
- Create: `…/client/configuration/RestTemplateConfig.java`
- Create: `…/client/service/MsAuthClient.java`
- Create: `…/client/security/MsAuthLogoutHandler.java`
- Create: `…/client/configuration/SecurityConfig.java`
- Create: `…/client/web/LoginController.java`
- Create: `…/client/web/HomeController.java`

(racine : `src/main/resources/templates/ms-platform/ms-client/src/main/java/com/mr486/msplatform/client/`)

> **Ne pas committer à la fin de cette task.**

- [ ] **Step 1: `security/SessionKeys.java`**

```java
package com.mr486.msplatform.client.security;

public final class SessionKeys {
    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private SessionKeys() {}
}
```

- [ ] **Step 2: `dto/MsAuthTokens.java`**

```java
package com.mr486.msplatform.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MsAuthTokens(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("opaque_refresh_token") String opaqueRefreshToken,
        @JsonProperty("expires_in") long expiresIn) {
}
```

- [ ] **Step 3: `security/JwtRoles.java`**

```java
package com.mr486.msplatform.client.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Extrait les rôles realm d'un access token JWT sans librairie JWT (base64url du payload). */
public final class JwtRoles {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtRoles() {}

    public static List<String> realmRoles(String accessToken) {
        List<String> roles = new ArrayList<>();
        if (accessToken == null) return roles;
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return roles;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode arr = MAPPER.readTree(payload).path("realm_access").path("roles");
            if (arr.isArray()) arr.forEach(n -> roles.add(n.asText()));
        } catch (Exception ignored) {
            // token illisible -> aucun rôle
        }
        return roles;
    }
}
```

- [ ] **Step 4: `configuration/RestTemplateConfig.java`**

```java
package com.mr486.msplatform.client.configuration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
```

- [ ] **Step 5: `service/MsAuthClient.java`**

```java
package com.mr486.msplatform.client.service;

import com.mr486.msplatform.client.dto.MsAuthTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Client BFF vers ms-auth (exposé via le gateway). */
@Service
public class MsAuthClient {

    private final RestTemplate restTemplate;
    private final String gatewayUrl;

    public MsAuthClient(RestTemplate restTemplate, @Value("${gateway.url}") String gatewayUrl) {
        this.restTemplate = restTemplate;
        this.gatewayUrl = gatewayUrl;
    }

    public MsAuthTokens login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        try {
            ResponseEntity<MsAuthTokens> resp =
                    restTemplate.postForEntity(gatewayUrl + "/auth/login", entity, MsAuthTokens.class);
            return resp.getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new InvalidCredentialsException();
            }
            throw new AuthUnavailableException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AuthUnavailableException();
        }
    }

    public void logout(String accessToken, String opaqueRefreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("opaque_refresh_token", opaqueRefreshToken == null ? "" : opaqueRefreshToken), headers);
            restTemplate.postForEntity(gatewayUrl + "/auth/logout", entity, Void.class);
        } catch (Exception ignored) {
            // le logout local doit toujours réussir, même si ms-auth est injoignable
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {}

    public static class AuthUnavailableException extends RuntimeException {}
}
```

- [ ] **Step 6: `security/MsAuthLogoutHandler.java`**

```java
package com.mr486.msplatform.client.security;

import com.mr486.msplatform.client.service.MsAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Appelle ms-auth /auth/logout AVANT l'invalidation de session. Spring Security 6 exécute les
 * handlers ajoutés via addLogoutHandler avant le SecurityContextLogoutHandler interne (qui
 * invalide la session), donc la session est encore lisible ici.
 */
@Component
public class MsAuthLogoutHandler implements LogoutHandler {

    private final MsAuthClient msAuthClient;

    public MsAuthLogoutHandler(MsAuthClient msAuthClient) {
        this.msAuthClient = msAuthClient;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        String refreshToken = (String) session.getAttribute(SessionKeys.REFRESH_TOKEN);
        if (accessToken != null) {
            msAuthClient.logout(accessToken, refreshToken);
        }
    }
}
```

- [ ] **Step 7: `configuration/SecurityConfig.java`**

```java
package com.mr486.msplatform.client.configuration;

import com.mr486.msplatform.client.security.MsAuthLogoutHandler;
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
    public SecurityFilterChain filterChain(HttpSecurity http, MsAuthLogoutHandler logoutHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
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

- [ ] **Step 8: `web/LoginController.java`**

```java
package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.dto.MsAuthTokens;
import com.mr486.msplatform.client.security.JwtRoles;
import com.mr486.msplatform.client.security.SessionKeys;
import com.mr486.msplatform.client.service.MsAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class LoginController {

    private final MsAuthClient msAuthClient;
    private final SecurityContextRepository securityContextRepository;

    public LoginController(MsAuthClient msAuthClient, SecurityContextRepository securityContextRepository) {
        this.msAuthClient = msAuthClient;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          Model model) {
        MsAuthTokens tokens;
        try {
            tokens = msAuthClient.login(username, password);
        } catch (MsAuthClient.InvalidCredentialsException e) {
            model.addAttribute("error", "Identifiants invalides");
            return "login";
        } catch (MsAuthClient.AuthUnavailableException e) {
            model.addAttribute("error", "Service d'authentification indisponible");
            return "login";
        }

        List<SimpleGrantedAuthority> authorities = JwtRoles.realmRoles(tokens.accessToken()).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.ACCESS_TOKEN, tokens.accessToken());
        session.setAttribute(SessionKeys.REFRESH_TOKEN, tokens.opaqueRefreshToken());

        return "redirect:/";
    }
}
```

- [ ] **Step 9: `web/HomeController.java`**

```java
package com.mr486.msplatform.client.web;

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

- [ ] **Step 10: Vérifier**

Run: `find src/main/resources/templates/ms-platform/ms-client/src/main/java -name '*.java' | wc -l`
Expected: `8`

---

## Task 3 : UI du module `ms-client`

**Files:**
- Create: `…/ms-client/src/main/resources/templates/layout.html`
- Create: `…/ms-client/src/main/resources/templates/login.html`
- Create: `…/ms-client/src/main/resources/templates/home.html`
- Create: `…/ms-client/src/main/resources/static/css/app.css`

> **Ne pas committer à la fin de cette task** (le commit a lieu en Task 4 après mise à jour du compteur).

- [ ] **Step 1: `templates/layout.html`** (fragment header réutilisable)

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<body>
<header th:fragment="header" class="topbar">
  <span class="brand">ms-client</span>
</header>
</body>
</html>
```

- [ ] **Step 2: `templates/login.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Connexion — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<main class="login">
  <h1>Connexion</h1>
  <p th:if="${param.logout}" class="info">Vous êtes déconnecté.</p>
  <p th:if="${error}" class="error" th:text="${error}">erreur</p>
  <form th:action="@{/login}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <label>Utilisateur
      <input type="text" name="username" required autofocus/>
    </label>
    <label>Mot de passe
      <input type="password" name="password" required/>
    </label>
    <button type="submit">Se connecter</button>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 3: `templates/home.html`**

```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Accueil — ms-client</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<div th:replace="~{layout :: header}"></div>
<main>
  <h1>Bienvenue, <span th:text="${username}">user</span></h1>
  <h2>Vos rôles</h2>
  <ul>
    <li th:each="role : ${roles}" th:text="${role}">ROLE</li>
  </ul>
  <h2>Pages</h2>
  <ul class="links">
    <li>Page consumer <em>(à venir — 2b)</em></li>
    <li>CRUD <em>(à venir — 2c)</em></li>
    <li>Notifications batch <em>(à venir — 2d)</em></li>
    <li>Chat <em>(à venir — 2e)</em></li>
  </ul>
  <form th:action="@{/logout}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button type="submit">Se déconnecter</button>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 4: `static/css/app.css`**

```css
* { box-sizing: border-box; }
body { font-family: system-ui, sans-serif; margin: 0; color: #1a1a1a; background: #f7f7f8; }
.topbar { background: #2d2d34; color: #fff; padding: .8rem 1.2rem; }
.topbar .brand { font-weight: 600; letter-spacing: .02em; }
main { max-width: 640px; margin: 2rem auto; padding: 0 1.2rem; }
h1 { font-size: 1.5rem; }
h2 { font-size: 1.05rem; margin-top: 1.5rem; }
ul { padding-left: 1.2rem; }
.links em { color: #888; font-style: italic; }
form { display: flex; flex-direction: column; gap: .8rem; max-width: 320px; margin-top: 1rem; }
label { display: flex; flex-direction: column; gap: .25rem; font-size: .9rem; }
input { padding: .5rem; border: 1px solid #ccc; border-radius: 4px; }
button { padding: .55rem 1rem; background: #3b5bdb; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
button:hover { background: #2f49af; }
.login { max-width: 360px; }
.error { color: #c92a2a; }
.info { color: #2b8a3e; }
```

- [ ] **Step 5: Vérifier le total des fichiers du module**

Run: `find src/main/resources/templates/ms-platform/ms-client -type f | wc -l`
Expected: `17`

---

## Task 4 : Mettre à jour la parité `TemplateLoaderTest` + commit du module

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java:31`

- [ ] **Step 1: Lancer TemplateLoaderTest pour connaître le compteur réel**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'but was|Tests run|expected'`
Expected: échec du type `expected: 112 ... but was: 129`. **Le nombre observé fait foi.**

- [ ] **Step 2: Mettre à jour l'assertion**

Remplacer dans `TemplateLoaderTest.java` :
```java
        assertThat(loader.load()).hasSize(112);
```
par (avec le nombre observé au Step 1, normalement `129`) :
```java
        assertThat(loader.load()).hasSize(129);
```
Si le nombre observé n'est pas 129, utiliser ce nombre **et** vérifier qu'aucun des 17 fichiers `ms-client` n'a été silencieusement exclu : `mvn -q process-resources && find target/classes/templates/ms-platform/ms-client -type f | wc -l` doit donner `17`.

- [ ] **Step 3: Lancer la suite complète (doit être verte)**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 4: Commit (module template + parité)**

```bash
git add src/main/resources/templates/ms-platform/ms-client \
        src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java
git commit -m "feat(template): add ms-client module (Thymeleaf BFF foundation + auth)

New ms-client template module: Spring MVC UI on :8090, BFF auth via ms-auth,
Spring Session+Redis, Spring Security with custom login (no formLogin filter),
minimal hand-rolled CSS. Template parity 112 -> 129."
```

---

## Task 5 : Bloc compose `ms-client` + variables d'environnement

**Files:**
- Modify: `src/main/resources/templates/ms-platform/docker-compose.yml`
- Modify: `src/main/resources/templates/ms-platform/dist.env`
- Modify: `src/main/resources/templates/ms-platform/dot-env`

- [ ] **Step 1: Ajouter le bloc `ms-client:` juste après le bloc `ms-admin:`**

Repérer le bloc existant :
```yaml
  ms-admin:
    build: ./ms-admin
    env_file: [.env]
    depends_on: [ms-eureka]
    environment:
      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/
    ports: ["9100:9100"]
```
et insérer immédiatement après (ligne vide de séparation incluse) :
```yaml

  ms-client:
    build: ./ms-client
    env_file: [.env]
    depends_on: [ms-eureka, ms-gateway, redis, ms-auth]
    environment:
      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/
      REDIS_HOST: redis
      GATEWAY_URL: http://ms-gateway:9000
      MS_CLIENT_PORT: 8090
    ports: ["8090:8090"]
```

- [ ] **Step 2: Ajouter `MS_CLIENT_PORT` à `dist.env`** (à la fin du fichier)

```
MS_CLIENT_PORT=8090
```

- [ ] **Step 3: Ajouter `MS_CLIENT_PORT` à `dot-env`** (à la fin du fichier)

```
MS_CLIENT_PORT=8090
```

- [ ] **Step 4: Vérifier**

Run: `grep -c 'ms-client:' src/main/resources/templates/ms-platform/docker-compose.yml ; grep -c 'MS_CLIENT_PORT=8090' src/main/resources/templates/ms-platform/dist.env src/main/resources/templates/ms-platform/dot-env`
Expected: `1` puis `1` et `1`.

- [ ] **Step 5: Suite verte (fichiers modifiés, pas ajoutés → parité inchangée)**

Run: `mvn -q test -Dtest=TemplateLoaderTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/ms-platform/docker-compose.yml \
        src/main/resources/templates/ms-platform/dist.env \
        src/main/resources/templates/ms-platform/dot-env
git commit -m "feat(template): add ms-client docker-compose block + MS_CLIENT_PORT env"
```

---

## Task 6 : Générateur — `desiredModules` + `blocksToRemove` (TDD)

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: Ajouter un bloc `ms-client:` à `SAMPLE_COMPOSE` du test**

Dans `CrossCuttingConfigProcessorTest.java`, repérer dans `SAMPLE_COMPOSE` les lignes :
```java
        "  ms-admin:\n" +
        "    build: ./ms-admin\n" +
        "\n" +
```
et insérer juste après :
```java
        "  ms-client:\n" +
        "    build: ./ms-client\n" +
        "\n" +
```

- [ ] **Step 2: Ajouter les 4 tests d'échec** (après `root_pom_excludes_ms_admin_when_admin_disabled`)

```java
    @Test
    void root_pom_includes_ms_client_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setClientWebUI(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());
        assertThat(pom).contains("<module>ms-client</module>");
    }

    @Test
    void root_pom_excludes_ms_client_when_client_web_ui_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>ms-client</module>");
    }

    @Test
    void compose_keeps_ms_client_block_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setClientWebUI(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String compose = contentOf(result.stream().filter(g -> g.path().endsWith("docker-compose.yml")).findFirst().orElseThrow());
        assertThat(compose).contains("  ms-client:");
    }

    @Test
    void compose_removes_ms_client_block_when_client_web_ui_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String compose = contentOf(result.stream().filter(g -> g.path().endsWith("docker-compose.yml")).findFirst().orElseThrow());
        assertThat(compose).doesNotContain("  ms-client:");
    }
```

- [ ] **Step 3: Lancer → échec attendu**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|ms_client'`
Expected: échecs sur `root_pom_includes_ms_client...` et `compose_keeps_ms_client_block...` (le code ne câble pas encore ms-client).

- [ ] **Step 4: `desiredModules` — ajouter ms-client après ms-admin**

Dans `CrossCuttingConfigProcessor.java`, remplacer :
```java
        if (f.isSpringbootAdmin())    modules.add("ms-admin");
        if (hasResources) {
```
par :
```java
        if (f.isSpringbootAdmin())    modules.add("ms-admin");
        if (f.isClientWebUI())        modules.add("ms-client");
        if (hasResources) {
```

- [ ] **Step 5: `blocksToRemove` — retirer ms-client quand désactivé**

Remplacer :
```java
        if (!f.isSpringbootAdmin())  blocks.add("ms-admin");
        if (hasResources) {
```
par :
```java
        if (!f.isSpringbootAdmin())  blocks.add("ms-admin");
        if (!f.isClientWebUI())      blocks.add("ms-client");
        if (hasResources) {
```

- [ ] **Step 6: Lancer → vert**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java \
        src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java
git commit -m "feat(generator): wire ms-client into modules + compose (clientWebUI)"
```

---

## Task 7 : Générateur — smoke `ms-client` dans `test-all.sh` (TDD)

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: Ajouter 2 tests** (après `test_all_preserves_executable_flag`)

```java
    @Test
    void test_all_includes_ms_client_smoke_when_client_web_ui_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(res("order-service", "Order", DatabaseType.POSTGRES)));
        req.getFeatures().setClientWebUI(true);
        List<GeneratedFile> result = processor.process(
                List.of(file(TESTALL_PATH, "old", true)), GenerationContext.from(req));
        String s = testAllOf(result);
        assertThat(s).contains("wait_for 'ms-client'").contains("Client OK");
    }

    @Test
    void test_all_omits_ms_client_smoke_when_client_web_ui_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(res("order-service", "Order", DatabaseType.POSTGRES)));
        List<GeneratedFile> result = processor.process(
                List.of(file(TESTALL_PATH, "old", true)), GenerationContext.from(req));
        String s = testAllOf(result);
        assertThat(s).doesNotContain("ms-client").doesNotContain("Client OK");
    }
```

- [ ] **Step 2: Lancer → échec attendu**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|ms_client'`
Expected: échec sur `test_all_includes_ms_client_smoke...`.

- [ ] **Step 3: `rewriteTestAll` — wait_for ms-client (après le wait_for ms-admin)**

Dans `CrossCuttingConfigProcessor.java`, remplacer :
```java
        if (feat.isSpringbootAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        sb.append("echo 'Stack is ready.'\n");
```
par :
```java
        if (feat.isSpringbootAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        if (feat.isClientWebUI()) sb.append("wait_for 'ms-client' curl -fs http://localhost:8090/login\n");
        sb.append("echo 'Stack is ready.'\n");
```

- [ ] **Step 4: `rewriteTestAll` — smoke "Client OK" (après "Admin OK")**

Remplacer :
```java
        if (feat.isSpringbootAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
        sb.append("\n");
```
par :
```java
        if (feat.isSpringbootAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
        if (feat.isClientWebUI()) sb.append("curl -fs http://localhost:8090/login >/dev/null && echo 'Client OK'\n");
        sb.append("\n");
```

- [ ] **Step 5: Lancer → vert**

Run: `mvn -q test -Dtest=CrossCuttingConfigProcessorTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java \
        src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java
git commit -m "feat(generator): test-all.sh ms-client smoke check (clientWebUI)"
```

---

## Task 8 : Build complet du générateur

**Files:** aucun (vérification).

- [ ] **Step 1: Build + tests**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS` ; `Failures: 0, Errors: 0`.

- [ ] **Step 2: Confirmer la parité**

`TemplateLoaderTest` (compteur 129) passe — 17 fichiers `ms-client` ajoutés, aucun retiré.

---

## Task 9 : Génération end-to-end

**Files:** aucun (vérification). **Port dédié 8077** (piège du serveur zombie sur :8080). Lancer le générateur en arrière-plan **avec sandbox désactivé** (le bind réseau + la redirection `/tmp` échouent sous sandbox).

> **PIÈGE VÉCU — ne PAS combiner le `pkill` et le `java -jar` dans la même commande.** La ligne de commande contiendrait `generator-v5-0.0.1-SNAPSHOT.jar`, et `pkill -f '[g]enerator-v5-0.0.1'` matcherait sa propre ligne et se tuerait. Le motif crochet `[g]` empêche seulement de matcher le *littéral du motif*, pas le chemin du jar présent sur la même ligne. **Faire DEUX invocations shell séparées.**

- [ ] **Step 1a: Tuer un éventuel générateur (commande séparée, sans chemin de jar)**

Run (sandbox désactivé) : `pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null; sleep 2; pgrep -af '[g]enerator-v5' || echo clean`
Expected: `clean`.

- [ ] **Step 1b: Lancer le générateur (commande séparée, en arrière-plan, SANS pkill)**

Lancer en tâche de fond du harnais, sandbox désactivé :
```bash
java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 > /tmp/genapp.log 2>&1
```
Puis attendre (commande séparée, sandbox désactivé) :
```bash
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
grep -q "Tomcat started on port 8077" /tmp/genapp.log && echo STARTED
```
Expected: `STARTED`.

- [ ] **Step 2: Générer avec `clientWebUI=true`**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform", "groupId": "com.acme", "basePackage": "com.acme.shop", "javaVersion": "17",
    "resources": [
      { "serviceName": "order-service", "className": "Order", "routePrefix": "/api/orders", "databaseType": "POSTGRES", "idType": "LONG" }
    ],
    "batch": { "enabled": true, "grafana": false },
    "features": { "springbootAdmin": true, "clientWebUI": true }
  }' \
  -o /tmp/refc.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refcx && mkdir -p /tmp/refcx && unzip -q /tmp/refc.zip -d /tmp/refcx && echo UNZIPPED
```
Expected: `HTTP=200`, `UNZIPPED`.

- [ ] **Step 3: Inspecter (clientWebUI=true)**

```bash
echo "=== modules ==="; grep -E '<module>ms-client</module>' /tmp/refcx/ms-platform/pom.xml
echo "=== compose block ==="; grep -E '^  ms-client:' /tmp/refcx/ms-platform/docker-compose.yml
echo "=== dossier module ==="; ls /tmp/refcx/ms-platform/ms-client/pom.xml
echo "=== test-all smoke ==="; grep -E "wait_for 'ms-client'|Client OK" /tmp/refcx/ms-platform/test-all.sh
```
Expected : chaque commande renvoie une ligne (présence confirmée).

- [ ] **Step 4: Build du module généré + validation compose**

```bash
cd /tmp/refcx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo COMPOSE_OK
cd /tmp/refcx/ms-platform && mvn -q -pl ms-client -am -DskipTests package 2>&1 | grep -E 'BUILD (SUCCESS|FAILURE)|ERROR' | head
```
Expected: `COMPOSE_OK` ; `BUILD SUCCESS` (le module `ms-client` compile).
Si Docker/Maven indisponible : le noter explicitement comme NON vérifié, ne pas affirmer le succès.

- [ ] **Step 5: Générer avec `clientWebUI=false` et vérifier l'absence totale**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform", "groupId": "com.acme", "basePackage": "com.acme.shop", "javaVersion": "17",
    "resources": [ { "serviceName": "order-service", "className": "Order", "routePrefix": "/api/orders", "databaseType": "POSTGRES", "idType": "LONG" } ],
    "batch": { "enabled": true, "grafana": false },
    "features": { "springbootAdmin": false, "clientWebUI": false }
  }' \
  -o /tmp/refc0.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refc0x && mkdir -p /tmp/refc0x && unzip -q /tmp/refc0.zip -d /tmp/refc0x
echo "=== module (doit etre vide) ==="; grep -c 'ms-client' /tmp/refc0x/ms-platform/pom.xml
echo "=== compose (doit etre 0) ==="; grep -c '^  ms-client:' /tmp/refc0x/ms-platform/docker-compose.yml
echo "=== dossier (doit etre absent) ==="; ls /tmp/refc0x/ms-platform/ms-client 2>&1
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
```
Expected : `HTTP=200` ; `0` (pom) ; `0` (compose) ; « No such file or directory » (dossier `ms-client` absent).

- [ ] **Step 6: Vérifier l'arbre git propre**

Run: `cd /home/mr486/Developpement/Projets/GestoMS && git status --short`
Expected: rien (tous les changements déjà commités aux Tasks 4–7).

---

## Recovery
- `git log --oneline -6` — commits déjà passés (module, compose/env, modules+compose, test-all).
- `find src/main/resources/templates/ms-platform/ms-client -type f | wc -l` → `17` si le module est créé.
- `grep -c 'ms-client' src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` → `4` (desiredModules ×1, blocksToRemove ×1, rewriteTestAll ×2) si le générateur est câblé.
- `mvn test` SUCCESS → Tasks 1–7 faites. Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Page consumer (2b), CRUD générique (2c), notifs batch (2d), chat (2e), refresh de token (2b).
- Routes gateway pour ms-client (port direct — non requises).
- Tests unitaires embarqués dans le module `ms-client`.
