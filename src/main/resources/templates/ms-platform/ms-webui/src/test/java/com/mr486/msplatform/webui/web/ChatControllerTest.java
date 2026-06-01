package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.dto.ChatMessage;
import com.mr486.msplatform.webui.service.ChatHistory;
import com.mr486.msplatform.webui.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ChatController} : l'auteur vient du Principal, le serveur
 * pose un timestamp, et l'événement de frappe renvoie l'utilisateur authentifié.
 */
class ChatControllerTest {

    @Test
    void send_attributes_author_and_server_timestamp() {
        ChatHistory history = Mockito.mock(ChatHistory.class);
        PresenceService presence = Mockito.mock(PresenceService.class);
        ChatController controller = new ChatController(history, presence);
        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("alice");

        // le client tente d'usurper "bob" et d'imposer un timestamp 0 — les deux sont écrasés serveur
        ChatMessage out = controller.send(new ChatMessage("bob", "hello", 0), principal);

        assertThat(out.user()).isEqualTo("alice");
        assertThat(out.text()).isEqualTo("hello");
        assertThat(out.timestamp()).isGreaterThan(0);
        verify(history).add(out);
    }

    @Test
    void typing_returns_authenticated_username() {
        ChatController controller = new ChatController(
                Mockito.mock(ChatHistory.class), Mockito.mock(PresenceService.class));
        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("alice");

        assertThat(controller.typing(principal)).containsEntry("user", "alice");
    }
}
