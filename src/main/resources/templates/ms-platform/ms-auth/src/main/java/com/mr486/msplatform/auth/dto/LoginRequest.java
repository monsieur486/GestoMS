package com.mr486.msplatform.auth.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.AllArgsConstructor;
@Getter @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}
