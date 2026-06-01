package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

/**
 * Vérifie que {@link CrossCuttingConfigProcessor} réécrit correctement les
 * fichiers transversaux de la plateforme : pom.xml racine (modules), docker-compose
 * (blocs de services et volumes), routes du gateway, realm Keycloak, script
 * test-all.sh, AggregateController, et catalogue ms-webui — en fonction des
 * features activées et de la liste de resources.
 */
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
        "  ms-webui:\n" +
        "    build: ./ms-webui\n" +
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
        "    depends_on: [ms-eureka, keycloak, rabbitmq, redis]\n" +
        "\n" +
        "  service-batch:\n" +
        "    build: ./service-batch\n" +
        "\n" +
        "  ms-auth:\n" +
        "    build: ./ms-auth\n" +
        "\n" +
        "  admin-application:\n" +
        "    build: ./admin-application\n" +
        "\n" +
        "volumes:\n" +
        "  keycloak_db_data:\n" +
        "  redis_data:\n" +
        "  service_a_db_data:\n" +
        "  service_b_db_data:\n";

    private static final String SAMPLE_GATEWAY_YML =
        "server:\n" +
        "  port: ${GATEWAY_PORT:9000}\n" +
        "spring:\n" +
        "  cloud:\n" +
        "    gateway:\n" +
        "      server:\n" +
        "        webflux:\n" +
        "          routes:\n" +
        "            - id: ms-auth\n" +
        "              uri: lb://ms-auth\n" +
        "              predicates:\n" +
        "                - Path=/auth/**\n" +
        "            - id: service-a\n" +
        "              uri: lb://service-a\n" +
        "              predicates:\n" +
        "                - Path=/service-a/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "            - id: service-b\n" +
        "              uri: lb://service-b\n" +
        "              predicates:\n" +
        "                - Path=/service-b/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "            - id: service-c\n" +
        "              uri: lb://service-c\n" +
        "              predicates:\n" +
        "                - Path=/service-c/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "            - id: service-consumer\n" +
        "              uri: lb://service-consumer\n" +
        "              predicates:\n" +
        "                - Path=/service-consumer/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "eureka:\n" +
        "  client:\n" +
        "    service-url:\n" +
        "      defaultZone: http://localhost:8761/eureka/\n";

    private List<GeneratedFile> sampleFiles() {
        return List.of(
            file("ms-platform/pom.xml", SAMPLE_POM),
            file("ms-platform/docker-compose.yml", SAMPLE_COMPOSE),
            file("ms-platform/ms-gateway/src/main/resources/application.yml", SAMPLE_GATEWAY_YML),
            file("ms-platform/service-a/pom.xml", "<project/>")
        );
    }

    private String gatewayContent(List<GeneratedFile> result) {
        return contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-gateway/src/main/resources/application.yml"))
            .findFirst().orElseThrow());
    }

    // ── Root pom <modules> rewriting ─────────────────────────────────────────

    @Test
    void root_pom_includes_all_default_modules_when_all_features_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setSpringbootAdmin(true);
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
    void compose_always_keeps_admin_application_block() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        String compose = contentOf(result.stream()
            .filter(g -> g.path().endsWith("docker-compose.yml"))
            .findFirst().orElseThrow());
        assertThat(compose).contains("  admin-application:");
    }

    @Test
    void root_pom_excludes_ms_admin_when_admin_disabled() {
        FeatureOptions f = new FeatureOptions(); f.setSpringbootAdmin(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        String pom = contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-platform/pom.xml"))
            .findFirst().orElseThrow());
        assertThat(pom).doesNotContain("<module>ms-admin</module>");
    }

    @Test
    void root_pom_includes_ms_client_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setWebUI(true);
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
    void compose_keeps_ms_client_block_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions(); f.setWebUI(true);
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
    void root_pom_excludes_service_batch_when_batch_disabled() {
        BatchOptions b = new BatchOptions(); b.setEnabled(false);
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
        r1.setServiceName("order-service"); r1.setClassName("Order");
        ResourceModuleRequest r2 = new ResourceModuleRequest();
        r2.setServiceName("product-service"); r2.setClassName("Product");
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

    // ── docker-compose service block removal ─────────────────────────────────

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
        r.setServiceName("order-service"); r.setClassName("Order");
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

    // ── gateway application.yml routes ───────────────────────────────────────

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

    // ── docker-compose: add new resource service blocks ──────────────────────

    private GenerationContext ctxWithResources(ResourceModuleRequest... rs) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(rs));
        return GenerationContext.from(req);
    }

    private static ResourceModuleRequest res(String serviceName, String className, DatabaseType db) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(serviceName);
        r.setClassName(className);
        r.setDatabaseType(db);
        return r;
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

    // ── stale volume cleanup ─────────────────────────────────────────────────

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

    // ── Keycloak realm regeneration ──────────────────────────────────────────

    private static final String REALM_PATH = "ms-platform/keycloak/import/ms-realm-realm.json";

    // fixture: minified JSON user lines in SAMPLE_REALM are intentionally long — do not reformat
    private static final String SAMPLE_REALM =
        "{\n" +
        "  \"realm\": \"ms-realm\",\n" +
        "  \"roles\": { \"realm\": [\n" +
        "    {\"name\":\"USER_SERVICE_A\"},\n" +
        "    {\"name\":\"USER_SERVICE_B\"},\n" +
        "    {\"name\":\"USER_SERVICE_C\"},\n" +
        "    {\"name\":\"USER_BATCH\"},\n" +
        "    {\"name\":\"ADMIN\"},\n" +
        "    {\"name\":\"SERVICE\"}\n" +
        "  ] },\n" +
        "  \"users\": [\n" +
        "    {\"username\":\"test-admin\",\"credentials\":[{\"type\":\"password\",\"value\":\"admin123\",\"temporary\":false}],\"realmRoles\":[\"ADMIN\",\"USER_BATCH\",\"USER_SERVICE_A\",\"USER_SERVICE_B\",\"USER_SERVICE_C\"]},\n" +
        "    {\"username\":\"test-batch\",\"credentials\":[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],\"realmRoles\":[\"USER_BATCH\"]},\n" +
        "    {\"username\":\"test-service-a\",\"credentials\":[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],\"realmRoles\":[\"USER_SERVICE_A\"]},\n" +
        "    {\"username\":\"test-service-b\",\"credentials\":[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],\"realmRoles\":[\"USER_SERVICE_B\"]},\n" +
        "    {\"username\":\"test-service-c\",\"credentials\":[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],\"realmRoles\":[\"USER_SERVICE_C\"]}\n" +
        "  ]\n" +
        "}\n";

    private String realmOf(List<GeneratedFile> result) {
        return contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-realm-realm.json"))
            .findFirst().orElseThrow());
    }

    private static JsonNode parse(String json) {
        try { return new ObjectMapper().readTree(json); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static JsonNode userNamed(JsonNode realm, String username) {
        for (JsonNode u : realm.get("users")) if (username.equals(u.path("username").asText())) return u;
        return null;
    }

    @Test
    void realm_generates_role_and_user_per_resource() {
        List<GeneratedFile> result = processor.process(
            List.of(file(REALM_PATH, SAMPLE_REALM)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO),
                             res("inventory-service", "Item", DatabaseType.H2)));
        String realm = realmOf(result);
        assertThat(realm).contains("USER_ORDER_SERVICE", "USER_PRODUCT_SERVICE", "USER_INVENTORY_SERVICE");
        assertThat(realm).doesNotContain("USER_SERVICE_A", "USER_SERVICE_B", "USER_SERVICE_C");
        assertThat(realm).contains("test-order-service", "test-product-service", "test-inventory-service");
        assertThat(realm).doesNotContain("test-service-a", "test-service-b", "test-service-c");
    }

    @Test
    void realm_admin_user_gets_all_resource_roles() {
        List<GeneratedFile> result = processor.process(
            List.of(file(REALM_PATH, SAMPLE_REALM)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO)));
        JsonNode admin = userNamed(parse(realmOf(result)), "test-admin");
        assertThat(admin).isNotNull();
        List<String> roles = new ArrayList<>();
        admin.get("realmRoles").forEach(n -> roles.add(n.asText()));
        assertThat(roles).contains("ADMIN", "USER_BATCH", "USER_ORDER_SERVICE", "USER_PRODUCT_SERVICE")
                         .doesNotContain("USER_SERVICE_A", "USER_SERVICE_B", "USER_SERVICE_C");
    }

    @Test
    void realm_new_user_has_password_and_single_role() {
        List<GeneratedFile> result = processor.process(
            List.of(file(REALM_PATH, SAMPLE_REALM)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES)));
        JsonNode user = userNamed(parse(realmOf(result)), "test-order-service");
        assertThat(user).isNotNull();
        assertThat(user.get("credentials").get(0).get("value").asText()).isEqualTo("user123");
        List<String> roles = new ArrayList<>();
        user.get("realmRoles").forEach(n -> roles.add(n.asText()));
        assertThat(roles).containsExactly("USER_ORDER_SERVICE");
    }

    @Test
    void realm_untouched_when_no_resources() {
        List<GeneratedFile> result = processor.process(List.of(file(REALM_PATH, SAMPLE_REALM)), defaultCtx());
        assertThat(realmOf(result)).isEqualTo(SAMPLE_REALM);
    }

    // ── test-all.sh regeneration ─────────────────────────────────────────────

    private static final String TESTALL_PATH = "ms-platform/test-all.sh";

    private String testAllOf(List<GeneratedFile> result) {
        return contentOf(result.stream()
            .filter(g -> g.path().endsWith("/test-all.sh"))
            .findFirst().orElseThrow());
    }

    @Test
    void test_all_uses_resource_urls_and_users() {
        List<GeneratedFile> result = processor.process(
            List.of(file(TESTALL_PATH, "#!/usr/bin/env bash\nold service-a resources-a test-service-a\n", true)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO),
                             res("inventory-service", "Item", DatabaseType.H2)));
        String s = testAllOf(result);
        assertThat(s).contains("$GATEWAY_URL/order-service/api/orders")
                     .contains("$GATEWAY_URL/product-service/api/products")
                     .contains("$GATEWAY_URL/inventory-service/api/items")
                     .contains("auth_login test-order-service");
        assertThat(s).doesNotContain("resources-a").doesNotContain("/service-a/").doesNotContain("test-service-a");
    }

    @Test
    void test_all_denies_cross_service_access() {
        List<GeneratedFile> result = processor.process(
            List.of(file(TESTALL_PATH, "old", true)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES),
                             res("product-service", "Product", DatabaseType.MONGO)));
        String s = testAllOf(result);
        assertThat(s).contains("403 GET \"$TOKEN_PRODUCT_SERVICE\" \"$GATEWAY_URL/order-service/api/orders\"");
        assertThat(s).contains("200 GET \"$TOKEN_ADMIN\" \"$GATEWAY_URL/order-service/api/orders\"");
        assertThat(s).contains("200 GET \"$TOKEN_ORDER_SERVICE\" \"$GATEWAY_URL/order-service/api/orders\"");
    }

    @Test
    void test_all_preserves_executable_flag() {
        List<GeneratedFile> result = processor.process(
            List.of(file(TESTALL_PATH, "old", true)),
            ctxWithResources(res("order-service", "Order", DatabaseType.POSTGRES)));
        GeneratedFile f = result.stream()
            .filter(g -> g.path().endsWith("/test-all.sh"))
            .findFirst().orElseThrow();
        assertThat(f.executable()).isTrue();
    }

    @Test
    void test_all_includes_ms_client_smoke_when_client_web_ui_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(res("order-service", "Order", DatabaseType.POSTGRES)));
        req.getFeatures().setWebUI(true);
        List<GeneratedFile> result = processor.process(
                List.of(file(TESTALL_PATH, "old", true)), GenerationContext.from(req));
        String s = testAllOf(result);
        assertThat(s).contains("wait_for 'ms-webui'").contains("Client OK");
    }

    @Test
    void test_all_omits_ms_client_smoke_when_client_web_ui_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(res("order-service", "Order", DatabaseType.POSTGRES)));
        List<GeneratedFile> result = processor.process(
                List.of(file(TESTALL_PATH, "old", true)), GenerationContext.from(req));
        String s = testAllOf(result);
        assertThat(s).doesNotContain("ms-webui").doesNotContain("Client OK");
    }

    // ── AggregateController regeneration ─────────────────────────────────────

    private static final String AGG_PATH =
        "ms-platform/service-consumer/src/main/java/com/acme/shop/consumer/controller/AggregateController.java";

    // fixture: SAMPLE_AGG contains a minified imports line — intentionally long, do not reformat
    private static final String SAMPLE_AGG =
        "package com.acme.shop.consumer.controller;\n" +
        "import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import reactor.core.publisher.Mono;import java.util.*;\n" +
        "@RestController @RequiredArgsConstructor @RequestMapping(\"/api\")\n" +
        "public class AggregateController{ return Mono.zip(uri(\"lb://service-a/api/resources-a\"),uri(\"lb://service-b/api/resources-b\")); }";

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

    // ── ms-webui application.yml catalog rewrite ─────────────────────────────

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
