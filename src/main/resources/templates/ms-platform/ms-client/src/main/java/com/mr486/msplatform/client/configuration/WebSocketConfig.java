package com.mr486.msplatform.client.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration STOMP/WebSocket : endpoint SockJS {@code /ws},
 * broker en mémoire sur {@code /topic}, préfixe applicatif {@code /app}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Enregistre l'endpoint SockJS {@code /ws} accessible depuis n'importe quelle origine.
     *
     * @param registry le registre des endpoints STOMP
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Active le broker simple sur {@code /topic} et définit le préfixe {@code /app}
     * pour les messages destinés aux méthodes {@code @MessageMapping}.
     *
     * @param registry le registre de configuration du broker de messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
