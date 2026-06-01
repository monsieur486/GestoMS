package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.dto.ChatMessage;
import com.mr486.msplatform.webui.dto.PrivateMessageRequest;
import com.mr486.msplatform.webui.service.ChatHistory;
import com.mr486.msplatform.webui.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ChatController} : auteur depuis le Principal, timestamp serveur,
 * signal de frappe, et routage d'un message privé vers le destinataire.
 */
class ChatControllerTest {

    private ChatController controller(SimpMessagingTemplate template) {
        return new ChatController(
                Mockito.mock(ChatHistory.class), Mockito.mock(PresenceService.class), template);
    }

    private Principal principal(String name) {
        Principal p = Mockito.mock(Principal.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    @Test
    void send_attributes_author_and_server_timestamp() {
        ChatHistory history = Mockito.mock(ChatHistory.class);
        ChatController controller = new ChatController(
                history, Mockito.mock(PresenceService.class), Mockito.mock(SimpMessagingTemplate.class));

        // le client tente d'usurper "bob" et d'imposer un timestamp 0 — les deux sont écrasés serveur
        ChatMessage out = controller.send(new ChatMessage("bob", "hello", 0), principal("alice"));

        assertThat(out.user()).isEqualTo("alice");
        assertThat(out.text()).isEqualTo("hello");
        assertThat(out.timestamp()).isGreaterThan(0);
        verify(history).add(out);
    }

    @Test
    void typing_returns_authenticated_username() {
        assertThat(controller(Mockito.mock(SimpMessagingTemplate.class)).typing(principal("alice")))
                .containsEntry("user", "alice");
    }

    @Test
    void private_message_routed_to_recipient_with_server_fields() {
        SimpMessagingTemplate template = Mockito.mock(SimpMessagingTemplate.class);
        ChatController controller = controller(template);

        controller.privateMessage(new PrivateMessageRequest("bob", "psst"), principal("alice"));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(template).convertAndSendToUser(eq("bob"), eq("/queue/private"), captor.capture());
        ChatMessage sent = captor.getValue();
        assertThat(sent.user()).isEqualTo("alice");
        assertThat(sent.text()).isEqualTo("psst");
        assertThat(sent.timestamp()).isGreaterThan(0);
    }
}
