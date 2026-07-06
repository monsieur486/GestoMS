package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.res;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.file;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link TestAllRewriter} régénère {@code test-all.sh} depuis {@code resources[]} :
 * URLs et utilisateurs par service, matrice d'accès (200/403), préservation du bit exécutable,
 * section ms-webui conditionnée par la feature, et bloc création admin2 + changement de mot de passe.
 */
class TestAllRewriterTest {

    private static final String TESTALL_PATH = "ms-platform/test-all.sh";

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

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
        assertThat(s).contains("wait_for 'ms-webui'").contains("WebUI OK");
    }

    @Test
    void test_all_omits_ms_client_smoke_when_client_web_ui_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(res("order-service", "Order", DatabaseType.POSTGRES)));
        List<GeneratedFile> result = processor.process(
                List.of(file(TESTALL_PATH, "old", true)), GenerationContext.from(req));
        String s = testAllOf(result);
        assertThat(s).doesNotContain("ms-webui").doesNotContain("WebUI OK");
    }

    @Test
    void test_all_includes_admin2_creation_and_self_password_change() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(res("order-service", "Order", DatabaseType.POSTGRES)));
        List<GeneratedFile> result = processor.process(
                List.of(file(TESTALL_PATH, "old", true)), GenerationContext.from(req));
        String s = testAllOf(result);
        assertThat(s)
                .contains("Testing admin user creation + self password change")
                .contains("/admin/realms/ms-realm/users")
                .contains("\"username\":\"admin2\"")
                .contains("/auth/account/password")
                .contains("admin2 self password change -> 204")
                .contains("wrong old password rejected -> 422");
    }
}
