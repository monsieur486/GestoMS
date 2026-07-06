package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;

/**
 * Substitue le type d'identifiant de l'entité générée (stratégie par {@link IdType}).
 *
 * <p>Remplace le {@code switch} historique sur le type d'identifiant par du polymorphisme : ajouter
 * un type = ajouter une implémentation. Les types sans transformation (LONG, STRING) n'ont pas de
 * stratégie — {@link ResourceExpandProcessor} les laisse alors inchangés.
 */
public interface IdVariant {

    /**
     * Type d'identifiant pris en charge par cette stratégie.
     *
     * <p><b>Exemple :</b> {@code UuidIdVariant.type()} vaut {@link IdType#UUID}.
     *
     * @return le type d'identifiant géré
     */
    IdType type();

    /**
     * Substitue le type d'identifiant dans le contenu textuel d'un fichier.
     *
     * <p><b>Exemple :</b> pour UUID, remplace {@code private Long id} par {@code private UUID id}
     * et ajoute l'import {@code java.util.UUID}.
     *
     * @param text contenu textuel du fichier
     * @param path chemin du fichier (pour cibler les sources SQL/Java)
     * @param res  description de la ressource
     * @return contenu avec le type d'identifiant substitué
     */
    String apply(String text, String path, ResourceModuleRequest res);
}
