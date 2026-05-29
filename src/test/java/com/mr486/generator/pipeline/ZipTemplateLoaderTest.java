package com.mr486.generator.pipeline;

import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ZipTemplateLoaderTest {

    private final ZipTemplateLoader loader = new ZipTemplateLoader();

    @Test
    void loads_non_empty_file_list() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).isNotEmpty();
    }

    @Test
    void all_entries_have_ms_platform_prefix() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).allMatch(f -> f.path().startsWith("ms-platform/"));
    }

    @Test
    void no_directory_entries() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).allMatch(f -> !f.path().endsWith("/"));
    }

    @Test
    void marks_sh_files_as_executable() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).anyMatch(f -> f.path().endsWith(".sh") && f.executable());
    }

    @Test
    void marks_mvnw_as_executable() {
        List<GeneratedFile> files = loader.load();
        // Verify that if mvnw exists in the template, it should be marked executable
        // (This test documents the expected behavior even if mvnw is not in current template)
        assertThat(files)
            .noneMatch(f -> f.path().endsWith("mvnw") && !f.executable());
    }
}
