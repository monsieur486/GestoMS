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
     * Retourne une page d'utilisateurs du realm, avec filtre de recherche optionnel.
     *
     * @param search terme de recherche (sur username/email/prénom/nom), {@code null} ou vide pour tous
     * @param first  index du premier résultat (pagination)
     * @param max    nombre maximum de résultats
     * @return la liste des utilisateurs correspondants
     */
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

    /**
     * Retourne le nombre total d'utilisateurs du realm correspondant au filtre de recherche.
     *
     * @param search terme de recherche, {@code null} ou vide pour compter tous les utilisateurs
     * @return le nombre d'utilisateurs correspondants
     */
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

    /**
     * Crée un nouvel utilisateur dans le realm avec un mot de passe permanent.
     *
     * @param username  le nom d'utilisateur
     * @param email     l'adresse e-mail
     * @param firstName le prénom
     * @param lastName  le nom de famille
     * @param password  le mot de passe initial (permanent, non temporaire)
     * @throws UserConflictException        si le nom d'utilisateur est déjà pris
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /**
     * Supprime un utilisateur du realm par son identifiant Keycloak.
     *
     * @param id l'identifiant unique de l'utilisateur dans Keycloak
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /**
     * Met à jour les champs modifiables d'un utilisateur existant (GET-then-PUT pour préserver le username).
     *
     * @param id        l'identifiant unique de l'utilisateur dans Keycloak
     * @param email     la nouvelle adresse e-mail
     * @param firstName le nouveau prénom
     * @param lastName  le nouveau nom de famille
     * @param enabled   {@code true} pour activer le compte, {@code false} pour le désactiver
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /**
     * Récupère un utilisateur du realm par son identifiant Keycloak.
     *
     * @param id l'identifiant unique de l'utilisateur dans Keycloak
     * @return le {@link KeycloakUser} correspondant, ou {@code null} si non trouvé
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /**
     * Retourne la liste des rôles du realm en excluant les rôles Keycloak internes
     * ({@code offline_access}, {@code uma_authorization}, {@code default-roles-*}).
     *
     * @return la liste des rôles applicatifs du realm
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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
                if (n == null || n.equals("offline_access")
                        || n.equals("uma_authorization") || n.startsWith("default-roles-")) {
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

    /**
     * Retourne la liste des rôles realm actuellement assignés à un utilisateur.
     *
     * @param userId l'identifiant de l'utilisateur dans Keycloak
     * @return la liste des rôles realm de l'utilisateur
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /**
     * Assigne un rôle realm à un utilisateur (résolution du rôle par nom, puis POST de la représentation).
     *
     * @param userId   l'identifiant de l'utilisateur dans Keycloak
     * @param roleName le nom du rôle realm à assigner
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /**
     * Retire un rôle realm d'un utilisateur (résolution du rôle par nom, puis DELETE de la représentation).
     *
     * @param userId   l'identifiant de l'utilisateur dans Keycloak
     * @param roleName le nom du rôle realm à retirer
     * @throws KeycloakUnavailableException si Keycloak est inaccessible
     */
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

    /** Exception levée lorsque Keycloak est inaccessible ou retourne une erreur inattendue. */
    public static class KeycloakUnavailableException extends RuntimeException {}

    /** Exception levée lorsque la création d'un utilisateur échoue en raison d'un conflit (409). */
    public static class UserConflictException extends RuntimeException {}
}
