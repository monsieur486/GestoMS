package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.pipeline.TemplateLoader;
import com.mr486.generator.zip.GeneratedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Orchestrateur du pipeline de génération.
 * <p>
 * Charge le modèle puis applique chaque {@link FileProcessor} dans l'ordre fourni par Spring
 * ({@link org.springframework.core.annotation.Order @Order}). La sortie est une liste de fichiers
 * prête à être empaquetée par {@link com.mr486.generator.zip.ZipService}.
 * <p>
 * Cette classe reste délibérément minimaliste — chaque préoccupation est encapsulée dans son
 * propre processor, donc ajouter une nouvelle fonctionnalité ne touche pas ce service.
 */
@Service
@RequiredArgsConstructor
public class PlatformGeneratorService {

    private final TemplateLoader loader;
    /** Liste injectée par Spring, triée automatiquement par {@code @Order}. */
    private final List<FileProcessor> processors;

    /**
     * Exécute le pipeline complet et retourne la liste finale des fichiers.
     *
     * @param request requête JSON validée
     * @return fichiers de la plateforme générée, prêts pour le ZIP
     */
    public List<GeneratedFile> generate(PlatformGenerationRequest request) {
        GenerationContext ctx = GenerationContext.from(request);
        List<GeneratedFile> files = loader.load();
        for (FileProcessor processor : processors) {
            files = processor.process(files, ctx);
        }
        return files;
    }
}
