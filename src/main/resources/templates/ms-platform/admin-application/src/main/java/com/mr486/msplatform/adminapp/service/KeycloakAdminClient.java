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
