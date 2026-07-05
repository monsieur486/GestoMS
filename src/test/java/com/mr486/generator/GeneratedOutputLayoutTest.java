package com.mr486.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.service.PlatformGeneratorService;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vérifie que la sortie générée respecte les contraintes de mise en forme Java :
 * aucune ligne ne dépasse 120 caractères, pas d'imports collés, et que les
 * variantes UUID et Mongo sont correctement appliquées après reformatage.
 */
@SpringBootTest
class GeneratedOutputLayoutTest {

    @Autowired
    PlatformGeneratorService service;

    private ResourceModuleRequest resource(String name, String cls, DatabaseType db, IdType id) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(name);
        r.setClassName(cls);
        r.setDatabaseType(db);
        r.setIdType(id);
        return r;
    }

    private List<GeneratedFile> generateWithResources() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(
            resource("service-widget", "Widget", DatabaseType.POSTGRES, IdType.UUID),
            resource("service-gadget", "Gadget", DatabaseType.MONGO, IdType.LONG)
        ));
        return service.generate(req);
    }

    @Test
    void no_java_line_exceeds_120_chars_and_no_glued_imports() {
        List<GeneratedFile> files = generateWithResources();
        for (GeneratedFile f : files) {
            if (!f.path().endsWith(".java")) {
                continue;
            }
            String content = new String(f.content(), StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                assertThat(lines[i].length())
                    .as("ligne %d de %s dépasse 120 caractères", i + 1, f.path())
                    .isLessThanOrEqualTo(120);
            }
            assertThat(content)
                .as("imports collés dans %s", f.path())
                .doesNotContain(";import ");
        }
    }

    @Test
    void uuid_variant_transform_still_applies_after_reformat() {
        List<GeneratedFile> files = generateWithResources();
        GeneratedFile entity = files.stream()
            .filter(f -> f.path().endsWith("/entity/Widget.java"))
            .findFirst().orElseThrow();
        String content = new String(entity.content(), StandardCharsets.UTF_8);
        assertThat(content).contains("private UUID id");
        assertThat(content).contains("import java.util.UUID");
        assertThat(content).doesNotContain("private Long id");
    }

    @Test
    void mongo_variant_transform_still_applies_after_reformat() {
        List<GeneratedFile> files = generateWithResources();
        GeneratedFile doc = files.stream()
            .filter(f -> f.path().endsWith("/document/Gadget.java"))
            .findFirst().orElseThrow();
        String content = new String(doc.content(), StandardCharsets.UTF_8);
        assertThat(content).contains("@Document");
        assertThat(content).contains("private String id");
    }
}
