package com.mr486.msplatform.webui.service;

import com.mr486.msplatform.webui.dto.MsAuthTokens;
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

    /**
     * Authentifie l'utilisateur auprès de ms-auth et retourne les tokens.
     *
     * @param username le nom d'utilisateur
     * @param password le mot de passe
     * @return les tokens d'accès et de rafraîchissement
     * @throws MsAuthClient.InvalidCredentialsException si les identifiants sont invalides (401/400)
     * @throws MsAuthClient.AuthUnavailableException    si ms-auth est injoignable ou retourne une erreur serveur
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
     * Effectue la rotation du refresh token et retourne une nouvelle paire de tokens.
     *
     * @param opaqueRefreshToken le refresh token opaque courant (peut être {@code null})
     * @return les nouveaux tokens d'accès et de rafraîchissement
     * @throws MsAuthClient.AuthUnavailableException si ms-auth est injoignable ou retourne un corps vide
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
     * Notifie ms-auth de la déconnexion de l'utilisateur (best-effort : les erreurs sont ignorées).
     *
     * @param accessToken        l'access token JWT à invalider
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

    /** Levée lorsque les identifiants fournis sont invalides (401 ou 400 retourné par ms-auth). */
    public static class InvalidCredentialsException extends RuntimeException {}

    /** Levée lorsque ms-auth est injoignable ou retourne une erreur serveur. */
    public static class AuthUnavailableException extends RuntimeException {}

    /** Levée lorsque ms-auth renvoie 422 : l'ancien mot de passe fourni est incorrect. */
    public static class WrongOldPasswordException extends RuntimeException {}

    /** Levée lorsque ms-auth renvoie 401 : l'access token de session est expiré. */
    public static class TokenExpiredException extends RuntimeException {}
}
