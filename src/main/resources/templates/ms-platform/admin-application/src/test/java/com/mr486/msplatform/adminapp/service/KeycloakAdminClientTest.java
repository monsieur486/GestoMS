package com.mr486.msplatform.adminapp.service;

import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
}
