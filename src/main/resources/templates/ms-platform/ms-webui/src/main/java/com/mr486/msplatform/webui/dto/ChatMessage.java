package com.mr486.msplatform.webui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message de chat échangé via STOMP : auteur ({@code user}), contenu ({@code text})
 * et horodatage serveur en millisecondes epoch ({@code timestamp}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(String user, String text, long timestamp) {}
