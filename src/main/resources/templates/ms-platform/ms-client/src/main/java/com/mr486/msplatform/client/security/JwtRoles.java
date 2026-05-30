package com.mr486.msplatform.client.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Extrait les rôles realm d'un access token JWT sans librairie JWT (base64url du payload). */
public final class JwtRoles {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtRoles() {}

    public static List<String> realmRoles(String accessToken) {
        List<String> roles = new ArrayList<>();
        if (accessToken == null) return roles;
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return roles;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode arr = MAPPER.readTree(payload).path("realm_access").path("roles");
            if (arr.isArray()) arr.forEach(n -> roles.add(n.asText()));
        } catch (Exception ignored) {
            // token illisible -> aucun rôle
        }
        return roles;
    }
}
