package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

/**
 * Vérifie que {@link RootRenameProcessor} renomme le répertoire racine
 * {@code ms-platform} en le nom choisi par l'utilisateur, préserve le contenu
 * et le flag exécutable, et est sans effet lorsque le nom par défaut est utilisé.
 */
class RootRenameProcessorTest {

    private final RootRenameProcessor processor = new RootRenameProcessor();

    @Test
    void renames_source_root_to_target_root() {
        GenerationContext ctx = ctxWithName("my-app");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/pom.xml", "<project/>"),
            file("ms-platform/docker-compose.yml", "version: '3'")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("my-app/service-a/pom.xml", "my-app/docker-compose.yml");
    }

    @Test
    void keeps_content_and_executable_flag_unchanged() {
        GenerationContext ctx = ctxWithName("x");
        List<GeneratedFile> input = List.of(file("ms-platform/go.sh", "#!/bin/bash", true));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).executable()).isTrue();
        assertThat(contentOf(result.get(0))).isEqualTo("#!/bin/bash");
    }

    @Test
    void is_noop_when_name_is_default() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = List.of(file("ms-platform/pom.xml", "content"));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).path()).isEqualTo("ms-platform/pom.xml");
    }
}
