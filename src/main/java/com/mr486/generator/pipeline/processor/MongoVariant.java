package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.ResourceModuleRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Stratégie de base MongoDB : remplace l'entité JPA par un {@code @Document}, le repository par un
 * {@code MongoRepository}, réécrit l'application.yml et le pom, repointe les autres sources Java
 * vers le package {@code document} et une clé {@code String}, et supprime les changelogs Liquibase.
 */
@Component
public class MongoVariant implements DbVariant {

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

    @Override
    public DatabaseType type() {
        return DatabaseType.MONGO;
    }

    // Le null retourné pour les changelogs est une sentinelle de suppression de fichier, pas une
    // collection — ReturnEmptyCollectionRatherThanNull est un faux positif ici (le type est byte[]).
    @Override
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    public byte[] apply(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        if (path.contains("/db/changelog/")) {
            return null;  // fichier supprimé (Liquibase inutile en Mongo)
        }
        if (path.endsWith("application.yml")) {
            return applicationYml(res);
        }
        if (path.endsWith("pom.xml")) {
            return pom(content);
        }
        return applyJava(path, content, res, basePackage);
    }

    // Sources Java : entité → document, repository → MongoRepository, autres → repointage entity/id.
    private byte[] applyJava(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (path.contains("/entity/") && path.endsWith(".java")) {
            return entity(res, basePackage);
        }
        if (path.contains("/repository/") && path.endsWith("Repository.java")) {
            return repository(res, basePackage);
        }
        if (path.endsWith(".java")) {
            return otherJava(content);
        }
        return content;
    }

    // application.yml MongoDB (URI + issuer JWT + Eureka).
    private byte[] applicationYml(ResourceModuleRequest res) {
        ResourceNaming n = ResourceNaming.from(res);
        String yml = MONGO_APP_YML_TEMPLATE
            .replace("{SERVICE_UPPER}", n.scream())
            .replace("{SERVICE_NAME}",  res.getServiceName())
            .replace("{SERVICE_SNAKE}", n.snake());
        return yml.getBytes(StandardCharsets.UTF_8);
    }

    // pom : dépendances JPA/Liquibase/Postgres → dépendances MongoDB + Mongock.
    private byte[] pom(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8)
            .replace(
                "<dependency><groupId>org.springframework.boot</groupId>"
                    + "<artifactId>spring-boot-starter-data-jpa</artifactId></dependency>",
                MONGO_POM_DEPS)
            .replace(
                "<dependency><groupId>org.liquibase</groupId><artifactId>liquibase-core</artifactId></dependency>", "")
            .replace(
                "<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>", "");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Entité JPA → document MongoDB (clé String).
    private byte[] entity(ResourceModuleRequest res, String basePackage) {
        ResourceNaming n = ResourceNaming.from(res);
        String doc = MONGO_ENTITY_TEMPLATE
            .replace("{PKG}", basePackage + "." + n.servicePackage())
            .replace("{COLLECTION}", n.entityPlural())
            .replace("{CLASS}", res.getClassName());
        return doc.getBytes(StandardCharsets.UTF_8);
    }

    // Repository JPA → MongoRepository.
    private byte[] repository(ResourceModuleRequest res, String basePackage) {
        ResourceNaming n = ResourceNaming.from(res);
        String repo = MONGO_REPO_TEMPLATE
            .replace("{PKG}", basePackage + "." + n.servicePackage())
            .replace("{CLASS}", res.getClassName());
        return repo.getBytes(StandardCharsets.UTF_8);
    }

    // Autres sources Java : repointe le package entity→document et la clé Long→String.
    private byte[] otherJava(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8)
            .replace(".entity.", ".document.")
            .replace("Long id", "String id");  // couvre champs, paramètres et variables de chemin
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
