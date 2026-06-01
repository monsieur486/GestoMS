package com.mr486.msplatform.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse de ms-auth après un login ou un refresh :
 * access token JWT, refresh token opaque et durée de validité en secondes.
 */
public record MsAuthTokens(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("opaque_refresh_token") String opaqueRefreshToken,
        @JsonProperty("expires_in") long expiresIn) {
}
