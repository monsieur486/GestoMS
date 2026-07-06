package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.CrossCuttingFixtures.res;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.file;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.zip.GeneratedFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link RealmRewriter} régénère le realm Keycloak : un rôle et un utilisateur de test
 * par ressource (mot de passe {@code user123}, rôle unique), repointage de {@code test-admin} vers
 * tous les rôles de ressources, et realm intact en l'absence de ressources.
 */
class RealmRewriterTest {

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
        "    {\"username\":\"test-admin\",\"credentials\":"
        + "[{\"type\":\"password\",\"value\":\"admin123\",\"temporary\":false}],"
        + "\"realmRoles\":[\"ADMIN\",\"USER_BATCH\",\"USER_SERVICE_A\",\"USER_SERVICE_B\",\"USER_SERVICE_C\"]},\n" +
        "    {\"username\":\"test-batch\",\"credentials\":"
        + "[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],\"realmRoles\":[\"USER_BATCH\"]},\n" +
        "    {\"username\":\"test-service-a\",\"credentials\":"
        + "[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],"
        + "\"realmRoles\":[\"USER_SERVICE_A\"]},\n" +
        "    {\"username\":\"test-service-b\",\"credentials\":"
        + "[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],"
        + "\"realmRoles\":[\"USER_SERVICE_B\"]},\n" +
        "    {\"username\":\"test-service-c\",\"credentials\":"
        + "[{\"type\":\"password\",\"value\":\"user123\",\"temporary\":false}],"
        + "\"realmRoles\":[\"USER_SERVICE_C\"]}\n" +
        "  ]\n" +
        "}\n";

    private final CrossCuttingConfigProcessor processor = CrossCuttingFixtures.processor();

    private String realmOf(List<GeneratedFile> result) {
        return contentOf(result.stream()
            .filter(g -> g.path().endsWith("ms-realm-realm.json"))
            .findFirst().orElseThrow());
    }

    private static JsonNode parse(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode userNamed(JsonNode realm, String username) {
        for (JsonNode u : realm.get("users")) {
            if (username.equals(u.path("username").asText())) {
                return u;
            }
        }
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
}
