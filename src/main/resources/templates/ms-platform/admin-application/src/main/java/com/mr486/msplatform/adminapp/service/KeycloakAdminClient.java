package com.mr486.msplatform.adminapp.service;

import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import com.mr486.msplatform.adminapp.dto.KeycloakRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
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

    public List<KeycloakUser> listUsers(String search, int first, int max) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(internalUrl + "/admin/realms/" + realm + "/users")
                .queryParam("first", first)
                .queryParam("max", max);
        if (search != null && !search.isBlank()) {
            builder.queryParam("search", search);
        }
        String url = builder.encode().toUriString();
        try {
            ResponseEntity<KeycloakUser[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), KeycloakUser[].class);
            KeycloakUser[] body = resp.getBody();
            return body == null ? List.of() : List.of(body);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public int countUsers(String search) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(internalUrl + "/admin/realms/" + realm + "/users/count");
        if (search != null && !search.isBlank()) {
            builder.queryParam("search", search);
        }
        String url = builder.encode().toUriString();
        try {
            ResponseEntity<Integer> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Integer.class);
            Integer body = resp.getBody();
            return body == null ? 0 : body;
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void createUser(String username, String email, String firstName, String lastName, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", false);
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("enabled", true);
        body.put("credentials", List.of(credential));
        try {
            restTemplate.postForEntity(internalUrl + "/admin/realms/" + realm + "/users",
                    new HttpEntity<>(body, headers), Void.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new UserConflictException();
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void deleteUser(String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            restTemplate.exchange(internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    @SuppressWarnings("rawtypes")
    public void updateUser(String id, String email, String firstName, String lastName, boolean enabled) {
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(adminToken());
        try {
            ResponseEntity<Map> getResp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.GET, new HttpEntity<>(getHeaders), Map.class);
            Map body = getResp.getBody();
            if (body == null) {
                throw new KeycloakUnavailableException();
            }
            body.put("email", email);
            body.put("firstName", firstName);
            body.put("lastName", lastName);
            body.put("enabled", enabled);
            HttpHeaders putHeaders = new HttpHeaders();
            putHeaders.setContentType(MediaType.APPLICATION_JSON);
            putHeaders.setBearerAuth(adminToken());
            restTemplate.exchange(internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.PUT, new HttpEntity<>(body, putHeaders), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

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

    public KeycloakUser getUser(String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakUser> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + id,
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakUser.class);
            return resp.getBody();
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public List<KeycloakRole> listRealmRoles() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakRole[]> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/roles",
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole[].class);
            KeycloakRole[] body = resp.getBody();
            if (body == null) return List.of();
            List<KeycloakRole> result = new ArrayList<>();
            for (KeycloakRole r : body) {
                String n = r.name();
                if (n == null || n.equals("offline_access") || n.equals("uma_authorization") || n.startsWith("default-roles-")) {
                    continue;
                }
                result.add(r);
            }
            return result;
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public List<KeycloakRole> listUserRealmRoles(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakRole[]> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole[].class);
            KeycloakRole[] body = resp.getBody();
            return body == null ? List.of() : List.of(body);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void addRealmRole(String userId, String roleName) {
        KeycloakRole role = roleByName(roleName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        try {
            restTemplate.postForEntity(
                    internalUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    new HttpEntity<>(List.of(role), headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    public void removeRealmRole(String userId, String roleName) {
        KeycloakRole role = roleByName(roleName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        try {
            restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.DELETE, new HttpEntity<>(List.of(role), headers), Void.class);
        } catch (KeycloakUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUnavailableException();
        }
    }

    private KeycloakRole roleByName(String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        try {
            ResponseEntity<KeycloakRole> resp = restTemplate.exchange(
                    internalUrl + "/admin/realms/" + realm + "/roles/" + name,
                    HttpMethod.GET, new HttpEntity<>(headers), KeycloakRole.class);
            KeycloakRole role = resp.getBody();
            if (role == null) {
                throw new KeycloakUnavailableException();
            }
            return role;
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

    public static class UserConflictException extends RuntimeException {}
}
