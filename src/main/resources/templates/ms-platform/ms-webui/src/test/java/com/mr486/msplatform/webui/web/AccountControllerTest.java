package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.config.WebUiProperties;
import com.mr486.msplatform.webui.security.SessionKeys;
import com.mr486.msplatform.webui.service.MsAuthClient;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AccountController#changePassword} : non-correspondance
 * rejetée sans appel backend, succès et ancien mot de passe invalide.
 */
class AccountControllerTest {

    private WebUiProperties props;
    private MsAuthClient msAuthClient;
    private AccountController controller;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        props = Mockito.mock(WebUiProperties.class);
        msAuthClient = Mockito.mock(MsAuthClient.class);
        controller = new AccountController(props, msAuthClient);
        session = Mockito.mock(HttpSession.class);
        when(session.getAttribute(SessionKeys.ACCESS_TOKEN)).thenReturn("at");
        when(session.getAttribute(SessionKeys.REFRESH_TOKEN)).thenReturn("rt");
    }

    @Test
    void rejects_mismatch_without_calling_backend() {
        String view = controller.changePassword("old", "new", "different", session);
        assertThat(view).isEqualTo("redirect:/account?mismatch");
        verify(msAuthClient, never()).changePassword(anyString(), anyString(), anyString());
    }

    @Test
    void redirects_ok_on_success() {
        String view = controller.changePassword("old", "new", "new", session);
        assertThat(view).isEqualTo("redirect:/account?ok");
        verify(msAuthClient).changePassword("at", "old", "new");
    }

    @Test
    void redirects_wrong_when_old_password_invalid() {
        Mockito.doThrow(new MsAuthClient.WrongOldPasswordException())
                .when(msAuthClient).changePassword(any(), any(), any());
        String view = controller.changePassword("bad", "new", "new", session);
        assertThat(view).isEqualTo("redirect:/account?wrong");
    }
}
