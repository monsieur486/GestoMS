package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Synchronise les fichiers transverses qui référencent les services par leur nom (pom racine,
 * docker-compose, routes du gateway, realm Keycloak, test-all.sh, README, AggregateController,
 * catalogue ms-webui).
 *
 * <p>Ne porte plus la logique : il agit en <b>dispatcher</b> sur les {@link CrossCuttingRewriter}
 * injectés (un par fichier transverse). Pour chaque fichier, le premier rewriter dont
 * {@link CrossCuttingRewriter#handles handles} répond {@code true} le réécrit ; les autres fichiers
 * passent inchangés.
 *
 * <p>Tourne en {@link Order @Order(60)} : après que {@link FeatureFilterProcessor} et
 * {@link ResourceExpandProcessor} aient déterminé l'ensemble final des services.
 */
@Component
@Order(60)
@RequiredArgsConstructor
public class CrossCuttingConfigProcessor implements FileProcessor {

    /** Rewriters injectés (un par fichier transverse) ; le premier qui prend en charge gagne. */
    private final List<CrossCuttingRewriter> rewriters;

    /**
     * Point d'entrée du pipeline : applique à chaque fichier le rewriter qui le prend en charge.
     *
     * @param files liste des fichiers générés en entrée
     * @param ctx   contexte de génération contenant la requête et le répertoire cible
     * @return liste des fichiers avec les fichiers transverses réécrits
     */
    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        List<GeneratedFile> result = new ArrayList<>(files.size());
        for (GeneratedFile f : files) {
            result.add(dispatch(f, ctx));
        }
        return result;
    }

    // Confie le fichier au premier rewriter qui le prend en charge, sinon le laisse inchangé.
    private GeneratedFile dispatch(GeneratedFile f, GenerationContext ctx) {
        for (CrossCuttingRewriter r : rewriters) {
            if (r.handles(f, ctx)) {
                return r.rewrite(f, ctx);
            }
        }
        return f;
    }
}
