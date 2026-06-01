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
