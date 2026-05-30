package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dérive un service métier par entrée de {@code resources[]} en clonant le template {@code service-a/}
 * et en appliquant les substitutions nominales, le type de base et le type d'identifiant.
 * <p>
 * Si la requête ne contient pas de ressource, les services par défaut (service-a/b/c) sont conservés
 * tels quels. Sinon, les trois services par défaut sont retirés et remplacés par autant de copies
 * que d'entrées. Le sous-dossier {@code service-batch/} et {@code service-consumer/} présents en
 * tant que sous-projets de service-a sont volontairement exclus du clonage (ce sont des patches).
 * <p>
 * Mode H2 : retire la clause {@code ON CONFLICT(...) DO NOTHING} du seed SQL (incompatible H2),
 * configure le datasource en mémoire et active la console.
 * Mode MongoDB : remplace l'entité JPA par un {@code @Document}, supprime les fichiers Liquibase,
 * ajuste le pom et l'application.yml.
 * Type d'identifiant UUID : substitue {@code Long}/{@code BIGINT IDENTITY} par {@code UUID}/{@code gen_random_uuid()}.
 */
@Component
@Order(50)
public class ResourceExpandProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();
        if (resources == null || resources.isEmpty()) return files;

        String root = ctx.getTargetRoot();
        List<GeneratedFile> serviceATemplate = extractServiceATemplate(files, root);
        List<GeneratedFile> filtered = removeDefaultServices(files, root);

        List<GeneratedFile> result = new ArrayList<>(filtered);
        for (ResourceModuleRequest res : resources) {
            result.addAll(generateService(serviceATemplate, res, root, ctx));
        }
        return result;
    }

    // ── Extract service-a template (excluding patch subdirs) ──────────────────

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
        String rel = relative(path, root);
        return rel.startsWith("service-a/")
            || rel.startsWith("service-b/")
            || rel.startsWith("service-c/");
    }

    // ── Generate one service ──────────────────────────────────────────────────

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
            if (newContent == null) continue;  // file removed (e.g., changelog for Mongo)
            newPath    = applyMongoPathRename(newPath, res);
            newContent = applyIdType(newPath, newContent, res);
            generated.add(new GeneratedFile(newPath, newContent, f.executable()));
        }
        return generated;
    }

    // ── Path transformations ──────────────────────────────────────────────────

    private String transformPath(String path, ResourceModuleRequest res, String root) {
        String serviceClass   = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        // More specific replacements first
        path = path.replace(root + "/service-a/", root + "/" + res.getServiceName() + "/");
        path = path.replace("/servicea/", "/" + servicePackage + "/");
        path = path.replace("ServiceA", serviceClass);
        path = path.replace("ResourceA", res.getClassName());
        return path;
    }

    private String applyMongoPathRename(String path, ResourceModuleRequest res) {
        if (res.getDatabaseType() == DatabaseType.MONGO) {
            path = path.replace("/entity/", "/document/");
        }
        return path;
    }

    // ── Content transformations ───────────────────────────────────────────────

    private byte[] transformContent(byte[] content, ResourceModuleRequest res) {
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        text = applyBaseReplacements(text, res);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String applyBaseReplacements(String text, ResourceModuleRequest res) {
        String serviceClass   = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        String serviceSnake   = res.getServiceName().replace("-", "_");
        String serviceScream  = serviceSnake.toUpperCase();
        String entityPlural   = res.getClassName().toLowerCase() + "s";
        String entityLower    = res.getClassName().toLowerCase();
        String routePrefix    = (res.getRoutePrefix() == null || res.getRoutePrefix().isBlank())
                                ? "/api/" + entityPlural
                                : res.getRoutePrefix();

        // Longest/most-specific first
        text = text.replace("USER_SERVICE_A",    "USER_" + serviceScream);
        text = text.replace("SERVICE_A",          serviceScream);
        text = text.replace("resources_a",        entityPlural);
        text = text.replace("resource_a",         entityLower);
        text = text.replace("/api/resources-a",   routePrefix);
        text = text.replace("service-a",          res.getServiceName());
        text = text.replace("service_a",          serviceSnake);
        text = text.replace("servicea",           servicePackage);
        text = text.replace("ServiceA",           serviceClass);
        text = text.replace("ResourceA",          res.getClassName());
        return text;
    }

    // ── DatabaseType (Tasks 8 and 9) ──────────────────────────────────────────

    protected byte[] applyDatabaseType(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (res.getDatabaseType() == null || res.getDatabaseType() == DatabaseType.POSTGRES) return content;
        if (res.getDatabaseType() == DatabaseType.H2)    return applyH2(path, content, res);
        if (res.getDatabaseType() == DatabaseType.MONGO) return applyMongo(path, content, res, basePackage);
        return content;
    }

    private byte[] applyH2(String path, byte[] content, ResourceModuleRequest res) {
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        String dbName = res.getServiceName().replace("-", "") + "db";

        if (path.endsWith("pom.xml")) {
            text = text.replace(
                "<groupId>org.postgresql</groupId><artifactId>postgresql</artifactId>",
                "<groupId>com.h2database</groupId><artifactId>h2</artifactId>"
            );
        }
        if (path.endsWith("application.yml")) {
            String serviceSnake = res.getServiceName().replace("-", "_").toUpperCase();
            text = text.replace(
                "url: ${" + serviceSnake + "_DATASOURCE_URL:jdbc:postgresql://localhost:5432/" + res.getServiceName().replace("-","_") + "_db}",
                "url: jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            );
            text = text.replace(
                "username: ${" + serviceSnake + "_DB_USERNAME:" + res.getServiceName().replace("-","_") + "}",
                "username: sa"
            );
            text = text.replace(
                "password: ${" + serviceSnake + "_DB_PASSWORD:" + res.getServiceName().replace("-","_") + "}",
                "password:"
            );
            text = text.replace("driver-class-name: org.postgresql.Driver", "driver-class-name: org.h2.Driver");
            // Add H2 console config after datasource block
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

    // ── MongoDB templates ─────────────────────────────────────────────────────

    private static final String MONGO_ENTITY_TEMPLATE =
        "package {PKG}.document;\n" +
        "import lombok.*;\n" +
        "import org.springframework.data.annotation.Id;\n" +
        "import org.springframework.data.mongodb.core.mapping.Document;\n" +
        "@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder\n" +
        "@Document(collection=\"{COLLECTION}\")\n" +
        "public class {CLASS} { @Id private String id; private String name; private String description; }";

    private static final String MONGO_REPO_TEMPLATE =
        "package {PKG}.repository;\n" +
        "import {PKG}.document.{CLASS};\n" +
        "import org.springframework.stereotype.Repository;\n" +
        "import org.springframework.data.mongodb.repository.MongoRepository;\n" +
        "@Repository\n" +
        "public interface {CLASS}Repository extends MongoRepository<{CLASS}, String> {}";

    private static final String MONGO_APP_YML_TEMPLATE =
        "server:\n" +
        "  port: ${{{SERVICE_UPPER}_PORT:8080}}\n" +
        "spring:\n" +
        "  application:\n" +
        "    name: {SERVICE_NAME}\n" +
        "  data:\n" +
        "    mongodb:\n" +
        "      uri: ${{{SERVICE_UPPER}_MONGO_URI:mongodb://{SERVICE_SNAKE}:{SERVICE_SNAKE}@localhost:27017/{SERVICE_SNAKE}_db?authSource=admin}}\n" +
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
        "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>" +
        "<dependency><groupId>io.mongock</groupId><artifactId>mongock-springboot-v3</artifactId></dependency>" +
        "<dependency><groupId>io.mongock</groupId><artifactId>mongodb-springdata-v4-driver</artifactId></dependency>";

    private byte[] applyMongo(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        String servicePackage = toConcatLower(res.getServiceName());
        String serviceSnake   = res.getServiceName().replace("-", "_");
        String serviceUpper   = serviceSnake.toUpperCase();
        String collection     = res.getClassName().toLowerCase() + "s";
        String pkg            = basePackage + "." + servicePackage;

        // Remove Liquibase changelog files — return null signals generateService to skip
        if (path.contains("/db/changelog/")) return null;

        if (path.endsWith("application.yml")) {
            String yml = MONGO_APP_YML_TEMPLATE
                .replace("{SERVICE_UPPER}", serviceUpper)
                .replace("{SERVICE_NAME}",  res.getServiceName())
                .replace("{SERVICE_SNAKE}", serviceSnake);
            return yml.getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("pom.xml") && !containsNullByte(content)) {
            String text = new String(content, StandardCharsets.UTF_8);
            text = text.replace(
                "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>",
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
        if (containsNullByte(content)) return content;
        return new String(content, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
    }

    // ── IdType (Task 10) ──────────────────────────────────────────────────────

    protected byte[] applyIdType(String path, byte[] content, ResourceModuleRequest res) {
        if (res.getIdType() == null || res.getIdType() == IdType.LONG) return content;
        // MONGO always uses String id — idType ignored
        if (res.getDatabaseType() == DatabaseType.MONGO) return content;
        if (containsNullByte(content)) return content;

        String text = new String(content, StandardCharsets.UTF_8);
        if (res.getIdType() == IdType.INTEGER) text = applyIntegerType(text, res);
        if (res.getIdType() == IdType.UUID)    text = applyUuidType(text, path, res);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String applyIntegerType(String text, ResourceModuleRequest res) {
        text = text.replace("private Long id", "private Integer id");
        text = text.replace("JpaRepository<" + res.getClassName() + ",Long>",
                            "JpaRepository<" + res.getClassName() + ",Integer>");
        return text;
    }

    private String applyUuidType(String text, String path, ResourceModuleRequest res) {
        // Entity
        text = text.replace(
            "@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id",
            "@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id");
        // Add UUID import if not already present
        if (text.contains("private UUID id") && !text.contains("import java.util.UUID")) {
            text = text.replace("import jakarta.persistence.*;", "import jakarta.persistence.*;\nimport java.util.UUID;");
        }
        // Repository generic
        text = text.replace("JpaRepository<" + res.getClassName() + ",Long>",
                            "JpaRepository<" + res.getClassName() + ",UUID>");
        // DTO
        text = text.replace("private Long id", "private UUID id");
        // SQL: BIGINT GENERATED BY DEFAULT AS IDENTITY → UUID DEFAULT gen_random_uuid()
        if (path.endsWith(".sql")) {
            text = text.replace(
                "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY",
                "UUID DEFAULT gen_random_uuid() PRIMARY KEY");
        }
        return text;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    protected String toPascalCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("[-_]")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    protected String toConcatLower(String kebab) {
        return kebab.replace("-", "").replace("_", "").toLowerCase();
    }

    protected String relative(String path, String root) {
        String prefix = root + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    protected boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
