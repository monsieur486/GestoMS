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
import java.util.Locale;
import java.util.stream.Collectors;
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
 * <p>Mode H2 : retire la clause {@code ON CONFLICT(...) DO NOTHING} du seed SQL (incompatible H2),
 * configure le datasource en mémoire et active la console.
 * Mode MongoDB : remplace l'entité JPA par un {@code @Document}, supprime les fichiers Liquibase,
 * ajuste le pom et l'application.yml.
 * Type d'identifiant UUID : substitue {@code Long}/{@code BIGINT IDENTITY} par
 * {@code UUID}/{@code gen_random_uuid()}.
 */
@Component
@Order(50)
public class ResourceExpandProcessor implements FileProcessor {

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

    // ── Extract service-a template (excluding patch subdirs) ──────────────

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

    private List<GeneratedFile> removeDefaultServices(List<GeneratedFile> files, String root) {
        return files.stream()
            .filter(f -> !isDefaultService(f.path(), root))
            .collect(Collectors.toList());
    }

    private boolean isDefaultService(String path, String root) {
        String rel = ProcessorUtils.relative(path, root);
        return rel.startsWith("service-a/")
            || rel.startsWith("service-b/")
            || rel.startsWith("service-c/");
    }

    // ── Generate one service ──────────────────────────────────────────────

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

    // ── Path transformations ──────────────────────────────────────────────

    private String transformPath(String path, ResourceModuleRequest res, String root) {
        ResourceNaming n = ResourceNaming.from(res);
        // More specific replacements first
        path = path.replace(root + "/service-a/", root + "/" + res.getServiceName() + "/");
        path = path.replace("/servicea/", "/" + n.servicePackage() + "/");
        path = path.replace("ServiceA", n.serviceClass());
        path = path.replace("ResourceA", res.getClassName());
        return path;
    }

    private String applyMongoPathRename(String path, ResourceModuleRequest res) {
        if (res.getDatabaseType() == DatabaseType.MONGO) {
            path = path.replace("/entity/", "/document/");
        }
        return path;
    }

    // ── Content transformations ───────────────────────────────────────────

    private byte[] transformContent(byte[] content, ResourceModuleRequest res) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        text = applyBaseReplacements(text, res);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String applyBaseReplacements(String text, ResourceModuleRequest res) {
        final ResourceNaming n = ResourceNaming.from(res);

        // Longest/most-specific first
        text = text.replace("USER_SERVICE_A",    n.roleName());
        text = text.replace("SERVICE_A",          n.scream());
        text = text.replace("resources_a",        n.entityPlural());
        text = text.replace("resource_a",         n.entityLower());
        text = text.replace("/api/resources-a",   n.routePrefix());
        text = text.replace("service-a",          res.getServiceName());
        text = text.replace("service_a",          n.snake());
        text = text.replace("servicea",           n.servicePackage());
        text = text.replace("ServiceA",           n.serviceClass());
        text = text.replace("ResourceA",          res.getClassName());
        return text;
    }

    // ── DatabaseType (Tasks 8 and 9) ──────────────────────────────────────

    /**
     * Adapte le contenu d'un fichier au type de base de données demandé (H2 ou MongoDB).
     * Retourne {@code null} si le fichier doit être supprimé (ex. : changelog Liquibase pour Mongo).
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
        return switch (db) {
            case POSTGRES -> content;
            case H2       -> applyH2(path, content, res);
            case MONGO    -> applyMongo(path, content, res, basePackage);
        };
    }

    private byte[] applyH2(String path, byte[] content, ResourceModuleRequest res) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        String dbName    = res.getServiceName().replace("-", "") + "db";
        String snake     = res.getServiceName().replace("-", "_");
        String snakeUp   = snake.toUpperCase(Locale.ROOT);

        if (path.endsWith("pom.xml")) {
            text = text.replace(
                "<groupId>org.postgresql</groupId><artifactId>postgresql</artifactId>",
                "<groupId>com.h2database</groupId><artifactId>h2</artifactId>"
            );
        }
        if (path.endsWith("application.yml")) {
            text = text.replace(
                "url: ${" + snakeUp + "_DATASOURCE_URL:jdbc:postgresql://localhost:5432/" + snake + "_db}",
                "url: jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            );
            text = text.replace(
                "username: ${" + snakeUp + "_DB_USERNAME:" + snake + "}",
                "username: sa"
            );
            text = text.replace(
                "password: ${" + snakeUp + "_DB_PASSWORD:" + snake + "}",
                "password:"
            );
            text = text.replace("driver-class-name: org.postgresql.Driver", "driver-class-name: org.h2.Driver");
            if (!text.contains("h2:") && text.contains("driver-class-name: org.h2.Driver")) {
                text = text.replace("driver-class-name: org.h2.Driver",
                    "driver-class-name: org.h2.Driver\n  h2:\n    console:\n      enabled: true");
            }
        }
        if (path.contains("/db/changelog/") && path.endsWith(".sql")) {
            // H2 (even with MODE=PostgreSQL) does not support ON CONFLICT — Liquibase changeset
            // history already prevents duplicate runs, so the clause is safe to drop.
            text = text.replaceAll(" ON CONFLICT\\([^)]+\\) DO NOTHING", "");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ── MongoDB templates ─────────────────────────────────────────────────

    private static final String MONGO_ENTITY_TEMPLATE = """
        package {PKG}.document;

        import lombok.*;
        import org.springframework.data.annotation.Id;
        import org.springframework.data.mongodb.core.mapping.Document;

        /**
         * Document MongoDB de la ressource {@code {CLASS}} (collection {@code {COLLECTION}}).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Document(collection = "{COLLECTION}")
        public class {CLASS} {

            @Id
            private String id;

            private String name;
            private String description;
        }
        """;

    private static final String MONGO_REPO_TEMPLATE = """
        package {PKG}.repository;

        import {PKG}.document.{CLASS};
        import org.springframework.data.mongodb.repository.MongoRepository;
        import org.springframework.stereotype.Repository;

        /**
         * Repository MongoDB du document {@code {CLASS}} (clé {@code String}).
         */
        @Repository
        public interface {CLASS}Repository extends MongoRepository<{CLASS}, String> {
        }
        """;

    private static final String MONGO_APP_YML_TEMPLATE =
        "server:\n" +
        "  port: ${{SERVICE_UPPER}_PORT:8080}\n" +
        "spring:\n" +
        "  application:\n" +
        "    name: {SERVICE_NAME}\n" +
        "  data:\n" +
        "    mongodb:\n" +
        "      uri: ${{SERVICE_UPPER}_MONGO_URI:mongodb://{SERVICE_SNAKE}:{SERVICE_SNAKE}" +
        "@localhost:27017/{SERVICE_SNAKE}_db?authSource=admin}\n" +
        "  security:\n" +
        "    oauth2:\n" +
        "      resourceserver:\n" +
        "        jwt:\n" +
        "          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8089/realms/ms-realm}\n" +
        "          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://keycloak:8080/realms/ms-realm/protocol/openid-connect/certs}\n" +
        "eureka:\n" +
        "  client:\n" +
        "    service-url:\n" +
        "      defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}\n" +
        "management:\n" +
        "  endpoints:\n" +
        "    web:\n" +
        "      exposure:\n" +
        "        include: health,info\n";

    private static final String MONGO_POM_DEPS =
        "<dependency><groupId>org.springframework.boot</groupId>" +
        "<artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>\n" +
        "    <dependency><groupId>io.mongock</groupId><artifactId>mongock-springboot-v3</artifactId></dependency>\n" +
        "    <dependency><groupId>io.mongock</groupId>" +
        "<artifactId>mongodb-springdata-v4-driver</artifactId></dependency>";

    private byte[] applyMongo(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        final String servicePackage = ResourceNaming.from(res).servicePackage();
        final String serviceSnake   = res.getServiceName().replace("-", "_");
        final String serviceUpper   = serviceSnake.toUpperCase(Locale.ROOT);
        final String collection     = res.getClassName().toLowerCase(Locale.ROOT) + "s";
        final String pkg            = basePackage + "." + servicePackage;

        // Remove Liquibase changelog files — return null signals generateService to skip
        if (path.contains("/db/changelog/")) {
            return null;
        }

        if (path.endsWith("application.yml")) {
            String yml = MONGO_APP_YML_TEMPLATE
                .replace("{SERVICE_UPPER}", serviceUpper)
                .replace("{SERVICE_NAME}",  res.getServiceName())
                .replace("{SERVICE_SNAKE}", serviceSnake);
            return yml.getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("pom.xml") && !ProcessorUtils.containsNullByte(content)) {
            String text = new String(content, StandardCharsets.UTF_8);
            text = text.replace(
                "<dependency><groupId>org.springframework.boot</groupId>"
                    + "<artifactId>spring-boot-starter-data-jpa</artifactId></dependency>",
                MONGO_POM_DEPS);
            text = text.replace(
                "<dependency><groupId>org.liquibase</groupId><artifactId>liquibase-core</artifactId></dependency>", "");
            text = text.replace(
                "<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>", "");
            return text.getBytes(StandardCharsets.UTF_8);
        }
        // Entity file → MongoDB document
        if (path.contains("/entity/") && path.endsWith(".java")) {
            String doc = MONGO_ENTITY_TEMPLATE
                .replace("{PKG}", pkg)
                .replace("{COLLECTION}", collection)
                .replace("{CLASS}", res.getClassName());
            return doc.getBytes(StandardCharsets.UTF_8);
        }
        // Repository file → MongoRepository
        if (path.contains("/repository/") && path.endsWith("Repository.java")) {
            String repo = MONGO_REPO_TEMPLATE
                .replace("{PKG}", pkg)
                .replace("{CLASS}", res.getClassName());
            return repo.getBytes(StandardCharsets.UTF_8);
        }
        // Remaining Java sources (service, DTO, …) reference the entity by its old package
        // and Long id; for Mongo the entity lives in the document package and is String-keyed.
        if (path.endsWith(".java")) {
            String text = new String(content, StandardCharsets.UTF_8)
                .replace(".entity.", ".document.")
                .replace("Long id", "String id");  // covers fields, method params and path vars
            return text.getBytes(StandardCharsets.UTF_8);
        }
        return content;
    }

    // ── IdType (Task 10) ──────────────────────────────────────────────────

    /**
     * Adapte le type d'identifiant de l'entité générée (UUID ou Integer).
     * Sans effet si {@code idType} est {@code null}, {@code LONG} ou si la base est MongoDB
     * (déjà gérée par {@link #applyDatabaseType}).
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
        String result = switch (idType) {
            case INTEGER -> applyIntegerType(text, res);
            case UUID    -> applyUuidType(text, path, res);
            case LONG    -> text;   // unreachable — handled above; forces exhaustiveness check
            case STRING  -> text;   // SQL resource with STRING id: no-op (Mongo guard is above)
        };
        return result.getBytes(StandardCharsets.UTF_8);
    }

    private String applyIntegerType(String text, ResourceModuleRequest res) {
        text = text.replace("private Long id", "private Integer id");
        text = text.replace("JpaRepository<" + res.getClassName() + ", Long>",
                            "JpaRepository<" + res.getClassName() + ", Integer>");
        return text;
    }

    private String applyUuidType(String text, String path, ResourceModuleRequest res) {
        // Entity
        text = text.replace(
            "@Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id",
            "@Id\n    @GeneratedValue(strategy = GenerationType.UUID)\n    private UUID id");
        // Repository generic (reformatted templates use the spaced form)
        text = text.replace("JpaRepository<" + res.getClassName() + ", Long>",
                            "JpaRepository<" + res.getClassName() + ", UUID>");
        // DTO, service method params and controller path vars
        text = text.replace("Long id", "UUID id");
        // SQL: BIGINT GENERATED BY DEFAULT AS IDENTITY → UUID DEFAULT gen_random_uuid()
        if (path.endsWith(".sql")) {
            text = text.replace(
                "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY",
                "UUID DEFAULT gen_random_uuid() PRIMARY KEY");
        }
        // Any Java source now referencing UUID (entity, DTO, repository) needs the import.
        if (path.endsWith(".java") && text.contains("UUID") && !text.contains("import java.util.UUID")) {
            text = text.replaceFirst("(package [^;]+;\n)", "$1import java.util.UUID;\n");
        }
        return text;
    }

}
