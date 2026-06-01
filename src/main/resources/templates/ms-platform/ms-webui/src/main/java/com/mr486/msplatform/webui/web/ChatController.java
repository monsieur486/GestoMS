package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.dto.ChatMessage;
import com.mr486.msplatform.webui.dto.PrivateMessageRequest;
import com.mr486.msplatform.webui.service.ChatHistory;
import com.mr486.msplatform.webui.service.PresenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur du chat temps réel : affiche l'historique et diffuse les messages via STOMP/WebSocket.
 */
@Controller
public class ChatController {

    private final ChatHistory chatHistory;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatHistory chatHistory, PresenceService presenceService,
                          SimpMessagingTemplate messagingTemplate) {
        this.chatHistory = chatHistory;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Affiche la page de chat avec l'historique récent.
     *
     * @param model     le modèle Thymeleaf
     * @param principal le Principal Spring Security de l'utilisateur courant
     * @return le nom de la vue {@code chat}
     */
    @GetMapping("/chat")
    public String chat(Model model, Principal principal) {
        model.addAttribute("history", chatHistory.recent());
        model.addAttribute("username", principal.getName());
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
        ChatMessage message = new ChatMessage(principal.getName(), in.text(), System.currentTimeMillis());
        chatHistory.add(message);
        return message;
    }

    /**
     * Diffuse un signal de frappe portant l'utilisateur authentifié (non persisté).
     *
     * @param principal le Principal de l'expéditeur
     * @return une map {@code {"user": <nom>}} diffusée sur {@code /topic/typing}
     */
    @MessageMapping("/chat.typing")
    @SendTo("/topic/typing")
    public Map<String, String> typing(Principal principal) {
        return Map.of("user", principal.getName());
    }

    /**
     * Route un message privé vers le destinataire via une user destination
     * ({@code /user/{to}/queue/private}). L'expéditeur est signé par le Principal ;
     * le message n'est pas persisté.
     *
     * @param in        la requête {@code {to, text}}
     * @param principal le Principal de l'expéditeur
     */
    @MessageMapping("/chat.private")
    public void privateMessage(PrivateMessageRequest in, Principal principal) {
        ChatMessage message = new ChatMessage(principal.getName(), in.text(), System.currentTimeMillis());
        messagingTemplate.convertAndSendToUser(in.to(), "/queue/private", message);
    }

    /**
     * Répond à un client qui vient de s'abonner en rediffusant la liste des connectés.
     *
     * @return la liste des utilisateurs connectés
     */
    @MessageMapping("/presence.hello")
    @SendTo("/topic/presence")
    public List<String> presenceHello() {
        return presenceService.connectedUsers();
    }
}
