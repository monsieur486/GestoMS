package com.mr486.msplatform.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Message de chat échangé via STOMP : auteur ({@code user}) et contenu ({@code text}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(String user, String text) {}
