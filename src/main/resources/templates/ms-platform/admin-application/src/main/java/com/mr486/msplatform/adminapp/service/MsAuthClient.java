package com.mr486.msplatform.adminapp.service;

import com.mr486.msplatform.adminapp.dto.MsAuthTokens;
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

    /**
     * Construit le client en injectant le {@link RestTemplate} partagé et l'URL du gateway.
     *
     * @param restTemplate le client HTTP partagé
     * @param gatewayUrl   l'URL du gateway exposant les endpoints ms-auth
     */
    public MsAuthClient(RestTemplate restTemplate, @Value("${gateway.url}") String gatewayUrl) {
        this.restTemplate = restTemplate;
        this.gatewayUrl = gatewayUrl;
    }

    /**
     * Authentifie un utilisateur auprès de ms-auth et retourne ses tokens.
     *
     * @param username le nom d'utilisateur
     * @param password le mot de passe
     * @return les tokens d'authentification ({@link MsAuthTokens})
     * @throws InvalidCredentialsException  si les identifiants sont invalides (401/400)
     * @throws AuthUnavailableException     si ms-auth est inaccessible
     */
    public MsAuthTokens login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        try {
            ResponseEntity<MsAuthTokens> resp =
                    restTemplate.postForEntity(gatewayUrl + "/auth/login", entity, MsAuthTokens.class);
            MsAuthTokens body = resp.getBody();
            if (body == null) {
                throw new AuthUnavailableException();
            }
            return body;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new InvalidCredentialsException();
            }
            throw new AuthUnavailableException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AuthUnavailableException();
        }
    }

    /**
     * Échange un refresh token opaque contre de nouveaux tokens.
     *
     * @param opaqueRefreshToken le refresh token opaque (peut être {@code null})
     * @return les nouveaux tokens d'authentification
     * @throws AuthUnavailableException si ms-auth est inaccessible ou retourne un corps vide
     */
    public MsAuthTokens refresh(String opaqueRefreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("opaque_refresh_token", opaqueRefreshToken == null ? "" : opaqueRefreshToken), headers);
        ResponseEntity<MsAuthTokens> resp =
                restTemplate.postForEntity(gatewayUrl + "/auth/refresh", entity, MsAuthTokens.class);
        MsAuthTokens body = resp.getBody();
        if (body == null) {
            throw new AuthUnavailableException();
        }
        return body;
    }

    /**
     * Notifie ms-auth de la déconnexion pour révoquer les tokens côté serveur.
     * Les erreurs réseau sont silencieusement ignorées pour garantir la déconnexion locale.
     *
     * @param accessToken        le token d'accès JWT courant
     * @param opaqueRefreshToken le refresh token opaque à révoquer (peut être {@code null})
     */
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

    /** Exception levée lorsque les identifiants fournis sont rejetés par ms-auth (401/400). */
    public static class InvalidCredentialsException extends RuntimeException {}

    /** Exception levée lorsque ms-auth est inaccessible ou retourne une erreur serveur. */
    public static class AuthUnavailableException extends RuntimeException {}
}
