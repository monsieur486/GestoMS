package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.dto.ChatMessage;
import com.mr486.msplatform.webui.service.ChatHistory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * Contrôleur du chat temps réel : affiche l'historique et diffuse les messages via STOMP/WebSocket.
 */
@Controller
public class ChatController {

    private final ChatHistory chatHistory;

    public ChatController(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    /**
     * Affiche la page de chat avec l'historique récent.
     *
     * @param model le modèle Thymeleaf
     * @return le nom de la vue {@code chat}
     */
    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("history", chatHistory.recent());
        return "chat";
    }

    /**
     * Reçoit un message STOMP, remplace l'auteur par le nom du Principal authentifié,
     * persiste le message et le diffuse sur {@code /topic/chat}.
     *
     * @param in        le message entrant (l'auteur est ignoré, remplacé par le Principal)
     * @param principal le Principal Spring Security de l'expéditeur
     * @return le message signé à diffuser
     */
    @MessageMapping("/chat.send")
    @SendTo("/topic/chat")
    public ChatMessage send(ChatMessage in, Principal principal) {
        ChatMessage message = new ChatMessage(principal.getName(), in.text());
        chatHistory.add(message);
        return message;
    }
}
