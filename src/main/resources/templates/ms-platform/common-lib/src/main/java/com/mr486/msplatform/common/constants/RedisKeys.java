package com.mr486.msplatform.common.constants;

/**
 * Constantes et fabriques de clés Redis utilisées par la plateforme.
 * Centralise tous les préfixes de clés pour éviter les collisions entre modules.
 * Classe utilitaire non instanciable.
 */
public final class RedisKeys {

    /** Clé du set Redis contenant les identifiants de tous les jobs batch. */
    public static final String BATCH_JOBS_ALL = "batch:jobs:all";

    /**
     * Construit la clé Redis pour les données d'un job batch spécifique.
     *
     * @param jobId l'identifiant du job
     * @return la clé Redis correspondante
     */
    public static String batchJob(String jobId) {
        return "batch:job:" + jobId;
    }

    /**
     * Construit la clé Redis pour la liste des jobs d'un utilisateur donné.
     *
     * @param userId l'identifiant de l'utilisateur
     * @return la clé Redis correspondante
     */
    public static String batchUserJobs(Long userId) {
        return "batch:user:" + userId + ":jobs";
    }

    /**
     * Construit la clé Redis pour la liste noire d'un access token identifié par son JTI.
     *
     * @param jti l'identifiant unique du token JWT
     * @return la clé Redis correspondante
     */
    public static String authBlacklist(String jti) {
        return "auth:blacklist:" + jti;
    }

    /**
     * Construit la clé Redis pour stocker le refresh token Keycloak associé
     * à un token opaque.
     *
     * @param opaqueToken le token opaque émis par ms-auth
     * @return la clé Redis correspondante
     */
    public static String authRefresh(String opaqueToken) {
        return "auth:refresh:" + opaqueToken;
    }

    private RedisKeys() {
    }
}
