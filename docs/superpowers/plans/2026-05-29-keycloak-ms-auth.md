# Keycloak ms-auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `ms-auth` service to the template ZIP — exposes `/auth/login`, `/auth/refresh`, `/auth/logout` backed by Keycloak; stores opaque refresh tokens in Redis; blacklists revoked JWT JTIs in Redis; gateway filter enforces the blacklist before routing.

**Architecture:** `ms-auth` is a Spring Boot MVC service (port 9200) that wraps Keycloak's password grant flow, issuing opaque UUIDs instead of raw Keycloak refresh tokens. The gateway adds a `TokenBlacklistFilter` (reactive, `Ordered.HIGHEST_PRECEDENCE`) that checks `auth:blacklist:{jti}` in Redis and returns 401 if the token was revoked. All changes are made inside the extracted template ZIP and then repackaged.

**Tech Stack:** Spring Boot 3.5.5, Spring Security OAuth2 Resource Server, Spring Data Redis (blocking for ms-auth, reactive for gateway), RestTemplate for Keycloak HTTP calls, JUnit 5 + AssertJ for the generator-side FeatureFilterProcessor test.

---

## File Map

**Template ZIP — new files:**
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

**Template ZIP — modified files:**
- `ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java`
- `ms-platform/ms-gateway/pom.xml`
- `ms-platform/ms-gateway/src/main/resources/application.yml`
- `ms-platform/pom.xml`
- `ms-platform/docker-compose.yml`
- `ms-platform/.env`
- `ms-platform/keycloak/import/ms-realm-realm.json`
- `ms-platform/test-all.sh`

**Generator source — modified:**
- `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
- `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`

---

## Task 1: Extract template ZIP to working directory

**Files:**
- Read: `src/main/resources/templates/ms-platform-template.zip`
- Working dir: `/tmp/ms-auth-work/`

- [ ] **Step 1: Extract ZIP**

```bash
rm -rf /tmp/ms-auth-work
mkdir /tmp/ms-auth-work
cd /tmp/ms-auth-work
unzip /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip
```

Expected: `ms-platform/` directory with all template files.

- [ ] **Step 2: Verify key files exist**

```bash
ls /tmp/ms-auth-work/ms-platform/
ls /tmp/ms-auth-work/ms-platform/ms-gateway/
ls /tmp/ms-auth-work/ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/
```

Expected:
- `ms-platform/` contains: common-lib, ms-eureka, ms-gateway, ms-admin, service-a, service-b, service-c, service-consumer, service-batch, docker-compose.yml, pom.xml, .env, test-all.sh
- `ms-gateway/` contains: Dockerfile, pom.xml, src/
- `constants/` contains: RedisKeys.java, RabbitQueues.java

- [ ] **Step 3: Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add -A
git commit -m "chore: extract ZIP for ms-auth additions (no file changes yet)"
```

---

## Task 2: RedisKeys — add auth key methods

**Files:**
- Modify: `ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java`

Current content:
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

- [ ] **Step 1: Overwrite RedisKeys.java**

Replace the file `/tmp/ms-auth-work/ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java` with:

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

- [ ] **Step 2: Verify**

```bash
grep -n "authBlacklist\|authRefresh" /tmp/ms-auth-work/ms-platform/common-lib/src/main/java/com/mr486/msplatform/common/constants/RedisKeys.java
```

Expected:
```
17:    public static String authBlacklist(String jti) {
21:    public static String authRefresh(String opaqueToken) {
```

---

## Task 3: ms-auth — Dockerfile, pom.xml, AuthApplication.java

**Files:**
- Create: `ms-platform/ms-auth/Dockerfile`
- Create: `ms-platform/ms-auth/pom.xml`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/AuthApplication.java`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/{controller,service,configuration,dto}
mkdir -p /tmp/ms-auth-work/ms-platform/ms-auth/src/main/resources
```

- [ ] **Step 2: Create Dockerfile**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
EOF
```

- [ ] **Step 3: Create pom.xml**

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

- [ ] **Step 4: Create AuthApplication.java**

```bash
cat > /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/AuthApplication.java << 'EOF'
package com.mr486.msplatform.auth;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class AuthApplication{public static void main(String[] args){SpringApplication.run(AuthApplication.class,args);}}
EOF
```

- [ ] **Step 5: Verify**

```bash
ls /tmp/ms-auth-work/ms-platform/ms-auth/
cat /tmp/ms-auth-work/ms-platform/ms-auth/pom.xml | grep artifactId
```

Expected: `ms-auth/Dockerfile`, `ms-auth/pom.xml`, `ms-auth/src/` present; pom has `ms-auth` and `common-lib` artifactIds.

---

## Task 4: ms-auth DTOs

**Files:**
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginRequest.java`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LoginResponse.java`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/RefreshRequest.java`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/LogoutRequest.java`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/KeycloakTokenResponse.java`

- [ ] **Step 1: Create LoginRequest.java**

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

- [ ] **Step 2: Create LoginResponse.java**

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

- [ ] **Step 3: Create RefreshRequest.java**

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

- [ ] **Step 4: Create LogoutRequest.java**

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

- [ ] **Step 5: Create KeycloakTokenResponse.java**

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

- [ ] **Step 6: Verify**

```bash
ls /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/
```

Expected: 5 `.java` files.

---

## Task 5: ms-auth TokenBlacklistService

**Files:**
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/TokenBlacklistService.java`

- [ ] **Step 1: Create TokenBlacklistService.java**

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

- [ ] **Step 2: Verify**

```bash
grep -n "authBlacklist\|authRefresh\|StringRedisTemplate" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/TokenBlacklistService.java
```

Expected: all three present.

---

## Task 6: ms-auth AuthService

**Files:**
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java`

- [ ] **Step 1: Create AuthService.java**

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
            // best-effort: revocation failure doesn't roll back logout
        }
    }
}
EOF
```

- [ ] **Step 2: Verify**

```bash
grep -n "public LoginResponse login\|public LoginResponse refresh\|public void logout" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java
```

Expected: 3 lines with public method signatures.

---

## Task 7: ms-auth AuthController, SecurityConfig, RedisConfig, application.yml

**Files:**
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/controller/AuthController.java`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/SecurityConfig.java`
- Create: `ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/RedisConfig.java`
- Create: `ms-platform/ms-auth/src/main/resources/application.yml`

- [ ] **Step 1: Create AuthController.java**

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

- [ ] **Step 2: Create SecurityConfig.java**

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

- [ ] **Step 3: Create RedisConfig.java**

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

- [ ] **Step 4: Create application.yml**

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

- [ ] **Step 5: Verify**

```bash
grep -n "permitAll\|oauth2ResourceServer\|RestTemplate" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/configuration/SecurityConfig.java
grep -n "AUTH_PORT\|REDIS_HOST\|keycloak:" /tmp/ms-auth-work/ms-platform/ms-auth/src/main/resources/application.yml
```

Expected: SecurityConfig has `permitAll` for `/auth/login`, `/auth/refresh`. application.yml has all three keys.

---

## Task 8: Gateway — TokenBlacklistFilter

**Files:**
- Create: `ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter/TokenBlacklistFilter.java`

- [ ] **Step 1: Create filter directory and file**

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

- [ ] **Step 2: Verify**

```bash
grep -n "GlobalFilter\|Ordered.HIGHEST_PRECEDENCE\|auth:blacklist:" /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/java/com/mr486/msplatform/gateway/filter/TokenBlacklistFilter.java
```

Expected: all 3 present.

---

## Task 9: Gateway — pom.xml + application.yml

**Files:**
- Modify: `ms-platform/ms-gateway/pom.xml`
- Modify: `ms-platform/ms-gateway/src/main/resources/application.yml`

Current gateway pom.xml content:
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

- [ ] **Step 1: Overwrite gateway pom.xml to add Redis reactive**

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

- [ ] **Step 2: Overwrite gateway application.yml to add ms-auth route and Redis config**

Current gateway application.yml ends at line 48 (`include: health,info`). Replace with:

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

- [ ] **Step 3: Verify**

```bash
grep -n "redis-reactive\|ms-auth\|REDIS_HOST" /tmp/ms-auth-work/ms-platform/ms-gateway/pom.xml /tmp/ms-auth-work/ms-platform/ms-gateway/src/main/resources/application.yml
```

Expected: `redis-reactive` in pom.xml; `ms-auth` and `REDIS_HOST` in application.yml.

---

## Task 10: Root pom.xml + docker-compose.yml + .env

**Files:**
- Modify: `ms-platform/pom.xml`
- Modify: `ms-platform/docker-compose.yml`
- Modify: `ms-platform/.env`

- [ ] **Step 1: Add ms-auth module to root pom.xml**

Edit `/tmp/ms-auth-work/ms-platform/pom.xml`. The `<modules>` block currently ends with:
```xml
    <module>service-batch</module>
  </modules>
```

Change it to:
```xml
    <module>service-batch</module>
    <module>ms-auth</module>
  </modules>
```

Verify:
```bash
grep "ms-auth" /tmp/ms-auth-work/ms-platform/pom.xml
```

Expected: `<module>ms-auth</module>` present.

- [ ] **Step 2: Add ms-auth service to docker-compose.yml**

Append the `ms-auth` service block before the `volumes:` section. Find the line `volumes:` at the end of `docker-compose.yml` and insert before it:

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

Using sed to insert before `^volumes:`:
```bash
sed -i '/^volumes:/i\  ms-auth:\n    build: ./ms-auth\n    env_file: [.env]\n    depends_on: [ms-eureka, keycloak, redis]\n    environment:\n      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n      KEYCLOAK_INTERNAL_URL: http://keycloak:8080\n      REDIS_HOST: redis\n    ports: ["9200:9200"]\n' /tmp/ms-auth-work/ms-platform/docker-compose.yml
```

Verify:
```bash
grep -A 8 "ms-auth:" /tmp/ms-auth-work/ms-platform/docker-compose.yml
```

Expected: `ms-auth` service block with `depends_on`, `KEYCLOAK_INTERNAL_URL`, and `ports: ["9200:9200"]`.

- [ ] **Step 3: Add AUTH_PORT to .env**

```bash
echo "AUTH_PORT=9200" >> /tmp/ms-auth-work/ms-platform/.env
```

Verify:
```bash
grep "AUTH_PORT" /tmp/ms-auth-work/ms-platform/.env
```

Expected: `AUTH_PORT=9200`.

---

## Task 11: Keycloak realm JSON — token lifespans

**Files:**
- Modify: `ms-platform/keycloak/import/ms-realm-realm.json`

- [ ] **Step 1: Add accessTokenLifespan and ssoSessionMaxLifespan**

The realm JSON currently has neither field at top level. Add them. Use Python to update the JSON cleanly:

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

- [ ] **Step 2: Verify**

```bash
python3 -c "import json; d=json.load(open('/tmp/ms-auth-work/ms-platform/keycloak/import/ms-realm-realm.json')); print('accessTokenLifespan:', d['accessTokenLifespan']); print('ssoSessionMaxLifespan:', d['ssoSessionMaxLifespan']); print('refreshTokenMaxReuse:', d['refreshTokenMaxReuse'])"
```

Expected:
```
accessTokenLifespan: 300
ssoSessionMaxLifespan: 1800
refreshTokenMaxReuse: 0
```

---

## Task 12: Update test-all.sh

**Files:**
- Modify: `ms-platform/test-all.sh`

The current script uses `get_token()` that calls Keycloak directly. Replace with auth via ms-auth gateway endpoint. Also add refresh and logout tests.

- [ ] **Step 1: Overwrite test-all.sh**

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

- [ ] **Step 2: Verify**

```bash
grep -n "auth_login\|auth_refresh\|Blacklisted token\|stale refresh" /tmp/ms-auth-work/ms-platform/test-all.sh
```

Expected: all 4 patterns present.

---

## Task 13: Repackage ZIP and verify generator tests pass

**Files:**
- Overwrite: `src/main/resources/templates/ms-platform-template.zip`

- [ ] **Step 1: Repackage ZIP**

```bash
cd /tmp/ms-auth-work
zip -r /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip ms-platform/
```

- [ ] **Step 2: Verify ZIP contains new files**

```bash
unzip -l /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip | grep "ms-auth"
```

Expected: ms-auth/ entries including AuthApplication.java, AuthController.java, AuthService.java, TokenBlacklistService.java, application.yml, pom.xml, Dockerfile.

```bash
unzip -l /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform-template.zip | grep "TokenBlacklistFilter"
```

Expected: `ms-platform/ms-gateway/src/main/java/.../gateway/filter/TokenBlacklistFilter.java` present.

- [ ] **Step 3: Run generator tests**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn test
```

Expected: BUILD SUCCESS, all existing tests pass (53 tests or more, 0 failures).

- [ ] **Step 4: Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform-template.zip
git commit -m "feat: add ms-auth service + gateway TokenBlacklistFilter to template ZIP"
```

---

## Task 14: FeatureFilterProcessor — exclude ms-auth when keycloak=false (TDD)

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`

Context: `FeatureFilterProcessor` filters files based on feature flags from the request. When `keycloak=false`, the `keycloak/` directory is excluded. `ms-auth` depends on Keycloak (it wraps Keycloak login), so `ms-auth/` should also be excluded when `keycloak=false`.

Current file structure (read `FeatureFilterProcessorTest.java` to find where to add the test):

- [ ] **Step 1: Read the current test file**

```bash
cat src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java
```

Note the pattern for existing exclusion tests. They typically look like:

```java
@Test
void excludes_keycloak_when_disabled() {
    FeatureOptions features = FeatureOptions.builder().keycloak(false).build();
    // ... test setup ...
    List<GeneratedFile> result = processor.process(files, ctx);
    assertThat(result).noneMatch(f -> f.path().contains("/keycloak/"));
}
```

- [ ] **Step 2: Add failing test for ms-auth exclusion**

Open `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java` and add the following test after the existing `excludes_keycloak_when_disabled` test:

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
    assertThat(result).hasSize(2); // gateway filter and service-a remain
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=FeatureFilterProcessorTest#excludes_ms_auth_when_keycloak_disabled -q 2>&1 | tail -20
```

Expected: FAIL — `ms-auth` paths are currently not filtered out.

- [ ] **Step 4: Read FeatureFilterProcessor to find where to add the rule**

```bash
cat src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java
```

Find the `isExcluded` or `shouldExclude` method that handles the `keycloak` feature flag. It currently excludes `keycloak/`. Add `ms-auth/` to the same condition.

- [ ] **Step 5: Add ms-auth exclusion rule**

In `FeatureFilterProcessor.java`, find the block that handles `keycloak=false`. It currently reads something like:

```java
if (!features.isKeycloak()) {
    if (rel.startsWith("keycloak/")) return true;
}
```

Add the `ms-auth/` check to the same condition:

```java
if (!features.isKeycloak()) {
    if (rel.startsWith("keycloak/")) return true;
    if (rel.startsWith("ms-auth/")) return true;
}
```

(Exact location depends on the current file structure — read it in Step 4.)

- [ ] **Step 6: Run test to verify it passes**

```bash
mvn test -pl . -Dtest=FeatureFilterProcessorTest#excludes_ms_auth_when_keycloak_disabled -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, test passes.

- [ ] **Step 7: Run all tests**

```bash
mvn test
```

Expected: BUILD SUCCESS, all tests pass (no regressions).

- [ ] **Step 8: Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java
git add src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java
git commit -m "feat: exclude ms-auth from generated output when keycloak=false"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|---|---|
| POST /auth/login with opaque refresh token | Task 6 (AuthService.login) |
| POST /auth/refresh with token rotation | Task 6 (AuthService.refresh) |
| POST /auth/logout with JTI blacklist + revoke | Task 6 (AuthService.logout) |
| Redis: auth:blacklist:{jti} with TTL | Task 5 (TokenBlacklistService.blacklist) |
| Redis: auth:refresh:{uuid} with TTL | Task 5 (TokenBlacklistService.storeRefreshToken) |
| RedisKeys.authBlacklist + authRefresh | Task 2 |
| Gateway TokenBlacklistFilter reactive | Task 8 |
| Gateway Redis reactive dep + route ms-auth | Task 9 |
| ms-auth security: login+refresh public, logout authenticated | Task 7 (SecurityConfig) |
| Keycloak realm: accessTokenLifespan=300, ssoSessionMaxLifespan=1800, refreshTokenMaxReuse=0 | Task 11 |
| docker-compose ms-auth service | Task 10 |
| root pom.xml ms-auth module | Task 10 |
| .env AUTH_PORT=9200 | Task 10 |
| test-all.sh via ms-auth + refresh test + logout+blacklist test | Task 12 |
| FeatureFilterProcessor: exclude ms-auth when keycloak=false | Task 14 |

All spec requirements are covered.

**Placeholder scan:** No TBDs, no "add error handling later", no incomplete sections.

**Type consistency:** `LoginResponse`, `RefreshRequest`, `LogoutRequest`, `KeycloakTokenResponse` are used consistently across Tasks 4, 6, 7. `TokenBlacklistService` method names (`blacklist`, `storeRefreshToken`, `getRefreshToken`, `deleteRefreshToken`) are called correctly in Task 6. `RedisKeys.authBlacklist(jti)` and `RedisKeys.authRefresh(opaqueToken)` defined in Task 2 and used in Tasks 5, 8.
