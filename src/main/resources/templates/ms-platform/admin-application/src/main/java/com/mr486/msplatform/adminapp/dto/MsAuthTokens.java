package com.mr486.msplatform.adminapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO contenant les tokens retournés par ms-auth lors d'une connexion ou d'un rafraîchissement.
 */
public record MsAuthTokens(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("opaque_refresh_token") String opaqueRefreshToken,
        @JsonProperty("expires_in") long expiresIn) {
}
