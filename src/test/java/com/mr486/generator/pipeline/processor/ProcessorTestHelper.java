package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utilitaires partagés entre les tests de processeurs : création de
 * {@link GeneratedFile} à partir de chaînes, lecture de contenu, et
 * construction de contextes de génération préconfigurés.
 */
public final class ProcessorTestHelper {

    private ProcessorTestHelper() {}

    /**
     * Crée un {@link GeneratedFile} non exécutable à partir d'un chemin et d'un contenu texte.
     */
    public static GeneratedFile file(String path, String content) {
        return new GeneratedFile(path, content.getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * Crée un {@link GeneratedFile} à partir d'un chemin, d'un contenu texte et d'un flag exécutable.
     */
    public static GeneratedFile file(String path, String content, boolean executable) {
        return new GeneratedFile(path, content.getBytes(StandardCharsets.UTF_8), executable);
    }

    /**
     * Décode le contenu d'un {@link GeneratedFile} en chaîne UTF-8.
     */
    public static String contentOf(GeneratedFile f) {
        return new String(f.content(), StandardCharsets.UTF_8);
    }

    /**
     * Retourne un contexte de génération avec tous les paramètres par défaut.
     */
    public static GenerationContext defaultCtx() {
        return GenerationContext.from(new PlatformGenerationRequest());
    }

    /**
     * Retourne un contexte de génération avec le nom de plateforme spécifié.
     */
    public static GenerationContext ctxWithName(String name) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName(name);
        return GenerationContext.from(req);
    }

    /**
     * Retourne un contexte de génération avec le groupId et le basePackage spécifiés.
     */
    public static GenerationContext ctxWithPackage(String groupId, String basePackage) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setGroupId(groupId);
        req.setBasePackage(basePackage);
        return GenerationContext.from(req);
    }

    /**
     * Retourne un contexte de génération avec les features spécifiées.
     */
    public static GenerationContext ctxWithFeatures(FeatureOptions features) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setFeatures(features);
        return GenerationContext.from(req);
    }
}
