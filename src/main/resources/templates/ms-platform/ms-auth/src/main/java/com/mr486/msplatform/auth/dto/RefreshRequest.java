package com.mr486.msplatform.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.AllArgsConstructor;
@Getter @NoArgsConstructor @AllArgsConstructor
public class RefreshRequest {
    @JsonProperty("opaque_refresh_token") private String opaqueRefreshToken;
}
