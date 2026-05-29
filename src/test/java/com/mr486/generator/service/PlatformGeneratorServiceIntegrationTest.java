package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlatformGeneratorServiceIntegrationTest {

    @Autowired
    PlatformGeneratorService service;

    @Test
    void generates_non_empty_output() {
        List<GeneratedFile> files = service.generate(new PlatformGenerationRequest());
        assertThat(files).isNotEmpty();
    }

    @Test
    void renames_root_when_name_provided() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-project");
        List<GeneratedFile> files = service.generate(req);
        assertThat(files).allMatch(f -> f.path().startsWith("my-project/"));
        assertThat(files).noneMatch(f -> f.path().startsWith("ms-platform/"));
    }
}
