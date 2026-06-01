package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Met à jour {@link PresenceService} au gré des connexions/déconnexions STOMP et
 * diffuse la liste des connectés sur {@code /topic/presence} à chaque changement.
 */
@Component
public class PresenceEventListener {

    private static final String PRESENCE_TOPIC = "/topic/presence";

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Construit le listener avec le service de présence et le template de diffusion.
     *
     * @param presenceService   l'état de présence en mémoire
     * @param messagingTemplate le template de diffusion STOMP
     */
    public PresenceEventListener(PresenceService presenceService, SimpMessagingTemplate messagingTemplate) {
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * À la connexion STOMP : enregistre la session de l'utilisateur authentifié et diffuse le roster.
     *
     * @param event l'événement de connexion STOMP
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal user = event.getUser();
        if (sessionId != null && user != null) {
            presenceService.add(sessionId, user.getName());
            broadcast();
        }
    }

    /**
     * À la déconnexion STOMP : retire la session et diffuse le roster mis à jour.
     *
     * @param event l'événement de déconnexion STOMP
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.remove(event.getSessionId());
        broadcast();
    }

    private void broadcast() {
        messagingTemplate.convertAndSend(PRESENCE_TOPIC, presenceService.connectedUsers());
    }
}
