package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.file;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.config.PlatformVersions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link VersionInjectionProcessor} réécrit les versions dans les {@code pom.xml}
 * (parent Spring Boot, spring-cloud, mongock, spring-boot-admin) uniquement quand la config
 * surcharge les défauts, et laisse {@code java.version} intact.
 */
class VersionInjectionPomTest {

    private final VersionInjectionProcessor processor =
        new VersionInjectionProcessor(new PlatformVersions());   // défauts = littéraux template

    private final GenerationContext ctx = defaultCtx();

    private String run(String path, String content) {
        List<GeneratedFile> out = processor.process(List.of(file(path, content)), ctx);
        return contentOf(out.get(0));
    }

    @Test
    void root_pom_parent_and_properties_are_noop_at_defaults() {
        String src = "<parent><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-starter-parent</artifactId><version>3.5.5</version><relativePath/></parent>"
            + "<properties><java.version>17</java.version>"
            + "<spring-cloud.version>2025.0.0</spring-cloud.version>"
            + "<mongock.version>5.5.1</mongock.version></properties>";
        String out = run("ms-platform/pom.xml", src);
        assertThat(out).isEqualTo(src);
    }

    @Test
    void root_pom_properties_rewritten_when_config_overridden() {
        PlatformVersions cfg = new PlatformVersions();
        cfg.setSpringBoot("3.6.0");
        cfg.setSpringCloud("2025.1.0");
        cfg.setMongock("5.6.0");
        VersionInjectionProcessor p = new VersionInjectionProcessor(cfg);
        String src = "<parent><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-starter-parent</artifactId><version>3.5.5</version><relativePath/></parent>"
            + "<spring-cloud.version>2025.0.0</spring-cloud.version>"
            + "<mongock.version>5.5.1</mongock.version>";
        String out = contentOf(p.process(List.of(file("ms-platform/pom.xml", src)), defaultCtx()).get(0));
        assertThat(out).contains("<artifactId>spring-boot-starter-parent</artifactId><version>3.6.0</version>");
        assertThat(out).contains("<spring-cloud.version>2025.1.0</spring-cloud.version>");
        assertThat(out).contains("<mongock.version>5.6.0</mongock.version>");
    }

    @Test
    void ms_admin_pom_spring_boot_admin_rewritten_when_overridden() {
        PlatformVersions cfg = new PlatformVersions();
        cfg.setSpringBootAdmin("3.6.0");
        VersionInjectionProcessor p = new VersionInjectionProcessor(cfg);
        String src = "<dependency><groupId>de.codecentric</groupId>"
            + "<artifactId>spring-boot-admin-starter-server</artifactId><version>3.5.5</version></dependency>";
        String out = contentOf(
            p.process(List.of(file("ms-platform/ms-admin/pom.xml", src)), defaultCtx()).get(0));
        assertThat(out).contains(
            "<artifactId>spring-boot-admin-starter-server</artifactId><version>3.6.0</version>");
    }

    @Test
    void java_version_property_is_left_untouched() {
        String out = run("ms-platform/pom.xml", "<properties><java.version>21</java.version></properties>");
        assertThat(out).contains("<java.version>21</java.version>");
    }
}
