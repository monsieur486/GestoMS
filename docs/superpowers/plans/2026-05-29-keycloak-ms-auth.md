# Plan d'implémentation Keycloak ms-auth

> **Pour les workers agentiques :** SOUS-COMPÉTENCE REQUISE : Utiliser superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour implémenter ce plan tâche par tâche. Les étapes utilisent la syntaxe checkbox (`- [ ]`) pour le suivi.

**Objectif :** Ajouter le service `ms-auth` au template ZIP — expose `/auth/login`, `/auth/refresh`, `/auth/logout` appuyés sur Keycloak ; stocke les refresh tokens opaques dans Redis ; blackliste les JTI des JWT révoqués dans Redis ; le filtre gateway applique la blacklist avant le routage.

**Architecture :** `ms-auth` est un service Spring Boot MVC (port 9200) qui encapsule le flow password grant de Keycloak, en émettant des UUID opaques à la place des refresh tokens Keycloak bruts. Le gateway ajoute un `TokenBlacklistFilter` (réactif, `Ordered.HIGHEST_PRECEDENCE`) qui vérifie `auth:blacklist:{jti}` dans Redis et retourne 401 si le token est révoqué. Toutes les modifications sont faites dans le template ZIP extrait puis repackagé.

**Stack technique :** Spring Boot 3.5.5, Spring Security OAuth2 Resource Server, Spring Data Redis (bloquant pour ms-auth, réactif pour le gateway), RestTemplate pour les appels HTTP Keycloak, JUnit 5 + AssertJ pour le test FeatureFilterProcessor côté générateur.

---

## Cartographie des fichiers

**Template ZIP — nouveaux fichiers :**
- `ms-platform/ms-auth/Dockerfile`
- `ms-platform/ms-auth/pom.xml`
- `ms-platform/ms-auth/src/main/resources/application.yml`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/AuthApplication.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/controller/AuthController.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/TokenBlacklistService.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/SecurityConfig.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/RedisConfig.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginRequest.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginResponse.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/RefreshRequest.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LogoutRequest.java`
- `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/KeycloakTokenResponse.java`
- `ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter/TokenBlacklistFilter.java`

**Template ZIP — fichiers modifiés :**
- `ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java`
- `ms-platform/ms-gateway/pom.xml`
- `ms-platform/ms-gateway/src/main/resources/application.yml`
- `ms-platform/pom.xml`
- `ms-platform/docker-compose.yml`
- `ms-platform/.env`
- `ms-platform/keycloak/import/ms-realm-realm.json`
- `ms-platform/test-all.sh`

**Source du générateur — modifié :**
- `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
- `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`

---

## Tâche 1 : Extraire le template ZIP dans un répertoire de travail

**Fichiers :**
- Lecture : `src/main/resources/templates/ms-platform-template.zip`
- Répertoire de travail : `/tmp/ms-auth-work/`

- [ ] **Étape 1 : Extraire le ZIP**

```bash
rm -rf /tmp/ms-auth-work
mkdir /tmp/ms-auth-work
cd /tmp/ms-auth-work
unzip /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip
```

Résultat attendu : répertoire `ms-platform/` avec tous les fichiers du template.

- [ ] **Étape 2 : Vérifier les fichiers clés**

```bash
ls /tmp/ms-auth-work/ms-platform/
ls /tmp/ms-auth-work/ms-platform/ms-gateway/
ls /tmp/ms-auth-work/ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/
```

Résultat attendu :
- `ms-platform/` contient : common-lib, ms-eureka, ms-gateway, ms-admin, service-a, service-b, service-c, service-consumer, service-batch, docker-compose.yml, pom.xml, .env, test-all.sh
- `ms-gateway/` contient : Dockerfile, pom.xml, src/
- `constants/` contient : RedisKeys.java, RabbitQueues.java

- [ ] **Étape 3 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add -A
git commit -m "chore: extract ZIP for ms-auth additions (no file changes yet)"
```

---

## Tâche 2 : RedisKeys — ajouter les méthodes de clés auth

**Fichiers :**
- Modifier : `ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java`

Contenu actuel :
```java
package com.mr486.msplatform.common.constants;

public final class RedisKeys {
    public static final String BATCH_JOBS_ALL = "batch:jobs:all";

    public static String batchJob(String jobId) {
        return "batch:job:" + jobId;
    }

    public static String batchUserJobs(Long userId) {
        return "batch:user:" + userId + ":jobs";
    }

    private RedisKeys() {
    }
}
```

- [ ] **Étape 1 : Réécrire RedisKeys.java**

Remplacer le fichier `/tmp/ms-auth-work/ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java` par :

```java
package com.mr486.msplatform.common.constants;

public final class RedisKeys {
    public static final String BATCH_JOBS_ALL = "batch:jobs:all";

    public static String batchJob(String jobId) {
        return "batch:job:" + jobId;
    }

    public static String batchUserJobs(Long userId) {
        return "batch:user:" + userId + ":jobs";
    }

    public static String authBlacklist(String jti) {
        return "auth:blacklist:" + jti;
    }

    public static String authRefresh(String opaqueToken) {
        return "auth:refresh:" + opaqueToken;
    }

    private RedisKeys() {
    }
}
```

- [ ] **Étape 2 : Vérifier**

```bash
grep -n "authBlacklist\|authRefresh" /tmp/ms-auth-work/ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java
```

Résultat attendu :
```
17:    public static String authBlacklist(String jti) {
21:    public static String authRefresh(String opaqueToken) {
```

---

## Tâche 3 : ms-auth — Dockerfile, pom.xml, AuthApplication.java

**Fichiers :**
- Créer : `ms-platform/ms-auth/Dockerfile`
- Créer : `ms-platform/ms-auth/pom.xml`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/AuthApplication.java`

- [ ] **Étape 1 : Créer l'arborescence**

```bash
mkdir -p /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/{controller,service,configuration,dto}
mkdir -p /tmp/ms-auth-work/ms-platform/ms-auth/src/main/resources
```

- [ ] **Étape 2 : Créer le Dockerfile**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
EOF
```

- [ ] **Étape 3 : Créer le pom.xml**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.mr486</groupId><artifactId>ms-platform</artifactId><version>0.0.1-SNAPSHOT</version></parent>
  <artifactId>ms-auth</artifactId>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></dependency>
    <dependency><groupId>com.mr486</groupId><artifactId>common-lib</artifactId><version>${project.version}</version></dependency>
  </dependencies>
</project>
EOF
```

- [ ] **Étape 4 : Créer AuthApplication.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/AuthApplication.java << 'EOF'
package com.mr486.msplatform.auth;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class AuthApplication{public static void main(String[] args){SpringApplication.run(AuthApplication.class,args);}}
EOF
```

- [ ] **Étape 5 : Vérifier**

```bash
ls /tmp/ms-auth-work/ms-platform/ms-auth/
cat /tmp/ms-auth-work/ms-platform/ms-auth/pom.xml | grep artifactId
```

Résultat attendu : `ms-auth/Dockerfile`, `ms-auth/pom.xml`, `ms-auth/src/` présents ; le pom contient les artifactIds `ms-auth` et `common-lib`.

---

## Tâche 4 : ms-auth DTOs

**Fichiers :**
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginRequest.java`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginResponse.java`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/RefreshRequest.java`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LogoutRequest.java`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/KeycloakTokenResponse.java`

- [ ] **Étape 1 : Créer LoginRequest.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginRequest.java << 'EOF'
package com.mr486.msplatform.auth.dto;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.AllArgsConstructor;
@Getter @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    private String username;
    private String password;
}
EOF
```

- [ ] **Étape 2 : Créer LoginResponse.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginResponse.java << 'EOF'
package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;import lombok.Getter;
@Getter @AllArgsConstructor
public class LoginResponse {
    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
    @JsonProperty("expires_in") private long expiresIn;
}
EOF
```

- [ ] **Étape 3 : Créer RefreshRequest.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/RefreshRequest.java << 'EOF'
package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.AllArgsConstructor;
@Getter @NoArgsConstructor @AllArgsConstructor
public class RefreshRequest {
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
}
EOF
```

- [ ] **Étape 4 : Créer LogoutRequest.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LogoutRequest.java << 'EOF'
package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.AllArgsConstructor;
@Getter @NoArgsConstructor @AllArgsConstructor
public class LogoutRequest {
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
}
EOF
```

- [ ] **Étape 5 : Créer KeycloakTokenResponse.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/KeycloakTokenResponse.java << 'EOF'
package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;import lombok.NoArgsConstructor;
@Getter @NoArgsConstructor
public class KeycloakTokenResponse {
    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("refresh_token") private String refreshToken;
    @JsonProperty("expires_in") private long expiresIn;
    @JsonProperty("refresh_expires_in") private long refreshExpiresIn;
}
EOF
```

- [ ] **Étape 6 : Vérifier**

```bash
ls /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/
```

Résultat attendu : 5 fichiers `.java`.

---

## Tâche 5 : ms-auth TokenBlacklistService

**Fichiers :**
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/TokenBlacklistService.java`

- [ ] **Étape 1 : Créer TokenBlacklistService.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/TokenBlacklistService.java << 'EOF'
package com.mr486.msplatform.auth.service;
import com.mr486.msplatform.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    public void blacklist(String jti, long ttlSeconds) {
        redis.opsForValue().set(RedisKeys.authBlacklist(jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    public void storeRefreshToken(String opaqueToken, String kcRefreshToken, long ttlSeconds) {
        redis.opsForValue().set(RedisKeys.authRefresh(opaqueToken), kcRefreshToken, Duration.ofSeconds(ttlSeconds));
    }

    public Optional<String> getRefreshToken(String opaqueToken) {
        return Optional.ofNullable(redis.opsForValue().get(RedisKeys.authRefresh(opaqueToken)));
    }

    public void deleteRefreshToken(String opaqueToken) {
        redis.delete(RedisKeys.authRefresh(opaqueToken));
    }
}
EOF
```

- [ ] **Étape 2 : Vérifier**

```bash
grep -n "authBlacklist\|authRefresh\|StringRedisTemplate" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/TokenBlacklistService.java
```

Résultat attendu : les 3 présents.

---

## Tâche 6 : ms-auth AuthService

**Fichiers :**
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java`

- [ ] **Étape 1 : Créer AuthService.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java << 'EOF'
package com.mr486.msplatform.auth.service;
import com.mr486.msplatform.auth.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenBlacklistService blacklistService;
    private final RestTemplate restTemplate;

    @Value("${keycloak.internal-url:http://keycloak:8080}")
    private String keycloakInternalUrl;

    @Value("${keycloak.realm:ms-realm}")
    private String realm;

    @Value("${keycloak.client-id:ms-gateway}")
    private String clientId;

    @Value("${keycloak.client-secret:changeit-gateway}")
    private String clientSecret;

    public LoginResponse login(LoginRequest request) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", request.getUsername());
        body.add("password", request.getPassword());

        KeycloakTokenResponse kc = callKeycloak(body);

        String opaqueToken = UUID.randomUUID().toString();
        blacklistService.storeRefreshToken(opaqueToken, kc.getRefreshToken(), kc.getRefreshExpiresIn());
        return new LoginResponse(kc.getAccessToken(), opaqueToken, kc.getExpiresIn());
    }

    public LoginResponse refresh(RefreshRequest request) {
        String kcRefreshToken = blacklistService.getRefreshToken(request.getOpaqueRefreshToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", kcRefreshToken);

        KeycloakTokenResponse kc;
        try {
            kc = callKeycloak(body);
        } catch (ResponseStatusException e) {
            blacklistService.deleteRefreshToken(request.getOpaqueRefreshToken());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token refresh failed");
        }

        blacklistService.deleteRefreshToken(request.getOpaqueRefreshToken());
        String newOpaque = UUID.randomUUID().toString();
        blacklistService.storeRefreshToken(newOpaque, kc.getRefreshToken(), kc.getRefreshExpiresIn());
        return new LoginResponse(kc.getAccessToken(), newOpaque, kc.getExpiresIn());
    }

    public void logout(LogoutRequest request, JwtAuthenticationToken authentication) {
        String jti = authentication.getToken().getClaim("jti");
        Instant exp = authentication.getToken().getExpiresAt();

        if (jti != null && exp != null) {
            long ttl = Duration.between(Instant.now(), exp).getSeconds();
            if (ttl > 0) blacklistService.blacklist(jti, ttl);
        }

        if (request != null && request.getOpaqueRefreshToken() != null) {
            String kcRefresh = blacklistService.getRefreshToken(request.getOpaqueRefreshToken()).orElse(null);
            blacklistService.deleteRefreshToken(request.getOpaqueRefreshToken());
            if (kcRefresh != null) revokeKeycloak(kcRefresh);
        }
    }

    private KeycloakTokenResponse callKeycloak(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String url = keycloakInternalUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        try {
            ResponseEntity<KeycloakTokenResponse> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), KeycloakTokenResponse.class);
            KeycloakTokenResponse kc = resp.getBody();
            if (kc == null || kc.getAccessToken() == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No token in Keycloak response");
            return kc;
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Keycloak rejected credentials");
        }
    }

    private void revokeKeycloak(String kcRefreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", kcRefreshToken);
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            String url = keycloakInternalUrl + "/realms/" + realm + "/protocol/openid-connect/revoke";
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception ignored) {
            // best-effort : l'échec de révocation ne rollback pas le logout
        }
    }
}
EOF
```

- [ ] **Étape 2 : Vérifier**

```bash
grep -n "public LoginResponse login\|public LoginResponse refresh\|public void logout" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java
```

Résultat attendu : 3 lignes avec les signatures de méthodes publiques.

---

## Tâche 7 : ms-auth AuthController, SecurityConfig, RedisConfig, application.yml

**Fichiers :**
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/controller/AuthController.java`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/SecurityConfig.java`
- Créer : `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/RedisConfig.java`
- Créer : `ms-platform/ms-auth/src/main/resources/application.yml`

- [ ] **Étape 1 : Créer AuthController.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/controller/AuthController.java << 'EOF'
package com.mr486.msplatform.auth.controller;
import com.mr486.msplatform.auth.dto.*;
import com.mr486.msplatform.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request,
                       Authentication authentication) {
        authService.logout(request, (JwtAuthenticationToken) authentication);
    }
}
EOF
```

- [ ] **Étape 2 : Créer SecurityConfig.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/SecurityConfig.java << 'EOF'
package com.mr486.msplatform.auth.configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/auth/login", "/auth/refresh", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }
}
EOF
```

- [ ] **Étape 3 : Créer RedisConfig.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/RedisConfig.java << 'EOF'
package com.mr486.msplatform.auth.configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RedisConfig {

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
EOF
```

- [ ] **Étape 4 : Créer application.yml**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/resources/application.yml << 'EOF'
server:
  port: ${AUTH_PORT:9200}
spring:
  application:
    name: ms-auth
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8089/realms/ms-realm}
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://keycloak:8080/realms/ms-realm/protocol/openid-connect/certs}
keycloak:
  internal-url: ${KEYCLOAK_INTERNAL_URL:http://keycloak:8080}
  realm: ms-realm
  client-id: ms-gateway
  client-secret: ${KEYCLOAK_CLIENT_SECRET:changeit-gateway}
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}
management:
  endpoints:
    web:
      exposure:
        include: health,info
EOF
```

- [ ] **Étape 5 : Vérifier**

```bash
grep -n "permitAll\|oauth2ResourceServer\|RestTemplate" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/SecurityConfig.java
grep -n "AUTH_PORT\|REDIS_HOST\|keycloak:" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/resources/application.yml
```

Résultat attendu : SecurityConfig a `permitAll` pour `/auth/login`, `/auth/refresh`. application.yml contient les 3 clés.

---

## Tâche 8 : Gateway — TokenBlacklistFilter

**Fichiers :**
- Créer : `ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter/TokenBlacklistFilter.java`

- [ ] **Étape 1 : Créer le répertoire filter et le fichier**

```bash
mkdir -p /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter
cat > /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter/TokenBlacklistFilter.java << 'EOF'
package com.mr486.msplatform.gateway.filter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TokenBlacklistFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redis;

    public TokenBlacklistFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String jti = extractJti(authHeader.substring(7));
        if (jti == null) {
            return chain.filter(exchange);
        }
        return redis.hasKey("auth:blacklist:" + jti)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        DataBuffer buffer = exchange.getResponse().bufferFactory()
                                .wrap("{\"error\":\"token_revoked\"}".getBytes(StandardCharsets.UTF_8));
                        return exchange.getResponse().writeWith(Mono.just(buffer));
                    }
                    return chain.filter(exchange);
                });
    }

    private String extractJti(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int jtiIdx = payload.indexOf("\"jti\"");
            if (jtiIdx < 0) return null;
            int colonIdx = payload.indexOf(':', jtiIdx);
            int startQuote = payload.indexOf('"', colonIdx);
            int endQuote = payload.indexOf('"', startQuote + 1);
            if (startQuote < 0 || endQuote < 0) return null;
            return payload.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return null;
        }
    }
}
EOF
```

- [ ] **Étape 2 : Vérifier**

```bash
grep -n "GlobalFilter\|Ordered.HIGHEST_PRECEDENCE\|auth:blacklist:" /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter/TokenBlacklistFilter.java
```

Résultat attendu : les 3 présents.

---

## Tâche 9 : Gateway — pom.xml + application.yml

**Fichiers :**
- Modifier : `ms-platform/ms-gateway/pom.xml`
- Modifier : `ms-platform/ms-gateway/src/main/resources/application.yml`

Contenu actuel du pom.xml gateway :
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.mr486</groupId><artifactId>ms-platform</artifactId><version>0.0.1-SNAPSHOT</version></parent>
  <artifactId>ms-gateway</artifactId>
  <dependencies>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-gateway-server-webflux</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>

  </dependencies>
</project>
```

- [ ] **Étape 1 : Réécrire le pom.xml gateway pour ajouter Redis réactif**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-gateway/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.mr486</groupId><artifactId>ms-platform</artifactId><version>0.0.1-SNAPSHOT</version></parent>
  <artifactId>ms-gateway</artifactId>
  <dependencies>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-gateway-server-webflux</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis-reactive</artifactId></dependency>

  </dependencies>
</project>
EOF
```

- [ ] **Étape 2 : Réécrire l'application.yml gateway pour ajouter la route ms-auth et la config Redis**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/resources/application.yml << 'EOF'
server:
  port: ${GATEWAY_PORT:9000}
spring:
  application:
    name: ms-gateway
  main:
    web-application-type: reactive
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000
        response-timeout: 120s
      server:
        webflux:
          routes:
            - id: ms-auth
              uri: lb://ms-auth
              predicates:
                - Path=/auth/**
            - id: service-a
              uri: lb://service-a
              predicates:
                - Path=/service-a/**
              filters:
                - StripPrefix=1
            - id: service-b
              uri: lb://service-b
              predicates:
                - Path=/service-b/**
              filters:
                - StripPrefix=1
            - id: service-c
              uri: lb://service-c
              predicates:
                - Path=/service-c/**
              filters:
                - StripPrefix=1
            - id: service-consumer
              uri: lb://service-consumer
              predicates:
                - Path=/service-consumer/**
              filters:
                - StripPrefix=1
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}
management:
  endpoints:
    web:
      exposure:
        include: health,info
EOF
```

- [ ] **Étape 3 : Vérifier**

```bash
grep -n "redis-reactive\|ms-auth\|REDIS_HOST" /tmp/ms-auth-work/ms-platform/ms-gateway/pom.xml /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/resources/application.yml
```

Résultat attendu : `redis-reactive` dans le pom.xml ; `ms-auth` et `REDIS_HOST` dans application.yml.

---

## Tâche 10 : pom.xml racine + docker-compose.yml + .env

**Fichiers :**
- Modifier : `ms-platform/pom.xml`
- Modifier : `ms-platform/docker-compose.yml`
- Modifier : `ms-platform/.env`

- [ ] **Étape 1 : Ajouter le module ms-auth au pom.xml racine**

Éditer `/tmp/ms-auth-work/ms-platform/pom.xml`. Le bloc `<modules>` se termine actuellement par :
```xml
    <module>service-batch</module>
  </modules>
```

Le changer en :
```xml
    <module>service-batch</module>
    <module>ms-auth</module>
  </modules>
```

Vérifier :
```bash
grep "ms-auth" /tmp/ms-auth-work/ms-platform/pom.xml
```

Résultat attendu : `<module>ms-auth</module>` présent.

- [ ] **Étape 2 : Ajouter le service ms-auth dans docker-compose.yml**

Insérer le bloc service `ms-auth` avant la section `volumes:` en fin de fichier :

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

Via sed :
```bash
sed -i '/^volumes:/i\  ms-auth:\n    build: ./ms-auth\n    env_file: [.env]\n    depends_on: [ms-eureka, keycloak, redis]\n    environment:\n      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n      KEYCLOAK_INTERNAL_URL: http://keycloak:8080\n      REDIS_HOST: redis\n    ports: ["9200:9200"]\n' /tmp/ms-auth-work/ms-platform/docker-compose.yml
```

Vérifier :
```bash
grep -A 8 "ms-auth:" /tmp/ms-auth-work/ms-platform/docker-compose.yml
```

Résultat attendu : bloc service `ms-auth` avec `depends_on`, `KEYCLOAK_INTERNAL_URL` et `ports: ["9200:9200"]`.

- [ ] **Étape 3 : Ajouter AUTH_PORT dans .env**

```bash
echo "AUTH_PORT=9200" >> /tmp/ms-auth-work/ms-platform/.env
```

Vérifier :
```bash
grep "AUTH_PORT" /tmp/ms-auth-work/ms-platform/.env
```

Résultat attendu : `AUTH_PORT=9200`.

---

## Tâche 11 : Realm Keycloak JSON — durées de vie des tokens

**Fichiers :**
- Modifier : `ms-platform/keycloak/import/ms-realm-realm.json`

- [ ] **Étape 1 : Ajouter accessTokenLifespan, ssoSessionMaxLifespan et refreshTokenMaxReuse**

Le JSON du realm ne contient actuellement aucun de ces champs au niveau racine. Les ajouter via Python :

```bash
python3 << 'PYEOF'
import json

path = "/tmp/ms-auth-work/ms-platform/keycloak/import/ms-realm-realm.json"
with open(path) as f:
    realm = json.load(f)

realm["accessTokenLifespan"] = 300
realm["ssoSessionMaxLifespan"] = 1800
realm["refreshTokenMaxReuse"] = 0

with open(path, "w") as f:
    json.dump(realm, f, indent=2)
print("Done")
PYEOF
```

- [ ] **Étape 2 : Vérifier**

```bash
python3 -c "import json; d=json.load(open('/tmp/ms-auth-work/ms-platform/keycloak/import/ms-realm-realm.json')); print('accessTokenLifespan:', d['accessTokenLifespan']); print('ssoSessionMaxLifespan:', d['ssoSessionMaxLifespan']); print('refreshTokenMaxReuse:', d['refreshTokenMaxReuse'])"
```

Résultat attendu :
```
accessTokenLifespan: 300
ssoSessionMaxLifespan: 1800
refreshTokenMaxReuse: 0
```

---

## Tâche 12 : Mettre à jour test-all.sh

**Fichiers :**
- Modifier : `ms-platform/test-all.sh`

Le script actuel utilise `get_token()` qui appelle Keycloak directement. Remplacer par l'auth via l'endpoint ms-auth du gateway. Ajouter les tests de refresh et de logout.

- [ ] **Étape 1 : Réécrire test-all.sh**

```bash
cat > /tmp/ms-auth-work/ms-platform/test-all.sh << 'EOF'
#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8089}
GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}

auth_login(){
  curl -s -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}"
}

auth_refresh(){
  curl -s -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"opaque_refresh_token\":\"$1\"}"
}

check_token(){
  if [ -z "${!1}" ]; then
    echo "Unable to get $2 token"
    exit 1
  fi
}

assert_http(){
  local label=$1
  local expected=$2
  local method=$3
  local token=$4
  local url=$5

  local response_file
  response_file=$(mktemp)

  local status
  status=$(curl -s -o "$response_file" -w "%{http_code}" \
    -X "$method" \
    -H "Authorization: Bearer $token" \
    "$url")

  if [ "$status" = "$expected" ]; then
    echo "OK $label -> $status"
  else
    echo "FAIL $label expected $expected got $status"
    cat "$response_file"
    rm -f "$response_file"
    exit 1
  fi

  rm -f "$response_file"
}

assert_contains(){
  local label=$1
  local haystack=$2
  local needle=$3

  if echo "$haystack" | grep -q "$needle"; then
    echo "OK $label contains $needle"
  else
    echo "FAIL $label missing $needle"
    echo "$haystack"
    exit 1
  fi
}

echo 'Getting tokens via ms-auth...'
ADMIN_LOGIN=$(auth_login test-admin admin123)
TOKEN_ADMIN=$(echo "$ADMIN_LOGIN" | jq -r '.access_token // empty')
OPAQUE_ADMIN=$(echo "$ADMIN_LOGIN" | jq -r '.opaque_refresh_token // empty')

BATCH_LOGIN=$(auth_login test-batch user123)
TOKEN_BATCH=$(echo "$BATCH_LOGIN" | jq -r '.access_token // empty')

SERVICE_A_LOGIN=$(auth_login test-service-a user123)
TOKEN_SERVICE_A=$(echo "$SERVICE_A_LOGIN" | jq -r '.access_token // empty')

SERVICE_B_LOGIN=$(auth_login test-service-b user123)
TOKEN_SERVICE_B=$(echo "$SERVICE_B_LOGIN" | jq -r '.access_token // empty')

SERVICE_C_LOGIN=$(auth_login test-service-c user123)
TOKEN_SERVICE_C=$(echo "$SERVICE_C_LOGIN" | jq -r '.access_token // empty')

check_token TOKEN_ADMIN ADMIN
check_token TOKEN_BATCH BATCH
check_token TOKEN_SERVICE_A SERVICE_A
check_token TOKEN_SERVICE_B SERVICE_B
check_token TOKEN_SERVICE_C SERVICE_C

cat > tokens.env <<TEOF
TOKEN_ADMIN=${TOKEN_ADMIN}
TOKEN_BATCH=${TOKEN_BATCH}
TOKEN_SERVICE_A=${TOKEN_SERVICE_A}
TOKEN_SERVICE_B=${TOKEN_SERVICE_B}
TOKEN_SERVICE_C=${TOKEN_SERVICE_C}
TEOF
chmod 600 tokens.env

echo 'Testing resource role matrix...'
assert_http 'ADMIN can access service-a' 200 GET "$TOKEN_ADMIN" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-a user can access own resource' 200 GET "$TOKEN_SERVICE_A" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-a user cannot access service-b' 403 GET "$TOKEN_SERVICE_A" "$GATEWAY_URL/service-b/api/resources-b"
assert_http 'service-a user cannot access service-c' 403 GET "$TOKEN_SERVICE_A" "$GATEWAY_URL/service-c/api/resources-c"

assert_http 'ADMIN can access service-b' 200 GET "$TOKEN_ADMIN" "$GATEWAY_URL/service-b/api/resources-b"
assert_http 'service-b user can access own resource' 200 GET "$TOKEN_SERVICE_B" "$GATEWAY_URL/service-b/api/resources-b"
assert_http 'service-b user cannot access service-a' 403 GET "$TOKEN_SERVICE_B" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-b user cannot access service-c' 403 GET "$TOKEN_SERVICE_B" "$GATEWAY_URL/service-c/api/resources-c"

assert_http 'ADMIN can access service-c' 200 GET "$TOKEN_ADMIN" "$GATEWAY_URL/service-c/api/resources-c"
assert_http 'service-c user can access own resource' 200 GET "$TOKEN_SERVICE_C" "$GATEWAY_URL/service-c/api/resources-c"
assert_http 'service-c user cannot access service-a' 403 GET "$TOKEN_SERVICE_C" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'service-c user cannot access service-b' 403 GET "$TOKEN_SERVICE_C" "$GATEWAY_URL/service-b/api/resources-b"

echo 'Testing infrastructure...'
curl -fs http://localhost:8761 >/dev/null && echo 'Eureka OK'
curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'

echo 'Testing service-consumer aggregation...'
AGG_RESPONSE=$(curl -s \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  "$GATEWAY_URL/service-consumer/api/aggregate")

AGG_STATUS=$(curl -s -o /tmp/aggregate-response.txt -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  "$GATEWAY_URL/service-consumer/api/aggregate")

if [ "$AGG_STATUS" != "200" ]; then
  echo "FAIL ADMIN aggregate expected 200 got $AGG_STATUS"
  cat /tmp/aggregate-response.txt
  exit 1
fi

echo 'OK ADMIN aggregate -> 200'
assert_contains 'aggregate response' "$AGG_RESPONSE" 'service-a'
assert_contains 'aggregate response' "$AGG_RESPONSE" 'service-b'
assert_contains 'aggregate response' "$AGG_RESPONSE" 'service-c'

echo 'Testing batch jobs...'
assert_http 'BATCH user cannot access service-a' 403 GET "$TOKEN_BATCH" "$GATEWAY_URL/service-a/api/resources-a"
assert_http 'BATCH job accepted' 202 POST "$TOKEN_BATCH" "$GATEWAY_URL/service-consumer/api/users/1/batch-jobs"

echo 'Testing refresh token...'
REFRESH_RESPONSE=$(auth_refresh "$OPAQUE_ADMIN")
TOKEN_ADMIN_REFRESHED=$(echo "$REFRESH_RESPONSE" | jq -r '.access_token // empty')
if [ -z "$TOKEN_ADMIN_REFRESHED" ]; then
  echo "FAIL refresh token — no access_token in response"
  echo "$REFRESH_RESPONSE"
  exit 1
fi
echo "OK refresh token -> new access_token received"
assert_http 'Refreshed token works on service-a' 200 GET "$TOKEN_ADMIN_REFRESHED" "$GATEWAY_URL/service-a/api/resources-a"

echo 'Testing logout and blacklist...'
LOGOUT_LOGIN=$(auth_login test-service-a user123)
LOGOUT_ACCESS=$(echo "$LOGOUT_LOGIN" | jq -r '.access_token // empty')
LOGOUT_OPAQUE=$(echo "$LOGOUT_LOGIN" | jq -r '.opaque_refresh_token // empty')

assert_http 'Token works before logout' 200 GET "$LOGOUT_ACCESS" "$GATEWAY_URL/service-a/api/resources-a"

LOGOUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$GATEWAY_URL/auth/logout" \
  -H "Authorization: Bearer $LOGOUT_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"opaque_refresh_token\":\"$LOGOUT_OPAQUE\"}")
if [ "$LOGOUT_STATUS" != "204" ]; then
  echo "FAIL logout expected 204 got $LOGOUT_STATUS"
  exit 1
fi
echo "OK logout -> 204"

assert_http 'Blacklisted token rejected by gateway' 401 GET "$LOGOUT_ACCESS" "$GATEWAY_URL/service-a/api/resources-a"

STALE_REFRESH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$GATEWAY_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"opaque_refresh_token\":\"$LOGOUT_OPAQUE\"}")
if [ "$STALE_REFRESH_STATUS" != "401" ]; then
  echo "FAIL stale refresh expected 401 got $STALE_REFRESH_STATUS"
  exit 1
fi
echo "OK stale refresh token -> 401"

echo 'All tests passed. tokens.env generated.'
EOF
chmod +x /tmp/ms-auth-work/ms-platform/test-all.sh
```

- [ ] **Étape 2 : Vérifier**

```bash
grep -n "auth_login\|auth_refresh\|Blacklisted token\|stale refresh" /tmp/ms-auth-work/ms-platform/test-all.sh
```

Résultat attendu : les 4 patterns présents.

---

## Tâche 13 : Repackager le ZIP et vérifier que les tests du générateur passent

**Fichiers :**
- Écraser : `src/main/resources/templates/ms-platform-template.zip`

- [ ] **Étape 1 : Repackager le ZIP**

```bash
cd /tmp/ms-auth-work
zip -r /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip ms-platform/
```

- [ ] **Étape 2 : Vérifier que le ZIP contient les nouveaux fichiers**

```bash
unzip -l /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip | grep "ms-auth"
```

Résultat attendu : entrées ms-auth/ incluant AuthApplication.java, AuthController.java, AuthService.java, TokenBlacklistService.java, application.yml, pom.xml, Dockerfile.

```bash
unzip -l /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip | grep "TokenBlacklistFilter"
```

Résultat attendu : `ms-platform/ms-gateway/src/main/java/.../gateway/filter/TokenBlacklistFilter.java` présent.

- [ ] **Étape 3 : Lancer les tests du générateur**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn test
```

Résultat attendu : BUILD SUCCESS, tous les tests existants passent (53 tests ou plus, 0 échec).

- [ ] **Étape 4 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform-template.zip
git commit -m "feat: add ms-auth service + gateway TokenBlacklistFilter to template ZIP"
```

---

## Tâche 14 : FeatureFilterProcessor — exclure ms-auth quand keycloak=false (TDD)

**Fichiers :**
- Modifier : `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
- Modifier : `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`

Contexte : `FeatureFilterProcessor` filtre les fichiers en fonction des feature flags de la requête. Quand `keycloak=false`, le répertoire `keycloak/` est exclu. `ms-auth` dépend de Keycloak (il encapsule le login Keycloak), donc `ms-auth/` doit aussi être exclu quand `keycloak=false`.

- [ ] **Étape 1 : Lire le fichier de test actuel**

```bash
cat src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java
```

Observer le pattern des tests d'exclusion existants :

```java
@Test
void excludes_keycloak_when_disabled() {
    FeatureOptions features = FeatureOptions.builder().keycloak(false).build();
    // ... setup ...
    List<GeneratedFile> result = processor.process(files, ctx);
    assertThat(result).noneMatch(f -> f.path().contains("/keycloak/"));
}
```

- [ ] **Étape 2 : Ajouter le test en échec pour l'exclusion ms-auth**

Ouvrir `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java` et ajouter après le test `excludes_keycloak_when_disabled` existant :

```java
@Test
void excludes_ms_auth_when_keycloak_disabled() {
    FeatureOptions features = FeatureOptions.builder().keycloak(false).build();
    PlatformGenerationRequest request = PlatformGenerationRequest.builder()
            .name("my-app").features(features).build();
    GenerationContext ctx = GenerationContext.from(request);
    List<GeneratedFile> files = List.of(
            new GeneratedFile("my-app/ms-auth/pom.xml", new byte[]{1}, false),
            new GeneratedFile("my-app/ms-auth/src/main/java/Auth.java", new byte[]{1}, false),
            new GeneratedFile("my-app/ms-gateway/filter/TokenBlacklistFilter.java", new byte[]{1}, false),
            new GeneratedFile("my-app/service-a/pom.xml", new byte[]{1}, false)
    );

    List<GeneratedFile> result = processor.process(files, ctx);

    assertThat(result).noneMatch(f -> f.path().contains("/ms-auth/"));
    assertThat(result).hasSize(2); // le filtre gateway et service-a restent
}
```

- [ ] **Étape 3 : Lancer le test pour vérifier qu'il échoue**

```bash
mvn test -pl . -Dtest=FeatureFilterProcessorTest#excludes_ms_auth_when_keycloak_disabled -q 2>&1 | tail -20
```

Résultat attendu : FAIL — les chemins `ms-auth` ne sont pas encore filtrés.

- [ ] **Étape 4 : Lire FeatureFilterProcessor pour trouver où ajouter la règle**

```bash
cat src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java
```

Trouver le bloc qui gère `keycloak=false`. Il exclut actuellement `keycloak/`. Ajouter `ms-auth/` à la même condition.

- [ ] **Étape 5 : Ajouter la règle d'exclusion ms-auth**

Dans `FeatureFilterProcessor.java`, trouver le bloc qui gère `keycloak=false` :

```java
if (!features.isKeycloak()) {
    if (rel.startsWith("keycloak/")) return true;
}
```

Ajouter le check `ms-auth/` dans la même condition :

```java
if (!features.isKeycloak()) {
    if (rel.startsWith("keycloak/")) return true;
    if (rel.startsWith("ms-auth/")) return true;
}
```

(L'emplacement exact dépend de la structure actuelle du fichier — voir étape 4.)

- [ ] **Étape 6 : Lancer le test pour vérifier qu'il passe**

```bash
mvn test -pl . -Dtest=FeatureFilterProcessorTest#excludes_ms_auth_when_keycloak_disabled -q 2>&1 | tail -10
```

Résultat attendu : BUILD SUCCESS, le test passe.

- [ ] **Étape 7 : Lancer tous les tests**

```bash
mvn test
```

Résultat attendu : BUILD SUCCESS, tous les tests passent (aucune régression).

- [ ] **Étape 8 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java
git add src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java
git commit -m "feat: exclude ms-auth from generated output when keycloak=false"
```

---

## Auto-revue

**Couverture de la spec :**

| Exigence spec | Tâche |
|---|---|
| POST /auth/login avec opaque refresh token | Tâche 6 (AuthService.login) |
| POST /auth/refresh avec rotation de token | Tâche 6 (AuthService.refresh) |
| POST /auth/logout avec blacklist JTI + révocation | Tâche 6 (AuthService.logout) |
| Redis : auth:blacklist:{jti} avec TTL | Tâche 5 (TokenBlacklistService.blacklist) |
| Redis : auth:refresh:{uuid} avec TTL | Tâche 5 (TokenBlacklistService.storeRefreshToken) |
| RedisKeys.authBlacklist + authRefresh | Tâche 2 |
| Gateway TokenBlacklistFilter réactif | Tâche 8 |
| Gateway dépendance Redis réactive + route ms-auth | Tâche 9 |
| ms-auth sécurité : login+refresh publics, logout authentifié | Tâche 7 (SecurityConfig) |
| Realm Keycloak : accessTokenLifespan=300, ssoSessionMaxLifespan=1800, refreshTokenMaxReuse=0 | Tâche 11 |
| docker-compose service ms-auth | Tâche 10 |
| pom.xml racine module ms-auth | Tâche 10 |
| .env AUTH_PORT=9200 | Tâche 10 |
| test-all.sh via ms-auth + test refresh + test logout/blacklist | Tâche 12 |
| FeatureFilterProcessor : exclure ms-auth quand keycloak=false | Tâche 14 |

Toutes les exigences de la spec sont couvertes.

**Scan des placeholders :** Aucun TBD, aucun "ajouter la gestion d'erreurs plus tard", aucune section incomplète.

**Cohérence des types :** `LoginResponse`, `RefreshRequest`, `LogoutRequest`, `KeycloakTokenResponse` utilisés de façon cohérente dans les tâches 4, 6, 7. Les noms de méthodes de `TokenBlacklistService` (`blacklist`, `storeRefreshToken`, `getRefreshToken`, `deleteRefreshToken`) sont appelés correctement dans la tâche 6. `RedisKeys.authBlacklist(jti)` et `RedisKeys.authRefresh(opaqueToken)` définis en tâche 2 et utilisés en tâches 5, 8.
