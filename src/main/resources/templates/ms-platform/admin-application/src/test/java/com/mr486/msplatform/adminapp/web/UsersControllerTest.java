package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires de {@link UsersController#resetPassword} : la réinitialisation n'est
 * effectuée que si le mot de passe et sa confirmation correspondent.
 */
class UsersControllerTest {

    private KeycloakAdminClient adminClient;
    private UsersController controller;

    @BeforeEach
    void setUp() {
        adminClient = Mockito.mock(KeycloakAdminClient.class);
        controller = new UsersController(adminClient);
    }

    @Test
    void resets_when_passwords_match() {
        String view = controller.resetPassword("id1", "secret", "secret");
        assertThat(view).isEqualTo("redirect:/users/id1/edit?pwd");
        verify(adminClient).resetPassword("id1", "secret");
    }

    @Test
    void rejects_when_passwords_differ() {
        String view = controller.resetPassword("id1", "secret", "other");
        assertThat(view).isEqualTo("redirect:/users/id1/edit?mismatch");
        verify(adminClient, never()).resetPassword(anyString(), anyString());
    }
}
