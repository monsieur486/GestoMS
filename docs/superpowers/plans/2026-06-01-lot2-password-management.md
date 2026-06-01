# Lot 2 — Changement de mot de passe (self + admin) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre à un utilisateur de changer son propre mot de passe depuis « Mon compte » de `ms-webui` (ancien + nouveau + retape), et ajouter la confirmation à 2 champs sur le reset admin déjà existant d'`admin-application`.

**Architecture :** Générateur Spring Boot streamant une plateforme. La logique self-service vit dans `ms-auth` (nouvel endpoint `POST /auth/account/password`) qui vérifie l'ancien mot de passe via un *password grant* puis pose le nouveau via l'API Admin Keycloak avec les **creds admin master** (réutilisées d'`admin-application`, variables `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`). `ms-webui` appelle cet endpoint via le gateway avec le Bearer de session. Côté admin, `admin-application` possède déjà liste/édition/reset des users via `KeycloakAdminClient` ; on n'ajoute qu'un 2ᵉ champ de confirmation.

**Tech Stack :** Java 17, Spring Boot (Web, Security, OAuth2 Resource Server), Thymeleaf, Lombok, JUnit 5 + AssertJ + Mockito.

**Spec :** `docs/superpowers/specs/2026-06-01-webui-rename-and-password-management-design.md` (section Lot 2).

**Branche :** ce plan s'exécute sur `lot2-password-management`, basée sur `lot1-rename-ms-client-to-ms-webui` (il faut la structure `ms-webui`). Tant que la PR Lot 1 n'est pas mergée, une PR Lot 2 → `master` apparaîtra empilée sur les commits Lot 1 (normal ; se résorbe au merge de Lot 1).

---

## Rappels d'environnement (pièges)

- **Le code Java des templates n'est PAS compilé par `mvn test`** (ce sont des ressources sous `src/main/resources/templates/`). La sécurité au niveau générateur = `mvn test` (guards `GeneratedOutputLayoutTest`/`GeneratorSourceLayoutTest` + chargement des templates). La vraie validation = générer la plateforme puis compiler les modules touchés (Task 4).
- **Layout imposé sur le Java généré** : ≤120 colonnes, 4 espaces, un import par ligne, javadoc français. Respecter sinon `GeneratedOutputLayoutTest` casse.
- **Piège 401 vs erreur métier** : « ancien mot de passe faux » renvoie **422** (pas 401), et `ms-webui` n'utilise **pas** `GatewayClient` pour cet appel (le 401 y déclenche refresh + déconnexion).
- **Zombie :8080** : tuer les vieux `java -jar` avant de vérifier (`pkill -f springboot-platform-generator` ou `--server.port=8077`).

---

## File Structure

**Partie A — backend (`ms-auth`)** sous `src/main/resources/templates/ms-platform/ms-auth/` :
- Create `…/auth/dto/ChangePasswordRequest.java` — DTO `{oldPassword,newPassword}`.
- Create `…/auth/service/KeycloakAdminClient.java` — `adminToken()` + `resetPassword(id,pwd)` (copie réduite d'admin-application).
- Modify `…/auth/service/AuthService.java` — `changeOwnPassword(jwt, old, new)`.
- Modify `…/auth/controller/AuthController.java` — `POST /auth/account/password`.
- Modify `…/auth/src/main/resources/application.yml` — props `keycloak.admin-username/password`.
- Create `…/auth/src/test/java/.../service/AuthServiceChangePasswordTest.java`.

**Partie A — frontend (`ms-webui`)** sous `…/ms-platform/ms-webui/` :
- Modify `…/webui/service/MsAuthClient.java` — `changePassword(accessToken, old, new)` + exceptions.
- Modify `…/webui/web/AccountController.java` — `POST /account/password`.
- Modify `…/webui/src/main/resources/templates/account.html` — formulaire 3 champs + alertes + JS.
- Create `…/webui/src/test/java/.../web/AccountControllerTest.java`.

**Partie B (`admin-application`)** sous `…/ms-platform/admin-application/` :
- Modify `…/adminapp/web/UsersController.java` — `resetPassword(id, password, confirm)` + garde match.
- Modify `…/admin-application/src/main/resources/templates/edit.html` — 2ᵉ champ + alerte + JS.
- Create `…/admin-application/src/test/java/.../web/UsersControllerTest.java`.

---

## Task 1 : `ms-auth` — endpoint self password-change

**Files:**
- Create: `src/main/resources/templates/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/dto/ChangePasswordRequest.java`
- Create: `src/main/resources/templates/ms-platform/ms-auth/src/main/java/com/mr486/msplatform/auth/service/KeycloakAdminClient.java`
- Modify: `…/ms-auth/src/main/java/com/mr486/msplatform/auth/service/AuthService.java`
- Modify: `…/ms-auth/src/main/java/com/mr486/msplatform/auth/controller/AuthController.java`
- Modify: `…/ms-auth/src/main/resources/application.yml`
- Test: `…/ms-auth/src/test/java/com/mr486/msplatform/auth/service/AuthServiceChangePasswordTest.java`

- [ ] **Step 1 : Écrire le test d'abord (TDD) — `AuthServiceChangePasswordTest.java`**

```java
package com.mr486.msplatform.auth.service;

import com.mr486.msplatform.auth.dto.KeycloakTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AuthService#changeOwnPassword} : vérifie que l'ancien mot
 * de passe est contrôlé via un password grant avant de poser le nouveau, et qu'un
 * ancien mot de passe invalide renvoie 422 sans appeler le reset.
 */
class AuthServiceChangePasswordTest {

    private RestTemplate restTemplate;
    private TokenBlacklistService blacklist;
    private KeycloakAdminClient adminClient;
    private AuthService service;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        blacklist = Mockito.mock(TokenBlacklistService.class);
        adminClient = Mockito.mock(KeycloakAdminClient.class);
        service = new AuthService(blacklist, restTemplate, adminClient);
    }

    private JwtAuthenticationToken jwt(String username, String sub) {
        Jwt token = Mockito.mock(Jwt.class);
        when(token.getClaimAsString("preferred_username")).thenReturn(username);
        when(token.getSubject()).thenReturn(sub);
        JwtAuthenticationToken auth = Mockito.mock(JwtAuthenticationToken.class);
        when(auth.getToken()).thenReturn(token);
        return auth;
    }

    @Test
    void resets_password_when_old_password_is_valid() {
        KeycloakTokenResponse ok = Mockito.mock(KeycloakTokenResponse.class);
        when(ok.getAccessToken()).thenReturn("valid");
        when(restTemplate.postForEntity(anyString(), any(), eq(KeycloakTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(ok));

        service.changeOwnPassword(jwt("alice", "uid-1"), "old", "new");

        verify(adminClient).resetPassword("uid-1", "new");
    }

    @Test
    void returns_422_and_skips_reset_when_old_password_is_wrong() {
        when(restTemplate.postForEntity(anyString(), any(), eq(KeycloakTokenResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.changeOwnPassword(jwt("alice", "uid-1"), "bad", "new"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(adminClient, never()).resetPassword(anyString(), anyString());
    }
}
```

- [ ] **Step 2 : Créer le DTO `ChangePasswordRequest.java`**

```java
package com.mr486.msplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO de requête de changement de mot de passe self-service : ancien mot de passe
 * (pour vérification) et nouveau mot de passe à poser.
 */
@Getter @NoArgsConstructor @AllArgsConstructor
public class ChangePasswordRequest {
    @NotBlank private String oldPassword;
    @NotBlank private String newPassword;
}
```

- [ ] **Step 3 : Créer `KeycloakAdminClient.java` (token admin master + reset)**

```java
package com.mr486.msplatform.auth.service;

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

import java.util.Map;

/** Appelle la Keycloak Admin REST API avec un token admin master (admin-cli, grant password). */
@Service
public class KeycloakAdminClient {

    private final RestTemplate restTemplate;
    private final String internalUrl;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    /**
     * Construit le client en injectant les paramètres de connexion Keycloak.
     *
     * @param restTemplate  le client HTTP partagé
     * @param internalUrl   l'URL interne de Keycloak (ex. {@code http://keycloak:8080})
     * @param realm         le nom du realm applicatif
     * @param adminUsername le nom d'utilisateur de l'admin master
     * @param adminPassword le mot de passe de l'admin master
     */
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

    /**
     * Réinitialise le mot de passe d'un utilisateur (credential permanent, non temporaire).
     *
     * @param id       l'identifiant unique de l'utilisateur dans Keycloak
     * @param password le nouveau mot de passe
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /** Exception levée lorsque Keycloak est inaccessible ou retourne une erreur inattendue. */
    public static class KeycloakUnavailableException extends RuntimeException {}
}
```

- [ ] **Step 4 : Modifier `AuthService.java` — dépendance + méthode**

Ajouter le champ injecté (l'ordre des champs `final` définit le constructeur Lombok `@RequiredArgsConstructor` ; ajouter APRÈS `restTemplate` pour que le constructeur soit `(blacklistService, restTemplate, keycloakAdminClient)`) :

```java
    private final TokenBlacklistService blacklistService;
    private final RestTemplate restTemplate;
    private final KeycloakAdminClient keycloakAdminClient;
```

Ajouter les imports nécessaires en tête (à côté des imports existants) :
```java
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
```
(`JwtAuthenticationToken`, `ResponseStatusException` et `HttpStatus` sont déjà importés dans ce fichier — ne pas dupliquer ; vérifier avant d'ajouter.)

Ajouter la méthode (le corps utilise le `callKeycloak(MultiValueMap)` privé existant pour la vérification) :

```java
    /**
     * Change le mot de passe de l'utilisateur courant : vérifie l'ancien mot de passe via
     * un password grant, puis pose le nouveau via l'API Admin Keycloak.
     *
     * @param authentication le JWT de l'utilisateur authentifié (fournit username et sub)
     * @param oldPassword    l'ancien mot de passe à vérifier
     * @param newPassword    le nouveau mot de passe à poser (permanent)
     * @throws ResponseStatusException 422 si l'ancien mot de passe est invalide
     */
    public void changeOwnPassword(JwtAuthenticationToken authentication,
                                  String oldPassword, String newPassword) {
        String username = authentication.getToken().getClaimAsString("preferred_username");
        String userId = authentication.getToken().getSubject();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", username);
        body.add("password", oldPassword);
        try {
            callKeycloak(body);
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ancien mot de passe incorrect");
        }

        keycloakAdminClient.resetPassword(userId, newPassword);
    }
```

- [ ] **Step 5 : Modifier `AuthController.java` — endpoint**

Ajouter la méthode (les imports `JwtAuthenticationToken`, `HttpStatus`, `ResponseStatusException`, `@RequestBody`, `@Valid` existent déjà) :

```java
    /**
     * Change le mot de passe de l'utilisateur authentifié après vérification de l'ancien.
     *
     * @param request        l'ancien et le nouveau mot de passe
     * @param authentication l'authentification JWT de la requête courante
     */
    @PostMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        authService.changeOwnPassword(jwt, request.getOldPassword(), request.getNewPassword());
    }
```

(`import com.mr486.msplatform.auth.dto.*;` couvre déjà `ChangePasswordRequest` — le contrôleur importe le package dto en `*`.)

- [ ] **Step 6 : Modifier `application.yml` de `ms-auth` — props admin master**

Sous la clé `keycloak:` (qui contient déjà `internal-url`, `realm`, `client-id`, `client-secret`), ajouter :

```yaml
  admin-username: ${KEYCLOAK_ADMIN:admin}
  admin-password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
```

(Pas de modification de `docker-compose.yml` : `ms-auth` a déjà `env_file: [.env]` et les défauts `admin`/`admin` correspondent à `KC_BOOTSTRAP_ADMIN_*` de Keycloak, exactement comme `admin-application`.)

- [ ] **Step 7 : Vérifier le layout + chargement des templates**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected : BUILD SUCCESS, 124 tests (les guards de layout chargent les nouveaux fichiers `ms-auth` ; aucune régression).

- [ ] **Step 8 : Compiler le module `ms-auth` généré (best-effort) pour valider le Java template**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
pkill -f springboot-platform-generator 2>/dev/null; true
mvn -q clean package -DskipTests >/tmp/b.log 2>&1 && java -jar target/*.jar --server.port=8077 >/tmp/s.log 2>&1 &
sleep 12
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/lot2.zip
rm -rf /tmp/lot2 && unzip -q /tmp/lot2.zip -d /tmp/lot2
( cd /tmp/lot2/ms-platform && mvn -q -pl ms-auth -am -o compile 2>&1 | tail -8 ) \
  || echo "compile hors-ligne indisponible — vérifier la présence des nouveaux fichiers à la place"
grep -rl "changeOwnPassword\|/account/password\|ChangePasswordRequest" /tmp/lot2/ms-platform/ms-auth
pkill -f "server.port=8077" 2>/dev/null; true
```
Expected : compilation OK (ou repli) ; les 3 symboles présents dans le `ms-auth` généré.

- [ ] **Step 9 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-auth
git commit -m "$(cat <<'EOF'
feat(ms-auth): self password-change endpoint POST /auth/account/password

Verifies old password via password grant, sets new via Keycloak Admin
(master creds, temporary:false). Wrong old password → 422 (not 401, to
avoid the gateway refresh/logout path). Unit-tested with mocked deps.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 : `ms-webui` — formulaire « Mon compte »

**Files:**
- Modify: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/service/MsAuthClient.java`
- Modify: `…/ms-webui/src/main/java/com/mr486/msplatform/webui/web/AccountController.java`
- Modify: `…/ms-webui/src/main/resources/templates/account.html`
- Test: `…/ms-webui/src/test/java/com/mr486/msplatform/webui/web/AccountControllerTest.java`

- [ ] **Step 1 : Écrire le test d'abord (TDD) — `AccountControllerTest.java`**

```java
package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.config.WebUiProperties;
import com.mr486.msplatform.webui.security.SessionKeys;
import com.mr486.msplatform.webui.service.MsAuthClient;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AccountController#changePassword} : non-correspondance
 * rejetée sans appel backend, succès et ancien mot de passe invalide.
 */
class AccountControllerTest {

    private WebUiProperties props;
    private MsAuthClient msAuthClient;
    private AccountController controller;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        props = Mockito.mock(WebUiProperties.class);
        msAuthClient = Mockito.mock(MsAuthClient.class);
        controller = new AccountController(props, msAuthClient);
        session = Mockito.mock(HttpSession.class);
        when(session.getAttribute(SessionKeys.ACCESS_TOKEN)).thenReturn("at");
        when(session.getAttribute(SessionKeys.REFRESH_TOKEN)).thenReturn("rt");
    }

    @Test
    void rejects_mismatch_without_calling_backend() {
        String view = controller.changePassword("old", "new", "different", session);
        assertThat(view).isEqualTo("redirect:/account?mismatch");
        verify(msAuthClient, never()).changePassword(anyString(), anyString(), anyString());
    }

    @Test
    void redirects_ok_on_success() {
        String view = controller.changePassword("old", "new", "new", session);
        assertThat(view).isEqualTo("redirect:/account?ok");
        verify(msAuthClient).changePassword("at", "old", "new");
    }

    @Test
    void redirects_wrong_when_old_password_invalid() {
        Mockito.doThrow(new MsAuthClient.WrongOldPasswordException())
                .when(msAuthClient).changePassword(any(), any(), any());
        String view = controller.changePassword("bad", "new", "new", session);
        assertThat(view).isEqualTo("redirect:/account?wrong");
    }
}
```

- [ ] **Step 2 : Ajouter `changePassword` à `MsAuthClient.java`**

Ajouter l'import `org.springframework.web.client.HttpClientErrorException` (déjà présent) et la méthode + exceptions :

```java
    /**
     * Change le mot de passe de l'utilisateur via ms-auth (endpoint authentifié).
     *
     * @param accessToken l'access token JWT de session
     * @param oldPassword l'ancien mot de passe
     * @param newPassword le nouveau mot de passe
     * @throws MsAuthClient.WrongOldPasswordException si ms-auth renvoie 422 (ancien mot de passe faux)
     * @throws MsAuthClient.TokenExpiredException     si ms-auth renvoie 401 (access token expiré)
     * @throws MsAuthClient.AuthUnavailableException  si ms-auth est injoignable ou en erreur
     */
    public void changePassword(String accessToken, String oldPassword, String newPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken == null ? "" : accessToken);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("oldPassword", oldPassword, "newPassword", newPassword), headers);
        try {
            restTemplate.postForEntity(gatewayUrl + "/auth/account/password", entity, Void.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new WrongOldPasswordException();
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new TokenExpiredException();
            }
            throw new AuthUnavailableException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AuthUnavailableException();
        }
    }
```

Ajouter les classes d'exception à côté des existantes (`InvalidCredentialsException`, `AuthUnavailableException`) :
```java
    /** Levée lorsque ms-auth renvoie 422 : l'ancien mot de passe fourni est incorrect. */
    public static class WrongOldPasswordException extends RuntimeException {}

    /** Levée lorsque ms-auth renvoie 401 : l'access token de session est expiré. */
    public static class TokenExpiredException extends RuntimeException {}
```

- [ ] **Step 3 : Modifier `AccountController.java`**

Le contrôleur dépend aujourd'hui de `WebUiProperties` (anciennement `ClientProperties`). Ajouter `MsAuthClient`, le constructeur à 2 args, et le POST. Remplacer le constructeur existant et ajouter les imports `HttpSession`, `MsAuthClient`, `SessionKeys`, `MsAuthTokens`, `@PostMapping`, `@RequestParam` :

```java
    private final WebUiProperties webUiProperties;
    private final MsAuthClient msAuthClient;

    public AccountController(WebUiProperties webUiProperties, MsAuthClient msAuthClient) {
        this.webUiProperties = webUiProperties;
        this.msAuthClient = msAuthClient;
    }
```

Ajouter la méthode POST (PRG : redirige vers `/account?…` ; le GET existant affiche les alertes via `param`) :

```java
    /**
     * Change le mot de passe de l'utilisateur courant après validation de la correspondance.
     *
     * @param oldPassword l'ancien mot de passe
     * @param newPassword le nouveau mot de passe
     * @param confirm     la confirmation du nouveau mot de passe (doit correspondre)
     * @param session     la session HTTP (porte l'access et le refresh token)
     * @return une redirection vers {@code /account} avec le statut de l'opération
     */
    @PostMapping("/account/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirm,
                                 HttpSession session) {
        if (!newPassword.equals(confirm)) {
            return "redirect:/account?mismatch";
        }
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        try {
            try {
                msAuthClient.changePassword(accessToken, oldPassword, newPassword);
            } catch (MsAuthClient.TokenExpiredException expired) {
                String refreshToken = (String) session.getAttribute(SessionKeys.REFRESH_TOKEN);
                MsAuthTokens fresh = msAuthClient.refresh(refreshToken);
                session.setAttribute(SessionKeys.ACCESS_TOKEN, fresh.accessToken());
                session.setAttribute(SessionKeys.REFRESH_TOKEN, fresh.opaqueRefreshToken());
                msAuthClient.changePassword(fresh.accessToken(), oldPassword, newPassword);
            }
            return "redirect:/account?ok";
        } catch (MsAuthClient.WrongOldPasswordException e) {
            return "redirect:/account?wrong";
        } catch (RuntimeException e) {
            return "redirect:/account?error";
        }
    }
```

⚠️ Mettre à jour la méthode GET existante : remplacer la référence au champ `clientProperties` par `webUiProperties` (le champ a été renommé). La ligne `model.addAttribute("keycloakAccountUrl", clientProperties.keycloakAccountUrl());` devient `... webUiProperties.keycloakAccountUrl());`.

- [ ] **Step 4 : Remplacer le bloc Keycloak de `account.html` par le formulaire**

Remplacer le bloc actuel (le `<p>` « Pour changer votre mot de passe… » + le `<a>` vers Keycloak) par :

```html
          <div th:if="${param.ok}" class="alert alert-success">Mot de passe modifié.</div>
          <div th:if="${param.mismatch}" class="alert alert-danger">Les mots de passe ne correspondent pas.</div>
          <div th:if="${param.wrong}" class="alert alert-danger">Ancien mot de passe incorrect.</div>
          <div th:if="${param.error}" class="alert alert-danger">Erreur, réessayez plus tard.</div>

          <h6 class="fw-bold mb-3">Changer mon mot de passe</h6>
          <form th:action="@{/account/password}" method="post" id="pwdForm" class="row g-3">
            <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
            <div class="col-12">
              <label class="form-label">Ancien mot de passe</label>
              <input class="form-control" type="password" name="oldPassword" required/>
            </div>
            <div class="col-12">
              <label class="form-label">Nouveau mot de passe</label>
              <input class="form-control" type="password" name="newPassword" id="newPassword" required/>
            </div>
            <div class="col-12">
              <label class="form-label">Retaper le nouveau mot de passe</label>
              <input class="form-control" type="password" name="confirm" id="confirm" required/>
            </div>
            <div class="col-12">
              <button type="submit" class="btn btn-primary w-100">Changer le mot de passe</button>
            </div>
          </form>
          <script>
            document.getElementById('pwdForm').addEventListener('submit', function (e) {
              if (document.getElementById('newPassword').value !== document.getElementById('confirm').value) {
                e.preventDefault();
                alert('Les mots de passe ne correspondent pas.');
              }
            });
          </script>
```

(Le bloc avatar/username/roles au-dessus reste inchangé. Conserver le `<script>` Bootstrap en bas de page.)

- [ ] **Step 5 : Layout + chargement**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected : BUILD SUCCESS, 124 tests.

- [ ] **Step 6 : Compiler le module `ms-webui` généré (best-effort)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
pkill -f springboot-platform-generator 2>/dev/null; true
mvn -q clean package -DskipTests >/tmp/b.log 2>&1 && java -jar target/*.jar --server.port=8077 >/tmp/s.log 2>&1 &
sleep 12
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/lot2.zip
rm -rf /tmp/lot2 && unzip -q /tmp/lot2.zip -d /tmp/lot2
( cd /tmp/lot2/ms-platform && mvn -q -pl ms-webui -am -o compile 2>&1 | tail -8 ) \
  || echo "compile hors-ligne indisponible — vérifier la présence du formulaire à la place"
grep -c "account/password" /tmp/lot2/ms-platform/ms-webui/src/main/resources/templates/account.html
pkill -f "server.port=8077" 2>/dev/null; true
```
Expected : compilation OK (ou repli) ; `account/password` présent dans `account.html`.

- [ ] **Step 7 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/ms-webui
git commit -m "$(cat <<'EOF'
feat(ms-webui): self password-change form on /account

3-field form (old/new/retype) posting to ms-auth via gateway with the
session bearer; client + server match check; 422→wrong, 401→refresh+
retry, success→green. Replaces the Keycloak link. Unit-tested.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 : `admin-application` — confirmation à 2 champs sur le reset

**Files:**
- Modify: `…/admin-application/src/main/java/com/mr486/msplatform/adminapp/web/UsersController.java`
- Modify: `…/admin-application/src/main/resources/templates/edit.html`
- Test: `…/admin-application/src/test/java/com/mr486/msplatform/adminapp/web/UsersControllerTest.java`

- [ ] **Step 1 : Écrire le test d'abord (TDD) — `UsersControllerTest.java`**

```java
package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires de {@link UsersController#resetPassword} : la réinitialisation n'est
 * effectuée que si le mot de passe et sa confirmation correspondent.
 */
class UsersControllerTest {

    private KeycloakAdminClient adminClient;
    private UsersController controller;

    @BeforeEach
    void setUp() {
        adminClient = Mockito.mock(KeycloakAdminClient.class);
        controller = new UsersController(adminClient);
    }

    @Test
    void resets_when_passwords_match() {
        String view = controller.resetPassword("id1", "secret", "secret");
        assertThat(view).isEqualTo("redirect:/users/id1/edit?pwd");
        verify(adminClient).resetPassword("id1", "secret");
    }

    @Test
    void rejects_when_passwords_differ() {
        String view = controller.resetPassword("id1", "secret", "other");
        assertThat(view).isEqualTo("redirect:/users/id1/edit?mismatch");
        verify(adminClient, never()).resetPassword(anyString(), anyString());
    }
}
```

- [ ] **Step 2 : Modifier `UsersController.resetPassword`**

Remplacer la méthode existante (ajouter le paramètre `confirm` et la garde de correspondance) :

```java
    /**
     * Réinitialise le mot de passe d'un utilisateur Keycloak après contrôle de la confirmation.
     *
     * @param id       l'identifiant Keycloak de l'utilisateur
     * @param password le nouveau mot de passe
     * @param confirm  la confirmation du mot de passe (doit correspondre)
     * @return une redirection vers le formulaire d'édition avec le statut de l'opération
     */
    @PostMapping("/users/{id}/password")
    public String resetPassword(@PathVariable String id,
                                @RequestParam String password,
                                @RequestParam String confirm) {
        if (!password.equals(confirm)) {
            return "redirect:/users/" + id + "/edit?mismatch";
        }
        try {
            keycloakAdminClient.resetPassword(id, password);
            return "redirect:/users/" + id + "/edit?pwd";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/edit?error";
        }
    }
```

- [ ] **Step 3 : Modifier `edit.html` — 2ᵉ champ + alerte + JS**

Ajouter l'alerte mismatch à côté des alertes existantes (après la ligne `<div th:if="${param.pwd}" ...>`) :
```html
  <div th:if="${param.mismatch}" class="alert alert-danger mb-3">Les mots de passe ne correspondent pas.</div>
```

Dans la carte « Réinitialiser le mot de passe », remplacer le `<form>` actuel par :
```html
          <form th:action="@{/users/{id}/password(id=${user.id})}" method="post" id="pwdForm" class="row g-3">
            <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
            <div class="col-12">
              <label class="form-label">Nouveau mot de passe</label>
              <input class="form-control" type="password" name="password" id="password" required/>
            </div>
            <div class="col-12">
              <label class="form-label">Retaper le nouveau mot de passe</label>
              <input class="form-control" type="password" name="confirm" id="confirm" required/>
            </div>
            <div class="col-12">
              <button type="submit" class="btn btn-outline-warning">Réinitialiser</button>
            </div>
          </form>
          <script>
            document.getElementById('pwdForm').addEventListener('submit', function (e) {
              if (document.getElementById('password').value !== document.getElementById('confirm').value) {
                e.preventDefault();
                alert('Les mots de passe ne correspondent pas.');
              }
            });
          </script>
```

- [ ] **Step 4 : Layout + chargement**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected : BUILD SUCCESS, 124 tests.

- [ ] **Step 5 : Compiler `admin-application` généré (best-effort)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
pkill -f springboot-platform-generator 2>/dev/null; true
mvn -q clean package -DskipTests >/tmp/b.log 2>&1 && java -jar target/*.jar --server.port=8077 >/tmp/s.log 2>&1 &
sleep 12
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/lot2.zip
rm -rf /tmp/lot2 && unzip -q /tmp/lot2.zip -d /tmp/lot2
( cd /tmp/lot2/ms-platform && mvn -q -pl admin-application -am -o compile 2>&1 | tail -8 ) \
  || echo "compile hors-ligne indisponible — vérifier la présence du 2e champ à la place"
grep -c 'name="confirm"' /tmp/lot2/ms-platform/admin-application/src/main/resources/templates/edit.html
pkill -f "server.port=8077" 2>/dev/null; true
```
Expected : compilation OK (ou repli) ; `name="confirm"` présent dans `edit.html`.

- [ ] **Step 6 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/admin-application
git commit -m "$(cat <<'EOF'
feat(admin-application): require password confirmation on admin reset

edit.html reset card gains a "retype" field; UsersController.resetPassword
rejects mismatched entries (redirect ?mismatch) before calling Keycloak.
Unit-tested.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 : Vérification de bout en bout

Objectif : prouver que la plateforme générée compile et que les deux flux fonctionnent.

- [ ] **Step 1 : Générer + compiler toute la plateforme (best-effort selon dispo réseau)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
pkill -f springboot-platform-generator 2>/dev/null; true
mvn -q clean package -DskipTests >/tmp/b.log 2>&1 && java -jar target/*.jar --server.port=8077 >/tmp/s.log 2>&1 &
sleep 12
curl -s -X POST http://localhost:8077/api/generate/platform -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/lot2.zip
rm -rf /tmp/lot2 && unzip -q /tmp/lot2.zip -d /tmp/lot2
( cd /tmp/lot2/ms-platform && mvn -q -pl ms-auth,ms-webui,admin-application -am compile 2>&1 | tail -10 ) \
  || echo "compilation indisponible hors-ligne — passer à la vérification manuelle Docker"
pkill -f "server.port=8077" 2>/dev/null; true
```
Expected : compilation des 3 modules OK (en ligne). Sinon, repli sur la vérif Docker (Step 3, manuelle).

- [ ] **Step 2 : Vérifier la présence/cohérence des artefacts clés**

```bash
cd /tmp/lot2/ms-platform
echo "--- ms-auth endpoint + admin props ---"
grep -rl "/account/password" ms-auth/src/main/java && grep -n "admin-username\|admin-password" ms-auth/src/main/resources/application.yml
echo "--- ms-webui form ---"
grep -n "account/password\|name=\"confirm\"" ms-webui/src/main/resources/templates/account.html
echo "--- admin-application confirm ---"
grep -n 'name="confirm"\|param.mismatch' admin-application/src/main/resources/templates/edit.html
```
Expected : endpoint + props présents ; `confirm` présent dans les deux templates.

- [ ] **Step 3 : (Manuel, nécessite Docker) Vérification fonctionnelle via la stack**

> Documenté pour l'utilisateur — non exécutable sans Docker dans cet environnement.
> ```bash
> cd /tmp/lot2/ms-platform && ./clean-docker.sh && docker compose up -d --build
> ```
> - **Self** : se connecter à `ms-webui` (`:8090`) avec un compte de test, aller sur `/account`,
>   changer le mot de passe (ancien correct + nouveau + retape) → alerte verte ; se reconnecter
>   avec le nouveau. Tester ancien faux → alerte rouge ; retape différente → bloqué (rouge).
> - **Admin** : se connecter à `admin-application` (`:9300`) en `test-admin`, `/users` → éditer un
>   user → carte reset avec 2 champs correspondants → vert (`?pwd`) ; différents → rouge (`?mismatch`).

---

## Self-Review (à exécuter après rédaction — déjà fait)

- **Couverture spec** : Partie A backend (Task 1), Partie A frontend (Task 2), Partie B (Task 3), vérif (Task 4). ✅
- **Pas de placeholder** : tout le code (DTO, client, service, contrôleurs, templates, tests) est fourni intégralement.
- **Cohérence des types** : `changeOwnPassword(JwtAuthenticationToken, String, String)`, `KeycloakAdminClient.resetPassword(String,String)`, `MsAuthClient.changePassword(String,String,String)` + `WrongOldPasswordException`/`TokenExpiredException`, `UsersController.resetPassword(String,String,String)` — signatures identiques entre tâches et tests.
- **Pièges couverts** : 422 vs 401 (Task 1 + Task 2), pas de `GatewayClient` pour le change-password, layout ≤120, template non compilé → compile best-effort + grep.

## Notes pour l'exécutant

- Réutiliser exactement le pattern `adminToken()` d'`admin-application` (grant `admin-cli` sur le realm `master`) — c'est éprouvé.
- Ne PAS introduire de rôle `realm-management` ni toucher au realm JSON : on utilise l'admin master.
- `AccountController` : bien renommer la référence résiduelle `clientProperties`→`webUiProperties` dans la méthode GET existante.
- Les `@Value` de `KeycloakAdminClient`/`AuthService` sont nuls en test unitaire — c'est sans effet car `RestTemplate` est mocké avec des matchers `any()`.
