package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.res;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.file;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link WebUiCatalogRewriter} régénère le catalogue {@code webui.resources} de
 * l'{@code application.yml} de ms-webui (une entrée par ressource : nom, route, label, rôle) en
 * préservant les sections antérieures, et le laisse intact en l'absence de ressources.
 */
class WebUiCatalogRewriterTest {

    private static final String CLIENT_YML_PATH =
        "ms-platform/ms-webui/src/main/resources/application.yml";

    private static final String SAMPLE_CLIENT_YML =
        "server:\n  port: 8090\n" +
        "webui:\n" +
        "  resources:\n" +
        "    - serviceName: service-a\n" +
        "      routePrefix: /api/resources-a\n" +
        "      label: Service A\n" +
        "      role: USER_SERVICE_A\n" +
        "    - serviceName: service-b\n" +
        "      routePrefix: /api/resources-b\n" +
        "      label: Service B\n" +
        "      role: USER_SERVICE_B\n";

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

    @Test
    void client_catalog_rewritten_for_resources() {
        List<GeneratedFile> result = processor.process(
            List.of(file(CLIENT_YML_PATH, SAMPLE_CLIENT_YML)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO)));
        String yml = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-webui/src/main/resources/application.yml"))
            .findFirst().orElseThrow());
        assertThat(yml).contains("serviceName: order-service")
                       .contains("routePrefix: /api/orders")
                       .contains("role: USER_ORDER_SERVICE")
                       .contains("label: Order")
                       .contains("serviceName: product-service")
                       .contains("routePrefix: /api/products")
                       .contains("role: USER_PRODUCT_SERVICE");
        assertThat(yml).doesNotContain("service-a").doesNotContain("USER_SERVICE_A");
        assertThat(yml).contains("server:\n  port: 8090"); // section avant client: préservée
    }

    @Test
    void client_catalog_untouched_when_no_resources() {
        List<GeneratedFile> result = processor.process(
            List.of(file(CLIENT_YML_PATH, SAMPLE_CLIENT_YML)), defaultCtx());
        String yml = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-webui/src/main/resources/application.yml"))
            .findFirst().orElseThrow());
        assertThat(yml).isEqualTo(SAMPLE_CLIENT_YML);
    }
}
