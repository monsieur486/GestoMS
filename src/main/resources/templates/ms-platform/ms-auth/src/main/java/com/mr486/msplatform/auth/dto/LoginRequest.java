package com.mr486.msplatform.auth.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.AllArgsConstructor;

/**
 * DTO de requête de connexion contenant les identifiants de l'utilisateur
 * (nom d'utilisateur et mot de passe).
 */
@Getter @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}
