package com.mr486.generator.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Requête JSON acceptée par {@code POST /api/generate/platform}.
 * <p>
 * Tous les champs ont des valeurs par défaut : un body vide produit la plateforme de référence
 * (10 modules, 15 services Docker, observabilité désactivée). Les champs sont consommés par les
 * différents processors du pipeline pour transformer le ZIP modèle.
 */
@Data
public class PlatformGenerationRequest {
    /** Nom du répertoire racine dans le ZIP généré et identifiant logique de la plateforme. */
    private String name = "ms-platform";
    /** Identifiant Maven {@code <groupId>} appliqué à tous les pom générés. */
    private String groupId = "com.mr486";
    /** Package Java racine sous lequel toutes les classes sont placées. */
    private String basePackage = "com.mr486.msplatform";
    /** Version Java déclarée dans {@code <java.version>} du pom parent. */
    private String javaVersion = "17";
    /** Services métier à générer dynamiquement ; si vide, on conserve service-a/b/c par défaut. */
    private List<ResourceModuleRequest> resources = new ArrayList<>();
    /** Configuration du service batch (replicas, concurrence, mémoire). */
    private BatchOptions batch = new BatchOptions();
    /** Bascules d'activation des composants optionnels (Keycloak, Redis, RabbitMQ, etc.). */
    private FeatureOptions features = new FeatureOptions();
}
