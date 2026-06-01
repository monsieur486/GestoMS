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

/**
 * Appelle la Keycloak Admin REST API avec un token admin master (admin-cli, grant password).
 * <p>Copie réduite (token + reset) du KeycloakAdminClient d'admin-application — garder adminToken() synchronisé.
 */
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
