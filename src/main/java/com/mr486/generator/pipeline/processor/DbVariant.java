package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.ResourceModuleRequest;

/**
 * Adapte le contenu d'un fichier généré à un type de base de données donné (stratégie par
 * {@link DatabaseType}).
 *
 * <p>Remplace le {@code switch} historique sur le type de base par du polymorphisme : ajouter un
 * type de base = ajouter une implémentation, sans toucher au {@link ResourceExpandProcessor}.
 */
public interface DbVariant {

    /**
     * Type de base de données pris en charge par cette stratégie.
     *
     * <p><b>Exemple :</b> {@code MongoVariant.type()} vaut {@link DatabaseType#MONGO}.
     *
     * @return le type de base géré
     */
    DatabaseType type();

    /**
     * Transforme le contenu d'un fichier pour ce type de base.
     *
     * <p><b>Exemple :</b> pour MongoDB, remplace l'entité JPA par un {@code @Document} et retourne
     * {@code null} sur un changelog Liquibase (fichier à supprimer).
     *
     * @param path        chemin du fichier généré
     * @param content     contenu binaire du fichier
     * @param res         description de la ressource
     * @param basePackage package de base du projet généré
     * @return contenu transformé, ou {@code null} pour signaler la suppression du fichier
     */
    byte[] apply(String path, byte[] content, ResourceModuleRequest res, String basePackage);
}
