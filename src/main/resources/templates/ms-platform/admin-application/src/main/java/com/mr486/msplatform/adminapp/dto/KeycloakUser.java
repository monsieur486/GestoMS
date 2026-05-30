package com.mr486.msplatform.adminapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(String id, String username, String email, String firstName, String lastName, boolean enabled) {}
