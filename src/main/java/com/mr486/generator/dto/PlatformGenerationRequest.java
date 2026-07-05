package com.mr486.generator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Requête JSON acceptée par {@code POST /api/generate/platform}.
 *
 * <p>Tous les champs ont des valeurs par défaut : un body vide produit la plateforme de référence
 * (10 modules, 15 services Docker, observabilité désactivée). Les champs sont consommés par les
 * différents processors du pipeline pour transformer le ZIP modèle.
 *
 * <p>Les identifiants injectés dans les chemins et les pom générés sont contraints à des listes
 * blanches ; {@code resources} est borné pour éviter l'épuisement mémoire et cascade la validation
 * ({@code @Valid}) vers chaque {@link ResourceModuleRequest}.
 */
@Data
public class PlatformGenerationRequest {
    /**
     * Nom du répertoire racine dans le ZIP généré et identifiant logique de la plateforme
     * (vide → {@code ms-platform}).
     */
    @Pattern(regexp = "^([a-z0-9][a-z0-9-]{0,63})?$")
    private String name = "ms-platform";
    /** Identifiant Maven {@code <groupId>} appliqué à tous les pom générés. */
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$")
    private String groupId = "com.mr486";
    /** Package Java racine sous lequel toutes les classes sont placées. */
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$")
    private String basePackage = "com.mr486.msplatform";
    /** Version Java déclarée dans {@code <java.version>} du pom parent (17 ou 21). */
    @NotBlank
    @Pattern(regexp = "^(17|21)$")
    private String javaVersion = "17";
    /** Services métier à générer dynamiquement ; si vide, on conserve service-a/b/c par défaut. */
    @Size(max = 20)
    private List<@Valid @NotNull ResourceModuleRequest> resources = new ArrayList<>();
    /** Configuration du service batch (replicas, concurrence, mémoire). */
    private BatchOptions batch = new BatchOptions();
    /** Bascules d'activation des composants optionnels (Keycloak, Redis, RabbitMQ, etc.). */
    private FeatureOptions features = new FeatureOptions();
}
