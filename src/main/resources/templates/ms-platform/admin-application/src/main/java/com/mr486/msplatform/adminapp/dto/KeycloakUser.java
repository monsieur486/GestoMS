package com.mr486.msplatform.adminapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO représentant un utilisateur Keycloak (identifiant, nom d'utilisateur, e-mail, prénom, nom, état actif).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled) {}
