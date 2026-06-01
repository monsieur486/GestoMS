package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO représentant la réponse brute de Keycloak lors de l'émission ou du
 * rafraîchissement d'un token (endpoint {@code /protocol/openid-connect/token}).
 */
@Getter @NoArgsConstructor
public class KeycloakTokenResponse {
    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("refresh_token") private String refreshToken;
    @JsonProperty("expires_in") private long expiresIn;
    @JsonProperty("refresh_expires_in") private long refreshExpiresIn;
}
