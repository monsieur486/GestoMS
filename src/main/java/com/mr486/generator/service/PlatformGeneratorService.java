package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.pipeline.TemplateLoader;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrateur du pipeline de génération.
 *
 * <p>Charge le modèle puis applique chaque {@link FileProcessor} dans l'ordre fourni par Spring
 * ({@link org.springframework.core.annotation.Order @Order}). La sortie est une liste de fichiers
 * prête à être empaquetée par {@link com.mr486.generator.zip.ZipService}.
 *
 * <p>Cette classe reste délibérément minimaliste — chaque préoccupation est encapsulée dans son
 * propre processor, donc ajouter une nouvelle fonctionnalité ne touche pas ce service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformGeneratorService {

    private final TemplateLoader loader;
    /** Liste injectée par Spring, triée automatiquement par {@code @Order}. */
    private final List<FileProcessor> processors;

    /**
     * Exécute le pipeline complet et retourne la liste finale des fichiers.
     *
     * <p><b>Exemple :</b> pour une requête avec deux {@code resources[]}, charge le modèle puis
     * applique chaque processor dans l'ordre {@code @Order} ; la sortie contient les modules clonés
     * et les fichiers transversaux réécrits, prêts pour le ZIP.
     *
     * @param request requête JSON validée
     * @return fichiers de la plateforme générée, prêts pour le ZIP
     */
    public List<GeneratedFile> generate(PlatformGenerationRequest request) {
        GenerationContext ctx = GenerationContext.from(request);
        List<GeneratedFile> files = loader.load();
        log.debug("modèle chargé : {} fichiers, {} processor(s) à appliquer", files.size(), processors.size());
        for (FileProcessor processor : processors) {
            files = processor.process(files, ctx);
            log.debug("processor {} appliqué : {} fichiers", processor.getClass().getSimpleName(), files.size());
        }
        log.info("pipeline terminé : {} fichiers générés", files.size());
        return files;
    }
}
