package com.mr486.msplatform.adminapp.service;

import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import com.mr486.msplatform.adminapp.dto.KeycloakRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakAdminClientTest {

    private RestTemplate restTemplate;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        client = new KeycloakAdminClient(restTemplate, "http://kc", "ms-realm", "admin", "admin");
    }

    @Test
    void lists_users_after_obtaining_admin_token() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        KeycloakUser[] users = {
                new KeycloakUser("1", "alice", "alice@x.io", "Al", "Ice", true),
                new KeycloakUser("2", "bob", "bob@x.io", "Bo", "B", false)
        };
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/users"), eq(HttpMethod.GET), any(), eq(KeycloakUser[].class)))
                .thenReturn(ResponseEntity.ok(users));

        assertThat(client.listUsers()).extracting(KeycloakUser::username).containsExactly("alice", "bob");
    }

    @Test
    void throws_when_token_missing() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of()));
        assertThatThrownBy(() -> client.listUsers())
                .isInstanceOf(KeycloakAdminClient.KeycloakUnavailableException.class);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void create_user_posts_representation_with_permanent_password() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));

        client.createUser("carol", "carol@x.io", "Ca", "Rol", "secret");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("/admin/realms/ms-realm/users"), captor.capture(), eq(Void.class));
        Map body = (Map) captor.getValue().getBody();
        assertThat(body.get("username")).isEqualTo("carol");
        assertThat(body.get("enabled")).isEqualTo(true);
        List creds = (List) body.get("credentials");
        Map cred = (Map) creds.get(0);
        assertThat(cred.get("temporary")).isEqualTo(false);
        assertThat(cred.get("value")).isEqualTo("secret");
    }

    @Test
    void create_user_conflict_maps_to_user_conflict_exception() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.postForEntity(contains("/admin/realms/ms-realm/users"), any(), eq(Void.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "409", null, null, null));

        assertThatThrownBy(() -> client.createUser("dup", "d@x.io", "D", "Up", "pw"))
                .isInstanceOf(KeycloakAdminClient.UserConflictException.class);
    }

    @Test
    void delete_user_sends_delete_to_user_id() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));

        client.deleteUser("123");

        verify(restTemplate).exchange(contains("/admin/realms/ms-realm/users/123"),
                eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    @Test
    void list_realm_roles_filters_builtins() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        KeycloakRole[] roles = {
                new KeycloakRole("a", "ADMIN"),
                new KeycloakRole("b", "USER_SERVICE_A"),
                new KeycloakRole("c", "offline_access"),
                new KeycloakRole("d", "uma_authorization"),
                new KeycloakRole("e", "default-roles-ms-realm")
        };
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/roles"), eq(HttpMethod.GET), any(), eq(KeycloakRole[].class)))
                .thenReturn(ResponseEntity.ok(roles));

        assertThat(client.listRealmRoles()).extracting(KeycloakRole::name).containsExactly("ADMIN", "USER_SERVICE_A");
    }

    @Test
    void list_user_realm_roles_parses() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/users/uid/role-mappings/realm"), eq(HttpMethod.GET), any(), eq(KeycloakRole[].class)))
                .thenReturn(ResponseEntity.ok(new KeycloakRole[]{new KeycloakRole("a", "ADMIN")}));

        assertThat(client.listUserRealmRoles("uid")).extracting(KeycloakRole::name).containsExactly("ADMIN");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void add_realm_role_resolves_then_posts_representation() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/roles/ADMIN"), eq(HttpMethod.GET), any(), eq(KeycloakRole.class)))
                .thenReturn(ResponseEntity.ok(new KeycloakRole("rid", "ADMIN")));

        client.addRealmRole("uid", "ADMIN");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("/users/uid/role-mappings/realm"), captor.capture(), eq(Void.class));
        List body = (List) captor.getValue().getBody();
        assertThat(body).hasSize(1);
        KeycloakRole sent = (KeycloakRole) body.get(0);
        assertThat(sent.id()).isEqualTo("rid");
        assertThat(sent.name()).isEqualTo("ADMIN");
    }

    @Test
    void remove_realm_role_sends_delete_with_representation() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/roles/USER_BATCH"), eq(HttpMethod.GET), any(), eq(KeycloakRole.class)))
                .thenReturn(ResponseEntity.ok(new KeycloakRole("rid", "USER_BATCH")));

        client.removeRealmRole("uid", "USER_BATCH");

        verify(restTemplate).exchange(contains("/users/uid/role-mappings/realm"),
                eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void update_user_merges_fields_and_preserves_username() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));
        Map<String, Object> existing = new HashMap<>();
        existing.put("username", "carol");
        existing.put("email", "old@x.io");
        existing.put("enabled", true);
        when(restTemplate.exchange(contains("/admin/realms/ms-realm/users/uid"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(existing));

        client.updateUser("uid", "new@x.io", "Ca", "Rol", false);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/admin/realms/ms-realm/users/uid"), eq(HttpMethod.PUT), captor.capture(), eq(Void.class));
        Map put = (Map) captor.getValue().getBody();
        assertThat(put.get("username")).isEqualTo("carol");   // préservé (GET puis PUT)
        assertThat(put.get("email")).isEqualTo("new@x.io");   // modifié
        assertThat(put.get("firstName")).isEqualTo("Ca");
        assertThat(put.get("enabled")).isEqualTo(false);      // modifié
    }

    @Test
    @SuppressWarnings("rawtypes")
    void reset_password_puts_permanent_credential() {
        when(restTemplate.postForEntity(contains("/realms/master/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "tok")));

        client.resetPassword("uid", "secret");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/users/uid/reset-password"), eq(HttpMethod.PUT), captor.capture(), eq(Void.class));
        Map body = (Map) captor.getValue().getBody();
        assertThat(body.get("type")).isEqualTo("password");
        assertThat(body.get("value")).isEqualTo("secret");
        assertThat(body.get("temporary")).isEqualTo(false);
    }
}
