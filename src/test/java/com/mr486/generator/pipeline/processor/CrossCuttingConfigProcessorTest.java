package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class CrossCuttingConfigProcessorTest {

    private final CrossCuttingConfigProcessor processor = new CrossCuttingConfigProcessor();

    private static final String SAMPLE_POM =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<project>\n" +
        "  <modules>\n" +
        "    <module>common-lib</module>\n" +
        "    <module>ms-eureka</module>\n" +
        "    <module>ms-gateway</module>\n" +
        "    <module>ms-admin</module>\n" +
        "    <module>service-a</module>\n" +
        "    <module>service-b</module>\n" +
        "    <module>service-c</module>\n" +
        "    <module>service-consumer</module>\n" +
        "    <module>service-batch</module>\n" +
        "    <module>ms-auth</module>\n" +
        "  </modules>\n" +
        "</project>\n";

    private static final String SAMPLE_COMPOSE =
        "services:\n" +
        "  keycloak-db:\n" +
        "    image: postgres:16\n" +
        "\n" +
        "  keycloak:\n" +
        "    image: quay.io/keycloak/keycloak:26.5.6\n" +
        "\n" +
        "  rabbitmq:\n" +
        "    image: rabbitmq:3.13\n" +
        "\n" +
        "  redis:\n" +
        "    image: redis:7-alpine\n" +
        "\n" +
        "  ms-eureka:\n" +
        "    build: ./ms-eureka\n" +
        "\n" +
        "  ms-gateway:\n" +
        "    build: ./ms-gateway\n" +
        "\n" +
        "  ms-admin:\n" +
        "    build: ./ms-admin\n" +
        "\n" +
        "  service-a-db:\n" +
        "    image: postgres:16\n" +
        "\n" +
        "  service-b-db:\n" +
        "    image: mongo:7\n" +
        "\n" +
        "  service-a:\n" +
        "    build: ./service-a\n" +
        "\n" +
        "  service-b:\n" +
        "    build: ./service-b\n" +
        "\n" +
        "  service-c:\n" +
        "    build: ./service-c\n" +
        "\n" +
        "  service-consumer:\n" +
        "    build: ./service-consumer\n" +
        "\n" +
        "  service-batch:\n" +
        "    build: ./service-batch\n" +
        "\n" +
        "  ms-auth:\n" +
        "    build: ./ms-auth\n" +
        "\n" +
        "volumes:\n" +
        "  keycloak_db_data:\n";

    private List<GeneratedFile> sampleFiles() {
        return List.of(
            file("ms-platform/pom.xml", SAMPLE_POM),
            file("ms-platform/docker-compose.yml", SAMPLE_COMPOSE),
            file("ms-platform/service-a/pom.xml", "<project/>")
        );
    }

    // ── Root pom <modules> rewriting ─────────────────────────────────────────

    @Test
    void root_pom_includes_all_default_modules_when_all_features_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setGrafana(true); f.setLoki(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());
        assertThat(pom).contains("<module>common-lib</module>")
                       .contains("<module>ms-eureka</module>")
                       .contains("<module>ms-gateway</module>")
                       .contains("<module>ms-admin</module>")
                       .contains("<module>service-a</module>")
                       .contains("<module>service-b</module>")
                       .contains("<module>service-c</module>")
                       .contains("<module>service-consumer</module>")
                       .contains("<module>service-batch</module>")
                       .contains("<module>ms-auth</module>");
    }

    @Test
    void root_pom_excludes_ms_auth_when_keycloak_disabled() {
        FeatureOptions f = new FeatureOptions(); f.setKeycloak(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>ms-auth</module>");
    }

    @Test
    void root_pom_excludes_ms_admin_when_admin_disabled() {
        FeatureOptions f = new FeatureOptions(); f.setAdmin(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>ms-admin</module>");
    }

    @Test
    void root_pom_excludes_service_batch_when_batch_disabled() {
        BatchOptions b = new BatchOptions(); b.setEnabled(false);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setBatch(b);
        GenerationContext ctx = GenerationContext.from(req);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>service-batch</module>");
    }

    @Test
    void root_pom_swaps_default_services_for_resources() {
        ResourceModuleRequest r1 = new ResourceModuleRequest();
        r1.setServiceName("order-service"); r1.setClassName("Order");
        ResourceModuleRequest r2 = new ResourceModuleRequest();
        r2.setServiceName("product-service"); r2.setClassName("Product");
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(r1, r2));
        GenerationContext ctx = GenerationContext.from(req);

        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String pom = contentOf(result.stream().filter(g -> g.path().endsWith("ms-platform/pom.xml")).findFirst().orElseThrow());

        assertThat(pom).doesNotContain("<module>service-a</module>")
                       .doesNotContain("<module>service-b</module>")
                       .doesNotContain("<module>service-c</module>")
                       .contains("<module>order-service</module>")
                       .contains("<module>product-service</module>");
    }

    // ── docker-compose service block removal ─────────────────────────────────

    @Test
    void compose_keeps_all_blocks_when_all_features_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setGrafana(true); f.setLoki(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String compose = contentOf(result.stream().filter(g -> g.path().endsWith("docker-compose.yml")).findFirst().orElseThrow());
        assertThat(compose).contains("  keycloak:").contains("  ms-auth:").contains("  service-a:");
    }

    @Test
    void compose_removes_keycloak_and_ms_auth_blocks_when_keycloak_disabled() {
        FeatureOptions f = new FeatureOptions(); f.setKeycloak(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String compose = contentOf(result.stream().filter(g -> g.path().endsWith("docker-compose.yml")).findFirst().orElseThrow());
        assertThat(compose).doesNotContain("  keycloak-db:")
                           .doesNotContain("  keycloak:\n")
                           .doesNotContain("  ms-auth:");
        // Sibling blocks remain
        assertThat(compose).contains("  rabbitmq:").contains("  ms-gateway:");
    }

    @Test
    void compose_removes_default_service_blocks_when_resources_provided() {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName("order-service"); r.setClassName("Order");
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(r));
        GenerationContext ctx = GenerationContext.from(req);

        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String compose = contentOf(result.stream().filter(g -> g.path().endsWith("docker-compose.yml")).findFirst().orElseThrow());

        assertThat(compose).doesNotContain("  service-a:")
                           .doesNotContain("  service-b:")
                           .doesNotContain("  service-c:")
                           .doesNotContain("  service-a-db:")
                           .doesNotContain("  service-b-db:");
        assertThat(compose).contains("  service-consumer:");
    }

    @Test
    void non_target_files_are_passed_through_unchanged() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        GeneratedFile servicePom = result.stream().filter(g -> g.path().endsWith("service-a/pom.xml")).findFirst().orElseThrow();
        assertThat(contentOf(servicePom)).isEqualTo("<project/>");
    }
}
