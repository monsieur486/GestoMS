package com.mr486.msplatform.webui.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Extrait les rôles realm d'un access token JWT sans librairie JWT (base64url du payload). */
public final class JwtRoles {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtRoles() {}

    /**
     * Extrait les rôles {@code realm_access.roles} du payload d'un token JWT (décodage base64url).
     *
     * @param accessToken le token JWT brut (peut être {@code null})
     * @return la liste des noms de rôles ; liste vide si le token est {@code null} ou illisible
     */
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
