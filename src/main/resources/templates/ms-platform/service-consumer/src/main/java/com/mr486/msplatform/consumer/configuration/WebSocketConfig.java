package com.mr486.msplatform.consumer.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration WebSocket STOMP : expose l'endpoint {@code /ws} (avec fallback SockJS),
 * active un broker en mémoire sur le préfixe {@code /topic} et route les messages
 * applicatifs via le préfixe {@code /app}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Enregistre l'endpoint STOMP {@code /ws} avec SockJS et origines autorisées sans restriction.
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
     * Configure le broker de messages en mémoire ({@code /topic}) et le préfixe
     * des destinations applicatives ({@code /app}).
     *
     * @param registry le registre du broker de messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
