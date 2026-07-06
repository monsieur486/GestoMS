package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.res;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.sampleFiles;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link GatewayRewriter} réécrit les routes du {@code application.yml} de ms-gateway :
 * routes par défaut conservées sans ressources, remplacées par une route par ressource sinon
 * (ms-auth et service-consumer toujours préservés), et laisse les autres fichiers intacts.
 */
class GatewayRewriterTest {

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

    private String gatewayContent(List<GeneratedFile> result) {
        return contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-gateway/src/main/resources/application.yml"))
            .findFirst().orElseThrow());
    }

    @Test
    void gateway_yml_unchanged_with_defaults() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String yml = gatewayContent(result);
        assertThat(yml).contains("- id: ms-auth").contains("- id: service-a")
                       .contains("- id: service-b").contains("- id: service-c")
                       .contains("- id: service-consumer");
    }

    @Test
    void gateway_yml_swaps_default_services_for_resource_routes() {
        ResourceModuleRequest r1 = res("order-service", "Order", DatabaseType.POSTGRES);
        ResourceModuleRequest r2 = res("product-service", "Product", DatabaseType.MONGO);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(r1, r2));
        GenerationContext ctx = GenerationContext.from(req);

        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String yml = gatewayContent(result);

        assertThat(yml).doesNotContain("- id: service-a\n")
                       .doesNotContain("- id: service-b\n")
                       .doesNotContain("- id: service-c\n");
        assertThat(yml).contains("- id: order-service")
                       .contains("uri: lb://order-service")
                       .contains("Path=/order-service/**")
                       .contains("- id: product-service")
                       .contains("uri: lb://product-service")
                       .contains("Path=/product-service/**");
        // ms-auth + service-consumer preserved
        assertThat(yml).contains("- id: ms-auth").contains("- id: service-consumer");
    }

    @Test
    void non_target_files_are_passed_through_unchanged() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        GeneratedFile servicePom = result.stream()
            .filter(g -> g.path().endsWith("service-a/pom.xml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(servicePom)).isEqualTo("<project/>");
    }
}
