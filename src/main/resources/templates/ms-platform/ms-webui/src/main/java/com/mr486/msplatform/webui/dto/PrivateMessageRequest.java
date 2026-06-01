package com.mr486.msplatform.webui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Requête d'envoi privé reçue via STOMP : destinataire ({@code to}) et contenu ({@code text}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivateMessageRequest(String to, String text) {}
