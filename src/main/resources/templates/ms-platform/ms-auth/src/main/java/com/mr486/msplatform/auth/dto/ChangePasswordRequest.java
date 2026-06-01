package com.mr486.msplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO de requête de changement de mot de passe self-service : ancien mot de passe
 * (pour vérification) et nouveau mot de passe à poser.
 */
@Getter @NoArgsConstructor @AllArgsConstructor
public class ChangePasswordRequest {
    @NotBlank private String oldPassword;
    @NotBlank private String newPassword;
}
