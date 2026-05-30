package com.mr486.msplatform.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MsAuthTokens(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("opaque_refresh_token") String opaqueRefreshToken,
        @JsonProperty("expires_in") long expiresIn) {
}
