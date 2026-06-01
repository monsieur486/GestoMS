package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.dto.ChatMessage;
import com.mr486.msplatform.client.service.ChatHistory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ChatController} :
 * vérification que l'auteur d'un message est toujours pris du Principal.
 */
class ChatControllerTest {

    @Test
    void send_attributes_author_from_principal_and_persists() {
        ChatHistory history = Mockito.mock(ChatHistory.class);
        ChatController controller = new ChatController(history);
        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("alice");

        // le client tente d'usurper "bob" — seul le texte doit être retenu, l'auteur vient du Principal
        ChatMessage out = controller.send(new ChatMessage("bob", "hello"), principal);

        assertThat(out.user()).isEqualTo("alice");
        assertThat(out.text()).isEqualTo("hello");
        verify(history).add(out);
    }
}
