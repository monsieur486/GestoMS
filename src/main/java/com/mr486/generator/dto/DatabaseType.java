package com.mr486.generator.dto;

/**
 * Types de base supportés pour un service issu de {@link ResourceModuleRequest}.
 * <ul>
 *   <li>{@link #H2} — base relationnelle en mémoire dans la JVM (pas de container Docker dédié, pas de volume).</li>
 *   <li>{@link #POSTGRES} — PostgreSQL externe ; déclenche la génération d'un service Docker {@code {name}-db}.</li>
 *   <li>{@link #MONGO} — MongoDB ; déclenche la génération d'un service Docker {@code {name}-db} et utilise un {@code @Document} au lieu de {@code @Entity}.</li>
 * </ul>
 */
public enum DatabaseType { H2, POSTGRES, MONGO }
