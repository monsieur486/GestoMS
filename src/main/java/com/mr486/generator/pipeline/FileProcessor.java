package com.mr486.generator.pipeline;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;

/**
 * Étape unitaire du pipeline de génération.
 * <p>
 * Chaque implémentation est un bean Spring stateless annoté
 * {@link org.springframework.core.annotation.Order @Order(N)} ; l'ordre dicte la séquence d'exécution.
 * Une étape transforme une liste de {@link GeneratedFile} (renommage, filtre, ajout, modification de
 * contenu) et renvoie une nouvelle liste — jamais d'effets de bord sur les entrées.
 * <p>
 * Ordre courant des processors :
 * <ol>
 *   <li>{@code @Order(10)} {@link com.mr486.generator.pipeline.processor.RootRenameProcessor} — renomme le préfixe racine.</li>
 *   <li>{@code @Order(20)} {@link com.mr486.generator.pipeline.processor.FeatureFilterProcessor} — filtre par {@code FeatureOptions}.</li>
 *   <li>{@code @Order(30)} {@link com.mr486.generator.pipeline.processor.PackagePlaceholderProcessor} — remplace groupId / basePackage / javaVersion.</li>
 *   <li>{@code @Order(40)} {@link com.mr486.generator.pipeline.processor.BatchConfigProcessor} — injecte les valeurs de {@code BatchOptions}.</li>
 *   <li>{@code @Order(50)} {@link com.mr486.generator.pipeline.processor.ResourceExpandProcessor} — dérive les services métier depuis {@code resources[]}.</li>
 *   <li>{@code @Order(60)} {@link com.mr486.generator.pipeline.processor.CrossCuttingConfigProcessor} — synchronise pom racine, docker-compose et routes du gateway.</li>
 * </ol>
 */
public interface FileProcessor {
    /**
     * Applique la transformation propre à ce processor.
     *
     * @param files fichiers en sortie de l'étape précédente
     * @param ctx   contexte immuable de la génération
     * @return liste transformée (peut être de taille différente)
     */
    List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx);
}
