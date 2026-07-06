package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.resource;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.serviceAFiles;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie la variante H2 de {@link ResourceExpandProcessor} : driver et datasource H2 substitués,
 * clause {@code ON CONFLICT} retirée du SQL de seed (non supportée par H2).
 */
class ResourceExpandH2Test {

    private final ResourceExpandProcessor processor = ResourceExpandFixtures.processor();

    @Test
    void h2_replaces_postgres_driver_in_pom() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("inventory", "Item", "/api/items", DatabaseType.H2, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile pom = result.stream()
            .filter(f -> f.path().endsWith("inventory/pom.xml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(pom)).contains("h2");
        assertThat(contentOf(pom)).doesNotContain("postgresql");
    }

    @Test
    void h2_replaces_datasource_in_application_yml() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("inventory", "Item", "/api/items", DatabaseType.H2, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile yml = result.stream()
            .filter(f -> f.path().endsWith("inventory/src/main/resources/application.yml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(yml)).contains("jdbc:h2:mem:");
        assertThat(contentOf(yml)).contains("org.h2.Driver");
        assertThat(contentOf(yml)).doesNotContain("org.postgresql.Driver");
    }

    @Test
    void h2_strips_on_conflict_clause_from_seed_sql() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("inventory", "Item", "/api/items", DatabaseType.H2, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile seed = result.stream()
            .filter(f -> f.path().endsWith("inventory/src/main/resources/db/changelog/002-seed.sql"))
            .findFirst().orElseThrow();
        assertThat(contentOf(seed)).doesNotContain("ON CONFLICT");
        assertThat(contentOf(seed)).contains("INSERT INTO items");
        assertThat(contentOf(seed)).endsWith(";");
    }
}
