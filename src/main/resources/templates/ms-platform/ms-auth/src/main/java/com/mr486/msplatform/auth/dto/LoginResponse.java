package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;import lombok.Getter;
@Getter @AllArgsConstructor
public class LoginResponse {
    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
    @JsonProperty("expires_in") private long expiresIn;
}
