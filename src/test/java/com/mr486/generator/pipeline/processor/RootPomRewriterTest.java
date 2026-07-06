package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.res;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.sampleFiles;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.ctxWithFeatures;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link RootPomRewriter} régénère le bloc {@code <modules>} du pom racine selon les
 * features (ms-admin, ms-webui, service-batch) et remplace les services par défaut par les
 * ressources demandées, en conservant toujours {@code admin-application}.
 */
class RootPomRewriterTest {

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

    @Test
    void root_pom_includes_all_default_modules_when_all_features_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setSpringbootAdmin(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());
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
    void root_pom_always_includes_admin_application() {
        // sans features ni resources
        List<GeneratedFile> a = processor.process(sampleFiles(), defaultCtx());
        assertThat(contentOf(a.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow()))
                .contains("<module>admin-application</module>");
        // avec resources
        List<GeneratedFile> b = processor.process(sampleFiles(),
                ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES)));
        assertThat(contentOf(b.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow()))
                .contains("<module>admin-application</module>");
    }

    @Test
    void root_pom_excludes_ms_admin_when_admin_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setSpringbootAdmin(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>ms-admin</module>");
    }

    @Test
    void root_pom_includes_ms_client_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setWebUI(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());
        assertThat(pom).contains("<module>ms-webui</module>");
    }

    @Test
    void root_pom_excludes_ms_client_when_client_web_ui_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>ms-webui</module>");
    }

    @Test
    void root_pom_excludes_service_batch_when_batch_disabled() {
        BatchOptions b = new BatchOptions();
        b.setEnabled(false);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setBatch(b);
        GenerationContext ctx = GenerationContext.from(req);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>service-batch</module>");
    }

    @Test
    void root_pom_swaps_default_services_for_resources() {
        ResourceModuleRequest r1 = new ResourceModuleRequest();
        r1.setServiceName("order-service");
        r1.setClassName("Order");
        ResourceModuleRequest r2 = new ResourceModuleRequest();
        r2.setServiceName("product-service");
        r2.setClassName("Product");
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(r1, r2));
        GenerationContext ctx = GenerationContext.from(req);

        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());

        assertThat(pom).doesNotContain("<module>service-a</module>")
                       .doesNotContain("<module>service-b</module>")
                       .doesNotContain("<module>service-c</module>")
                       .contains("<module>order-service</module>")
                       .contains("<module>product-service</module>");
    }
}
