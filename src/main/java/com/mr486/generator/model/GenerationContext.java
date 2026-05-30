package com.mr486.generator.model;

import com.mr486.generator.dto.PlatformGenerationRequest;
import lombok.Value;
import java.util.Objects;

/**
 * État immuable partagé entre tous les processors d'un pipeline de génération.
 * <p>
 * Calculé une seule fois en amont par {@link com.mr486.generator.service.PlatformGeneratorService},
 * puis transmis à chaque appel de {@link com.mr486.generator.pipeline.FileProcessor#process}.
 * Contient la requête utilisateur brute plus deux préfixes de chemin déjà normalisés.
 */
@Value
public class GenerationContext {
    /** Requête JSON reçue par le contrôleur ; transmise telle quelle aux processors. */
    PlatformGenerationRequest request;
    /** Préfixe racine appliqué dans le ZIP de sortie (ex: {@code "my-platform"}). */
    String targetRoot;
    /** Préfixe racine présent dans le ZIP modèle, toujours {@code "ms-platform"}. */
    String sourceRoot;

    /**
     * Construit un contexte à partir d'une requête. Si {@code name} est null ou vide, retombe sur
     * {@code "ms-platform"} (cohérent avec le préfixe source : pas de renommage).
     *
     * @param request requête utilisateur, ne doit pas être null
     * @return contexte prêt à être passé au pipeline
     */
    public static GenerationContext from(PlatformGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String name = request.getName();
        String target = (name == null || name.isBlank()) ? "ms-platform" : name.trim();
        return new GenerationContext(request, target, "ms-platform");
    }
}
