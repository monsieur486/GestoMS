package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.resource;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.serviceAFiles;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.serviceAFilesWithServiceParams;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie la variante Mongo de {@link ResourceExpandProcessor} : répertoire {@code entity} renommé
 * en {@code document}, annotations et dépendances MongoDB, id {@code String} propagé (entité, dto,
 * service, paramètres de méthode), URI Mongo et retrait des changelogs — l'id est toujours
 * {@code String}, quel que soit l'{@code idType} demandé.
 */
class ResourceExpandMongoTest {

    private final ResourceExpandProcessor processor = ResourceExpandFixtures.processor();

    @Test
    void mongo_renames_entity_dir_to_document() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).anyMatch(f -> f.path().contains("/catalog/") && f.path().contains("/document/"));
        assertThat(result).noneMatch(f -> f.path().contains("/catalog/") && f.path().contains("/entity/"));
    }

    @Test
    void mongo_entity_uses_document_annotation() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile doc = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().contains("/document/Product.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(doc)).contains("@Document");
        assertThat(contentOf(doc)).doesNotContain("@Entity");
        assertThat(contentOf(doc)).contains("private String id");
    }

    @Test
    void mongo_repository_extends_MongoRepository() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile repo = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().endsWith("Repository.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(repo)).contains("MongoRepository");
        assertThat(contentOf(repo)).doesNotContain("JpaRepository");
    }

    @Test
    void mongo_pom_contains_mongodb_not_jpa() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile pom = result.stream()
            .filter(f -> f.path().endsWith("catalog/pom.xml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(pom)).contains("spring-boot-starter-data-mongodb");
        assertThat(contentOf(pom)).doesNotContain("spring-boot-starter-data-jpa");
        assertThat(contentOf(pom)).doesNotContain("postgresql");
    }

    @Test
    void mongo_application_yml_has_mongodb_uri_not_datasource() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile yml = result.stream()
            .filter(f -> f.path().endsWith("catalog/src/main/resources/application.yml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(yml)).contains("mongodb:");
        assertThat(contentOf(yml)).doesNotContain("datasource:");
    }

    @Test
    void mongo_application_yml_uses_valid_single_brace_placeholders() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile yml = result.stream()
            .filter(f -> f.path().endsWith("catalog/src/main/resources/application.yml"))
            .findFirst().orElseThrow();
        String s = contentOf(yml);
        // ${{...}} is an invalid Spring placeholder: it resolves the inner and leaves a
        // dangling brace (e.g. "port: 8080}"), which breaks startup.
        assertThat(s).doesNotContain("${{");
        assertThat(s).contains("port: ${CATALOG_PORT:8080}");
        assertThat(s).contains("${CATALOG_MONGO_URI:");
    }

    @Test
    void mongo_removes_db_changelog_files() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).noneMatch(f -> f.path().contains("/catalog/") && f.path().contains("/db/changelog/"));
    }

    @Test
    void mongo_service_imports_document_not_entity() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile svc = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().endsWith("ProductService.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(svc)).contains(".document.Product");
        assertThat(contentOf(svc)).doesNotContain(".entity.");
    }

    @Test
    void mongo_dto_uses_string_id_not_long() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile dto = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().endsWith("Dto.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(dto)).contains("private String id");
        assertThat(contentOf(dto)).doesNotContain("private Long id");
    }

    @Test
    void mongo_service_method_params_use_string_id_not_long() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFilesWithServiceParams("ms-platform"), ctx);
        GeneratedFile svc = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().endsWith("ProductService.java"))
            .findFirst().orElseThrow();
        String content = contentOf(svc);
        assertThat(content).as("findById param").contains("findById(String id)");
        assertThat(content).as("update param").contains("update(String id,");
        assertThat(content).as("delete param").contains("delete(String id)");
        assertThat(content).as("no Long id left").doesNotContain("Long id");
    }

    @Test
    void mongo_idType_is_always_string_regardless_of_request() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile doc = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().contains("/document/"))
            .findFirst().orElseThrow();
        assertThat(contentOf(doc)).contains("private String id");
        assertThat(contentOf(doc)).doesNotContain("private UUID id");
    }
}
