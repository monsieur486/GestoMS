package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.res;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.sampleFiles;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.ctxWithFeatures;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link ComposeRewriter} synchronise {@code docker-compose.yml} : retrait des blocs
 * de services désactivés/par défaut et de leurs volumes, ajout d'un bloc (service + base + volume)
 * par ressource selon son type de base, et conservation d'{@code admin-application}.
 */
class ComposeRewriterTest {

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

    @Test
    void compose_always_keeps_admin_application_block() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());
        assertThat(compose).contains("  admin-application:");
    }

    @Test
    void compose_keeps_ms_client_block_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setWebUI(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());
        assertThat(compose).contains("  ms-webui:");
    }

    @Test
    void compose_removes_ms_client_block_when_client_web_ui_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());
        assertThat(compose).doesNotContain("  ms-webui:");
    }

    @Test
    void compose_keeps_all_blocks_when_all_features_enabled() {
        FeatureOptions f = new FeatureOptions();
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());
        assertThat(compose).contains("  keycloak:").contains("  ms-auth:").contains("  service-a:");
    }

    @Test
    void compose_removes_default_service_blocks_when_resources_provided() {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName("order-service");
        r.setClassName("Order");
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(r));
        GenerationContext ctx = GenerationContext.from(req);

        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());

        assertThat(compose).doesNotContain("  service-a:")
                           .doesNotContain("  service-b:")
                           .doesNotContain("  service-c:")
                           .doesNotContain("  service-a-db:")
                           .doesNotContain("  service-b-db:");
        assertThat(compose).contains("  service-consumer:");
    }

    @Test
    void compose_adds_postgres_resource_with_db_block_and_volume() {
        ResourceModuleRequest r = res("order-service", "Order", DatabaseType.POSTGRES);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithResources(r));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());

        assertThat(compose).contains("  order-service-db:")
                           .contains("image: postgres:16")
                           .contains("POSTGRES_DB: order_service_db")
                           .contains("POSTGRES_USER: order_service");
        assertThat(compose).contains("  order-service:")
                           .contains("build: ./order-service")
                           .contains("depends_on: [ms-eureka, keycloak, order-service-db]")
                           .contains("ORDER_SERVICE_DATASOURCE_URL: "
                               + "jdbc:postgresql://order-service-db:5432/order_service_db");
        assertThat(compose).contains("  order_service_db_data:");
    }

    @Test
    void compose_adds_mongo_resource_with_mongo_db_block() {
        ResourceModuleRequest r = res("product-service", "Product", DatabaseType.MONGO);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithResources(r));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());

        assertThat(compose).contains("  product-service-db:")
                           .contains("image: mongo:7")
                           .contains("MONGO_INITDB_ROOT_USERNAME: product_service")
                           .contains("MONGO_INITDB_DATABASE: product_service_db");
        assertThat(compose).contains("  product-service:")
                           .contains("PRODUCT_SERVICE_MONGO_URI: "
                               + "mongodb://product_service:product_service"
                               + "@product-service-db:27017/product_service_db?authSource=admin");
        assertThat(compose).contains("  product_service_db_data:");
    }

    @Test
    void compose_adds_h2_resource_without_db_block_or_volume() {
        ResourceModuleRequest r = res("light-service", "Light", DatabaseType.H2);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithResources(r));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());

        assertThat(compose).contains("  light-service:")
                           .contains("build: ./light-service")
                           .contains("depends_on: [ms-eureka, keycloak]");
        assertThat(compose).doesNotContain("  light-service-db:")
                           .doesNotContain("light_service_db_data:");
    }

    @Test
    void compose_removes_default_service_volumes_when_resources_provided() {
        ResourceModuleRequest r = res("order-service", "Order", DatabaseType.POSTGRES);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithResources(r));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());
        assertThat(compose).doesNotContain("  service_a_db_data:")
                           .doesNotContain("  service_b_db_data:");
        assertThat(compose).contains("  keycloak_db_data:")
                           .contains("  redis_data:")
                           .contains("  order_service_db_data:");
    }

    @Test
    void compose_resource_blocks_inserted_before_volumes_section() {
        ResourceModuleRequest r = res("order-service", "Order", DatabaseType.POSTGRES);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithResources(r));
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());

        // top-level volumes: section starts at column 0 (no leading space)
        int orderIdx = compose.indexOf("  order-service:");
        int volumesSectionIdx = compose.indexOf("\nvolumes:");
        assertThat(orderIdx).isGreaterThan(0).isLessThan(volumesSectionIdx);
    }
}
