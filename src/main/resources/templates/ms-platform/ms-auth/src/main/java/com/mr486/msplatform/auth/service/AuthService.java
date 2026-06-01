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

/**
 * Service métier d'authentification : orchestre les appels vers Keycloak
 * (login, refresh, revocation) et gère les refresh tokens opaques stockés dans Redis.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenBlacklistService blacklistService;
    private final RestTemplate restTemplate;
    private final KeycloakAdminClient keycloakAdminClient;

    @Value("${keycloak.internal-url:http://keycloak:8080}")
    private String keycloakInternalUrl;

    @Value("${keycloak.realm:ms-realm}")
    private String realm;

    @Value("${keycloak.client-id:ms-gateway}")
    private String clientId;

    @Value("${keycloak.client-secret:changeit-gateway}")
    private String clientSecret;

    /**
     * Authentifie un utilisateur auprès de Keycloak, génère un refresh token opaque
     * et le stocke dans Redis avant de retourner la réponse au client.
     *
     * @param request les identifiants de connexion
     * @return la réponse contenant l'access token JWT et le refresh token opaque
     */
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

    /**
     * Rafraîchit l'access token en échangeant le refresh token opaque contre
     * un nouveau token Keycloak ; l'ancien token opaque est supprimé atomiquement.
     *
     * @param request le refresh token opaque émis lors de la connexion précédente
     * @return la nouvelle paire access token / refresh token opaque
     */
    public LoginResponse refresh(RefreshRequest request) {
        String kcRefreshToken = blacklistService.getAndDeleteRefreshToken(request.getOpaqueRefreshToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", kcRefreshToken);

        KeycloakTokenResponse kc = callKeycloak(body);

        String newOpaque = UUID.randomUUID().toString();
        blacklistService.storeRefreshToken(newOpaque, kc.getRefreshToken(), kc.getRefreshExpiresIn());
        return new LoginResponse(kc.getAccessToken(), newOpaque, kc.getExpiresIn());
    }

    /**
     * Déconnecte l'utilisateur : blackliste le JTI de l'access token courant
     * et révoque le refresh token Keycloak si un token opaque est fourni.
     *
     * @param request        le refresh token opaque à révoquer (peut être {@code null})
     * @param authentication le JWT de l'utilisateur authentifié
     */
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
