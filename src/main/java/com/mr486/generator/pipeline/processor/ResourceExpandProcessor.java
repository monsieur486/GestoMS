package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dérive un service métier par entrée de {@code resources[]} en clonant le template {@code service-a/}
 * et en appliquant les substitutions nominales, le type de base et le type d'identifiant.
 *
 * <p>Si la requête ne contient pas de ressource, les services par défaut (service-a/b/c) sont conservés
 * tels quels. Sinon, les trois services par défaut sont retirés et remplacés par autant de copies
 * que d'entrées. Le sous-dossier {@code service-batch/} et {@code service-consumer/} présents en
 * tant que sous-projets de service-a sont volontairement exclus du clonage (ce sont des patches).
 *
 * <p>L'adaptation au type de base ({@link DbVariant}) et au type d'identifiant ({@link IdVariant})
 * est déléguée à des stratégies injectées : ajouter un type de base ou d'identifiant = ajouter une
 * implémentation, sans toucher à ce processor.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class ResourceExpandProcessor implements FileProcessor {

    /** Stratégies d'adaptation au type de base (Postgres, H2, Mongo). */
    private final List<DbVariant> dbVariants;

    /** Stratégies de substitution du type d'identifiant (Integer, UUID). */
    private final List<IdVariant> idVariants;

    /**
     * Applique l'expansion des ressources : clone {@code service-a/} pour chaque entrée de
     * {@code resources[]}, en substituant noms, types de base de données et types d'identifiants.
     *
     * @param files liste de fichiers à transformer
     * @param ctx   contexte de génération portant la requête
     * @return liste mise à jour avec les services générés
     */
    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();
        if (resources == null || resources.isEmpty()) {
            return files;
        }

        String root = ctx.getTargetRoot();
        List<GeneratedFile> serviceATemplate = extractServiceATemplate(files, root);
        List<GeneratedFile> filtered = removeDefaultServices(files, root);

        List<GeneratedFile> result = new ArrayList<>(filtered);
        for (ResourceModuleRequest res : resources) {
            result.addAll(generateService(serviceATemplate, res, root, ctx));
        }
        return result;
    }

    // Extrait les fichiers du template service-a/ en excluant les sous-projets patch (batch/consumer).
    private List<GeneratedFile> extractServiceATemplate(List<GeneratedFile> files, String root) {
        String serviceAPrefix = root + "/service-a/";
        String patchBatch    = serviceAPrefix + "service-batch/";
        String patchConsumer = serviceAPrefix + "service-consumer/";
        return files.stream()
            .filter(f -> f.path().startsWith(serviceAPrefix))
            .filter(f -> !f.path().startsWith(patchBatch))
            .filter(f -> !f.path().startsWith(patchConsumer))
            .collect(Collectors.toList());
    }

    // Retire les services par défaut (service-a/b/c) de la liste de fichiers.
    private List<GeneratedFile> removeDefaultServices(List<GeneratedFile> files, String root) {
        return files.stream()
            .filter(f -> !isDefaultService(f.path(), root))
            .collect(Collectors.toList());
    }

    // Vrai si le chemin appartient à un service par défaut (service-a/b/c).
    private boolean isDefaultService(String path, String root) {
        String rel = ProcessorUtils.relative(path, root);
        return rel.startsWith("service-a/")
            || rel.startsWith("service-b/")
            || rel.startsWith("service-c/");
    }

    // Clone le template pour une ressource : renommage de chemin, substitutions, base et identifiant.
    private List<GeneratedFile> generateService(List<GeneratedFile> template,
                                                ResourceModuleRequest res,
                                                String root,
                                                GenerationContext ctx) {
        String basePackage = ctx.getRequest().getBasePackage();
        List<GeneratedFile> generated = new ArrayList<>();
        for (GeneratedFile f : template) {
            String newPath    = transformPath(f.path(), res, root);
            byte[] newContent = transformContent(f.content(), res);
            newContent = applyDatabaseType(newPath, newContent, res, basePackage);
            if (newContent == null) {
                continue;  // file removed (e.g., changelog for Mongo)
            }
            newPath    = applyMongoPathRename(newPath, res);
            newContent = applyIdType(newPath, newContent, res);
            generated.add(new GeneratedFile(newPath, newContent, f.executable()));
        }
        return generated;
    }

    // Renomme le chemin (dossier, package, classes) vers les noms de la ressource.
    private String transformPath(String path, ResourceModuleRequest res, String root) {
        ResourceNaming n = ResourceNaming.from(res);
        // More specific replacements first
        return path
            .replace(root + "/service-a/", root + "/" + res.getServiceName() + "/")
            .replace("/servicea/", "/" + n.servicePackage() + "/")
            .replace("ServiceA", n.serviceClass())
            .replace("ResourceA", res.getClassName());
    }

    // En mode Mongo, déplace le package entity/ vers document/ dans le chemin.
    private String applyMongoPathRename(String path, ResourceModuleRequest res) {
        if (res.getDatabaseType() == DatabaseType.MONGO) {
            return path.replace("/entity/", "/document/");
        }
        return path;
    }

    // Substitutions nominales du template service-a vers la ressource (du plus spécifique au moins).
    private byte[] transformContent(byte[] content, ResourceModuleRequest res) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        ResourceNaming n = ResourceNaming.from(res);
        String text = new String(content, StandardCharsets.UTF_8)
            .replace("USER_SERVICE_A",    n.roleName())
            .replace("SERVICE_A",          n.scream())
            .replace("resources_a",        n.entityPlural())
            .replace("resource_a",         n.entityLower())
            .replace("/api/resources-a",   n.routePrefix())
            .replace("service-a",          res.getServiceName())
            .replace("service_a",          n.snake())
            .replace("servicea",           n.servicePackage())
            .replace("ServiceA",           n.serviceClass())
            .replace("ResourceA",          res.getClassName());
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Adapte le contenu d'un fichier au type de base demandé en déléguant à la {@link DbVariant}
     * correspondante ; {@code null} (base non précisée) ou type sans stratégie → contenu inchangé.
     *
     * <p><b>Exemple :</b> pour {@link DatabaseType#MONGO}, un changelog Liquibase donne {@code null}
     * (fichier supprimé par {@link #generateService}).
     *
     * @param path        chemin du fichier généré
     * @param content     contenu binaire du fichier
     * @param res         description de la ressource (contient le {@link DatabaseType})
     * @param basePackage package de base du projet généré
     * @return contenu transformé, ou {@code null} pour signaler la suppression du fichier
     */
    protected byte[] applyDatabaseType(String path, byte[] content,
                                       ResourceModuleRequest res, String basePackage) {
        DatabaseType db = res.getDatabaseType();
        if (db == null) {
            return content;
        }
        for (DbVariant v : dbVariants) {
            if (v.type() == db) {
                return v.apply(path, content, res, basePackage);
            }
        }
        return content;
    }

    /**
     * Substitue le type d'identifiant en déléguant à l'{@link IdVariant} correspondante.
     * Sans effet si {@code idType} est {@code null}/{@code LONG}, si la base est MongoDB, ou si aucun
     * variant ne correspond ({@code STRING}).
     *
     * <p><b>Exemple :</b> {@link IdType#UUID} substitue {@code Long id} par {@code UUID id} et ajoute
     * l'import {@code java.util.UUID}.
     *
     * @param path    chemin du fichier généré
     * @param content contenu binaire du fichier
     * @param res     description de la ressource (contient le {@link IdType})
     * @return contenu avec le type d'identifiant substitué
     */
    protected byte[] applyIdType(String path, byte[] content, ResourceModuleRequest res) {
        IdType idType = res.getIdType();
        if (idType == null || idType == IdType.LONG) {
            return content;
        }
        if (res.getDatabaseType() == DatabaseType.MONGO) {
            return content;
        }
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        for (IdVariant v : idVariants) {
            if (v.type() == idType) {
                return v.apply(text, path, res).getBytes(StandardCharsets.UTF_8);
            }
        }
        return content;
    }
}
