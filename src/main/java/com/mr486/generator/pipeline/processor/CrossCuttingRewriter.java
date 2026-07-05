package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;

/**
 * Réécrit un fichier transverse (pom racine, docker-compose, gateway YAML, realm Keycloak…)
 * pour refléter l'ensemble final des services d'une génération.
 *
 * <p>Chaque implémentation est un bean Spring cohérent (un fichier transverse = un rewriter) que
 * {@link CrossCuttingConfigProcessor} interroge dans l'ordre : le premier dont {@link #handles}
 * répond {@code true} traite le fichier. Ajouter un fichier transverse = ajouter un bean, sans
 * toucher au dispatcher.
 */
public interface CrossCuttingRewriter {

    /**
     * Indique si ce rewriter prend en charge le fichier donné dans ce contexte.
     *
     * <p><b>Exemple :</b> le rewriter du pom racine répond {@code true} pour
     * {@code <racine>/pom.xml} et {@code false} pour tout autre chemin.
     *
     * @param f   fichier généré candidat
     * @param ctx contexte de génération
     * @return {@code true} si {@link #rewrite} doit être appelé pour ce fichier
     */
    boolean handles(GeneratedFile f, GenerationContext ctx);

    /**
     * Réécrit le fichier pris en charge.
     *
     * <p><b>Exemple :</b> le rewriter du pom racine remplace le bloc {@code <modules>} par la liste
     * des modules effectivement générés.
     *
     * @param f   fichier généré à réécrire (garanti pris en charge)
     * @param ctx contexte de génération
     * @return le fichier réécrit
     */
    GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx);

    /**
     * Indique si la requête définit au moins une ressource métier dynamique.
     *
     * <p><b>Exemple :</b> {@code hasResources(ctx)} vaut {@code false} pour un corps vide
     * (services service-a/b/c par défaut) et {@code true} dès qu'un {@code resources[]} est fourni.
     *
     * @param ctx contexte de génération
     * @return {@code true} si {@code resources[]} est non vide
     */
    static boolean hasResources(GenerationContext ctx) {
        List<ResourceModuleRequest> r = ctx.getRequest().getResources();
        return r != null && !r.isEmpty();
    }
}
