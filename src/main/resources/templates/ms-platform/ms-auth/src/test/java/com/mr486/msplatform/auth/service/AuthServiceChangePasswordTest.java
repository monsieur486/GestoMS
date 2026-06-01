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
 * Tests unitaires de {@link AuthService#changeOwnPassword} : l'ancien mot de passe est
 * contrôlé via un password grant, l'id Keycloak est résolu par {@code preferred_username}
 * (le token n'expose pas toujours {@code sub}), et un ancien mot de passe invalide
 * renvoie 422 sans appeler le reset.
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

    private JwtAuthenticationToken jwt(String username) {
        Jwt token = Mockito.mock(Jwt.class);
        when(token.getClaimAsString("preferred_username")).thenReturn(username);
        JwtAuthenticationToken auth = Mockito.mock(JwtAuthenticationToken.class);
        when(auth.getToken()).thenReturn(token);
        return auth;
    }

    private void grantSucceeds() {
        KeycloakTokenResponse ok = Mockito.mock(KeycloakTokenResponse.class);
        when(ok.getAccessToken()).thenReturn("valid");
        when(restTemplate.postForEntity(anyString(), any(), eq(KeycloakTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(ok));
    }

    @Test
    void resolves_user_id_by_username_then_resets_when_old_password_is_valid() {
        grantSucceeds();
        when(adminClient.findUserId("alice")).thenReturn("uid-1");

        service.changeOwnPassword(jwt("alice"), "old", "new");

        verify(adminClient).resetPassword("uid-1", "new");
    }

    @Test
    void resets_via_username_even_when_token_has_no_sub() {
        // Régression : les tokens du client ms-gateway n'exposent pas 'sub' ; l'id doit
        // venir de findUserId(username), pas de getSubject() (qui serait null).
        grantSucceeds();
        when(adminClient.findUserId("admin2")).thenReturn("02904f91-uuid");

        service.changeOwnPassword(jwt("admin2"), "old", "new");

        verify(adminClient).resetPassword("02904f91-uuid", "new");
    }

    @Test
    void returns_422_and_skips_lookup_and_reset_when_old_password_is_wrong() {
        when(restTemplate.postForEntity(anyString(), any(), eq(KeycloakTokenResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.changeOwnPassword(jwt("alice"), "bad", "new"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(adminClient, never()).findUserId(anyString());
        verify(adminClient, never()).resetPassword(anyString(), anyString());
    }
}
