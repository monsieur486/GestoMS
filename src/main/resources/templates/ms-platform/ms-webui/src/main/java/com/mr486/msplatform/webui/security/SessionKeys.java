package com.mr486.msplatform.webui.security;

/** Constantes des clés de session HTTP utilisées pour stocker les tokens d'authentification. */
public final class SessionKeys {
    /** Clé de session pour l'access token JWT. */
    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    /** Clé de session pour le refresh token opaque. */
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private SessionKeys() {}
}
