package com.mr486.generator.dto;

/**
 * Types de base supportés pour un service issu de {@link ResourceModuleRequest}.
 */
public enum DatabaseType {

    /** Base relationnelle en mémoire dans la JVM (pas de container Docker dédié, pas de volume). */
    H2,

    /** PostgreSQL externe ; déclenche la génération d'un service Docker {@code {name}-db}. */
    POSTGRES,

    /** MongoDB ; service Docker {@code {name}-db} et {@code @Document} au lieu de {@code @Entity}. */
    MONGO
}
