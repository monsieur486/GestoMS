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
 * Vérifie que {@link AggregateRewriter} régénère l'{@code AggregateController} de service-consumer :
 * un {@code Mono.zip} sur les services de ressources (URL routée + clé par nom de service), et le
 * laisse intact en l'absence de ressources.
 */
class AggregateRewriterTest {

    private static final String AGG_PATH =
        "ms-platform/service-consumer/src/main/java/com/acme/shop/consumer/controller/AggregateController.java";

    // fixture: SAMPLE_AGG contains a minified imports line — intentionally long, do not reformat
    private static final String SAMPLE_AGG =
        "package com.acme.shop.consumer.controller;\n" +
        "import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;"
        + "import reactor.core.publisher.Mono;import java.util.*;\n" +
        "@RestController @RequiredArgsConstructor @RequestMapping(\"/api\")\n" +
        "public class AggregateController{ return Mono.zip(uri(\"lb://service-a/api/resources-a\"),"
        + "uri(\"lb://service-b/api/resources-b\")); }";

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

    private String aggOf(List<GeneratedFile> result) {
        return contentOf(result.stream()
            .filter(g -> g.path().endsWith("AggregateController.java"))
            .findFirst().orElseThrow());
    }

    @Test
    void aggregate_zips_all_resource_services() {
        List<GeneratedFile> result = processor.process(
            List.of(file(AGG_PATH, SAMPLE_AGG)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO),
                             res("inventory-service", "Item", DatabaseType.H2)));
        String s = aggOf(result);
        assertThat(s).contains("package com.acme.shop.consumer.controller;");
        assertThat(s).contains("Mono.zip(");
        assertThat(s).contains("lb://order-service/api/orders")
                     .contains("lb://product-service/api/products")
                     .contains("lb://inventory-service/api/items");
        assertThat(s).contains("result.put(\"order-service\"")
                     .contains("result.put(\"product-service\"")
                     .contains("result.put(\"inventory-service\"");
        assertThat(s).doesNotContain("service-a").doesNotContain("resources-a");
    }

    @Test
    void aggregate_untouched_when_no_resources() {
        List<GeneratedFile> result = processor.process(List.of(file(AGG_PATH, SAMPLE_AGG)), defaultCtx());
        assertThat(aggOf(result)).isEqualTo(SAMPLE_AGG);
    }
}
