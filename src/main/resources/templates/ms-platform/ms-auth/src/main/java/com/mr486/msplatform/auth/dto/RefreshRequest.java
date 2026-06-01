package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO de requête de rafraîchissement de token contenant le refresh token opaque
 * préalablement émis lors de la connexion ou du dernier rafraîchissement.
 */
@Getter @NoArgsConstructor @AllArgsConstructor
public class RefreshRequest {
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
}
