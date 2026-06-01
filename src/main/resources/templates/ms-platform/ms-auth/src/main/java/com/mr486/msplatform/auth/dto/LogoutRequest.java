package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO de requête de déconnexion contenant le refresh token opaque à révoquer.
 * Le champ est optionnel : si absent, seul le JTI de l'access token est blacklisté.
 */
@Getter @NoArgsConstructor @AllArgsConstructor
public class LogoutRequest {
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
}
