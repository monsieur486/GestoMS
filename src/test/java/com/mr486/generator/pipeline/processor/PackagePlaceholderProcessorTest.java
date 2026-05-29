package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class PackagePlaceholderProcessorTest {

    private final PackagePlaceholderProcessor processor = new PackagePlaceholderProcessor();

    @Test
    void replaces_basePackage_in_content() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/Foo.java",
                "package com.mr486.msplatform.servicea;")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).contains("com.example.myplatform.servicea");
        assertThat(contentOf(result.get(0))).doesNotContain("com.mr486.msplatform");
    }

    @Test
    void replaces_groupId_in_content_but_not_where_basePackage_already_replaced() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/pom.xml",
                "<groupId>com.mr486</groupId><parent><groupId>com.mr486</groupId></parent>")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).doesNotContain("com.mr486");
        assertThat(contentOf(result.get(0))).contains("com.example");
    }

    @Test
    void replaces_basePackage_in_path() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/Foo.java", "content")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).path())
            .contains("com/example/myplatform/servicea/Foo.java");
    }

    @Test
    void replaces_groupId_in_path_when_not_covered_by_basePackage() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        // path where groupId path appears but not the full basePackage path
        List<GeneratedFile> input = List.of(
            file("ms-platform/eureka/src/main/java/com/mr486/eureka/Foo.java", "content")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).path()).contains("com/example/eureka/Foo.java");
    }

    @Test
    void replaces_java_version_in_pom() {
        GenerationContext ctx = GenerationContext.from(
            new com.mr486.generator.dto.PlatformGenerationRequest() {{ setJavaVersion("21"); }}
        );
        List<GeneratedFile> input = List.of(
            file("ms-platform/pom.xml", "<java.version>17</java.version>")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).contains("<java.version>21</java.version>");
    }

    @Test
    void is_noop_when_defaults_unchanged() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/Foo.java", "package com.mr486.msplatform.servicea;")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).isEqualTo("package com.mr486.msplatform.servicea;");
    }

    @Test
    void does_not_corrupt_binary_like_content() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        byte[] binaryContent = new byte[]{0, 1, 2, (byte) 0xFF};
        List<GeneratedFile> input = List.of(new GeneratedFile("ms-platform/some.bin", binaryContent, false));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).content()).isEqualTo(binaryContent);
    }
}
