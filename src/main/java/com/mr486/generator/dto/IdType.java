package com.mr486.generator.dto;

/**
 * Types d'identifiant supportés pour les entités SQL générées.
 *
 * <p>Pour {@link DatabaseType#MONGO}, ce champ est ignoré : les documents Mongo utilisent toujours un
 * identifiant {@code String}.
 */
public enum IdType {
    /** Identifiant entier sur 32 bits, {@code @GeneratedValue(strategy=IDENTITY)}. */
    INTEGER,
    /** Identifiant entier sur 64 bits, {@code @GeneratedValue(strategy=IDENTITY)} — valeur par défaut. */
    LONG,
    /** UUID, {@code @GeneratedValue(strategy=UUID)} ; SQL utilise {@code gen_random_uuid()}. */
    UUID,
    /**
     * Accepté pour compatibilité de requête uniquement.
     * Les ressources Mongo sont générées avec un identifiant String automatiquement.
     */
    STRING
}
