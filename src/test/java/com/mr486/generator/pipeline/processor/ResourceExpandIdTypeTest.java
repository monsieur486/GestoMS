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
 * Vérifie les variantes de type d'identifiant de {@link ResourceExpandProcessor} : Integer et UUID
 * substitués à Long dans l'entité, le repository, le dto, le SQL et les paramètres de méthode,
 * avec l'import {@code java.util.UUID} ajouté où nécessaire.
 */
class ResourceExpandIdTypeTest {

    private final ResourceExpandProcessor processor = ResourceExpandFixtures.processor();

    @Test
    void integer_replaces_Long_with_Integer_in_entity() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.INTEGER)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile entity = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().contains("/entity/"))
            .findFirst().orElseThrow();
        assertThat(contentOf(entity)).contains("private Integer id");
        assertThat(contentOf(entity)).doesNotContain("private Long id");
    }

    @Test
    void integer_replaces_Long_in_repository_generic() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.INTEGER)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile repo = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("Repository.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(repo)).contains("JpaRepository<Order, Integer>");
        assertThat(contentOf(repo)).doesNotContain("JpaRepository<Order, Long>");
    }

    @Test
    void uuid_adds_UUID_type_and_generation_strategy() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile entity = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().contains("/entity/"))
            .findFirst().orElseThrow();
        assertThat(contentOf(entity)).contains("private UUID id");
        assertThat(contentOf(entity)).contains("GenerationType.UUID");
        assertThat(contentOf(entity)).contains("import java.util.UUID");
    }

    @Test
    void uuid_adds_UUID_import_to_dto() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile dto = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("Dto.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(dto)).contains("private UUID id");
        assertThat(contentOf(dto)).contains("import java.util.UUID");
    }

    @Test
    void uuid_adds_UUID_import_to_repository() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile repo = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("Repository.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(repo)).contains("JpaRepository<Order, UUID>");
        assertThat(contentOf(repo)).contains("import java.util.UUID");
    }

    @Test
    void uuid_replaces_BIGINT_with_UUID_in_sql() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile sql = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("001-init.sql"))
            .findFirst().orElseThrow();
        assertThat(contentOf(sql)).contains("UUID DEFAULT gen_random_uuid() PRIMARY KEY");
        assertThat(contentOf(sql)).doesNotContain("BIGINT");
    }

    @Test
    void uuid_service_method_params_use_uuid_id_not_long() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFilesWithServiceParams("ms-platform"), ctx);
        GeneratedFile svc = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("OrderService.java"))
            .findFirst().orElseThrow();
        String content = contentOf(svc);
        assertThat(content).as("findById param").contains("findById(UUID id)");
        assertThat(content).as("update param").contains("update(UUID id,");
        assertThat(content).as("delete param").contains("delete(UUID id)");
        assertThat(content).as("no Long id left").doesNotContain("Long id");
        assertThat(content).as("UUID import").contains("import java.util.UUID");
    }
}
