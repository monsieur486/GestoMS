package com.mr486.generator.dto;

import lombok.Data;

/**
 * Spécification d'un service métier à générer dynamiquement.
 * <p>
 * Si {@link com.mr486.generator.dto.PlatformGenerationRequest#getResources()} contient au moins
 * une instance, {@link com.mr486.generator.pipeline.processor.ResourceExpandProcessor} retire les
 * services par défaut (service-a/b/c) et clone le template service-a une fois par entrée, en
 * appliquant les transformations de noms, type de base et type d'identifiant.
 */
@Data
public class ResourceModuleRequest {
    /** Nom kebab-case du module et du dossier (ex: {@code "order-service"}). Obligatoire. */
    private String serviceName;
    /** Nom PascalCase de la classe d'entité (ex: {@code "Order"}). Obligatoire. */
    private String className;
    /** Préfixe REST exposé par le contrôleur. Si {@code null}, valeur dérivée : {@code /api/{classNameLower}s}. */
    private String routePrefix;
    /** Type de base à utiliser : {@link DatabaseType#POSTGRES} par défaut, {@link DatabaseType#H2} en mémoire, {@link DatabaseType#MONGO}. */
    private DatabaseType databaseType;
    /** Type d'identifiant pour les entités SQL : {@link IdType#LONG} par défaut. Ignoré pour Mongo (toujours String). */
    private IdType idType;

    /** Retourne {@link #routePrefix} s'il est défini, sinon dérive {@code /api/{classNameLower}s}. */
    public String getEffectiveRoutePrefix() {
        return (routePrefix != null && !routePrefix.isBlank())
            ? routePrefix
            : "/api/" + className.toLowerCase() + "s";
    }
}
