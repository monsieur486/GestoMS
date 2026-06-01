package com.mr486.generator.pipeline.processor;

import com.mr486.generator.config.PlatformVersions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que {@link VersionInjectionProcessor} injecte correctement les
 * versions d'images dans docker-compose (interpolation env), les Dockerfiles
 * (ARG JAVA_IMAGE), les fichiers .env (bloc de versions), et les pom.xml
 * (parent Spring Boot, spring-cloud, mongock) — et que ces transformations
 * sont idempotentes.
 */
class VersionInjectionProcessorTest {

    private final VersionInjectionProcessor processor =
        new VersionInjectionProcessor(new PlatformVersions());   // défauts = littéraux template

    private final GenerationContext ctx = defaultCtx();

    private String run(String path, String content) {
        List<GeneratedFile> out = processor.process(List.of(file(path, content)), ctx);
        return contentOf(out.get(0));
    }

    @Test
    void compose_image_becomes_env_interpolation() {
        String out = run("ms-platform/docker-compose.yml",
            "  keycloak-db:\n    image: postgres:16\n");
        assertThat(out).contains("image: postgres:${POSTGRES_VERSION:-16}");
        assertThat(out).doesNotContain("image: postgres:16\n");
    }

    @Test
    void compose_all_managed_images_interpolated() {
        String src = "    image: quay.io/keycloak/keycloak:26.5.6\n"
                   + "    image: rabbitmq:3.13-management\n"
                   + "    image: redis:7-alpine\n"
                   + "    image: mongo:7\n"
                   + "    image: grafana/loki:3.2.1\n"
                   + "    image: grafana/promtail:3.2.1\n"
                   + "    image: grafana/grafana:11.2.2\n";
        String out = run("ms-platform/docker-compose.yml", src);
        assertThat(out).contains("quay.io/keycloak/keycloak:${KEYCLOAK_VERSION:-26.5.6}");
        assertThat(out).contains("rabbitmq:${RABBITMQ_VERSION:-3.13-management}");
        assertThat(out).contains("redis:${REDIS_VERSION:-7-alpine}");
        assertThat(out).contains("mongo:${MONGO_VERSION:-7}");
        assertThat(out).contains("grafana/loki:${LOKI_VERSION:-3.2.1}");
        assertThat(out).contains("grafana/promtail:${PROMTAIL_VERSION:-3.2.1}");
        assertThat(out).contains("grafana/grafana:${GRAFANA_VERSION:-11.2.2}");
    }

    @Test
    void compose_build_short_form_expands_with_java_image_arg() {
        String out = run("ms-platform/docker-compose.yml",
            "  ms-eureka:\n    build: ./ms-eureka\n    env_file: [.env]\n");
        assertThat(out).contains(
            "    build:\n"
          + "      context: ./ms-eureka\n"
          + "      args:\n"
          + "        JAVA_IMAGE: ${JAVA_IMAGE:-eclipse-temurin:17-jre}");
        assertThat(out).doesNotContain("build: ./ms-eureka\n");
        assertThat(out).contains("    env_file: [.env]");
    }

    @Test
    void dockerfile_uses_arg_java_image() {
        String out = run("ms-platform/ms-eureka/Dockerfile",
            "FROM eclipse-temurin:17-jre\nWORKDIR /app\n");
        assertThat(out).contains("ARG JAVA_IMAGE=eclipse-temurin:17-jre\nFROM ${JAVA_IMAGE}");
        assertThat(out).doesNotContain("FROM eclipse-temurin:17-jre\n");
    }

    @Test
    void env_file_gets_version_block() {
        String out = run("ms-platform/.env", "REDIS_HOST=redis\n");
        assertThat(out).contains("# --- image versions ---");
        assertThat(out).contains("JAVA_IMAGE=eclipse-temurin:17-jre");
        assertThat(out).contains("POSTGRES_VERSION=16");
        assertThat(out).contains("GRAFANA_VERSION=11.2.2");
        assertThat(out).contains("REDIS_HOST=redis");
    }

    @Test
    void dist_env_also_gets_version_block() {
        String out = run("ms-platform/dist.env", "REDIS_HOST=redis\n");
        assertThat(out).contains("# --- image versions ---");
        assertThat(out).contains("KEYCLOAK_VERSION=26.5.6");
    }

    @Test
    void root_pom_parent_and_properties_are_noop_at_defaults() {
        String src = "<parent><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-starter-parent</artifactId><version>3.5.5</version><relativePath/></parent>"
            + "<properties><java.version>17</java.version>"
            + "<spring-cloud.version>2025.0.0</spring-cloud.version>"
            + "<mongock.version>5.5.1</mongock.version></properties>";
        String out = run("ms-platform/pom.xml", src);
        assertThat(out).isEqualTo(src);
    }

    @Test
    void root_pom_properties_rewritten_when_config_overridden() {
        PlatformVersions cfg = new PlatformVersions();
        cfg.setSpringBoot("3.6.0");
        cfg.setSpringCloud("2025.1.0");
        cfg.setMongock("5.6.0");
        VersionInjectionProcessor p = new VersionInjectionProcessor(cfg);
        String src = "<parent><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-starter-parent</artifactId><version>3.5.5</version><relativePath/></parent>"
            + "<spring-cloud.version>2025.0.0</spring-cloud.version>"
            + "<mongock.version>5.5.1</mongock.version>";
        String out = contentOf(p.process(List.of(file("ms-platform/pom.xml", src)), defaultCtx()).get(0));
        assertThat(out).contains("<artifactId>spring-boot-starter-parent</artifactId><version>3.6.0</version>");
        assertThat(out).contains("<spring-cloud.version>2025.1.0</spring-cloud.version>");
        assertThat(out).contains("<mongock.version>5.6.0</mongock.version>");
    }

    @Test
    void ms_admin_pom_spring_boot_admin_rewritten_when_overridden() {
        PlatformVersions cfg = new PlatformVersions();
        cfg.setSpringBootAdmin("3.6.0");
        VersionInjectionProcessor p = new VersionInjectionProcessor(cfg);
        String src = "<dependency><groupId>de.codecentric</groupId>"
            + "<artifactId>spring-boot-admin-starter-server</artifactId><version>3.5.5</version></dependency>";
        String out = contentOf(
            p.process(List.of(file("ms-platform/ms-admin/pom.xml", src)), defaultCtx()).get(0));
        assertThat(out).contains(
            "<artifactId>spring-boot-admin-starter-server</artifactId><version>3.6.0</version>");
    }

    @Test
    void java_version_property_is_left_untouched() {
        String out = run("ms-platform/pom.xml", "<properties><java.version>21</java.version></properties>");
        assertThat(out).contains("<java.version>21</java.version>");
    }

    @Test
    void compose_image_rewrite_is_idempotent() {
        String once = run("ms-platform/docker-compose.yml", "    image: postgres:16\n");
        String twice = run("ms-platform/docker-compose.yml", once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void binary_file_is_passed_through() {
        byte[] bin = new byte[]{1, 0, 2, 0};
        List<GeneratedFile> out = processor.process(
            List.of(new GeneratedFile("ms-platform/x.bin", bin, false)), ctx);
        assertThat(out.get(0).content()).isEqualTo(bin);
    }

    @Test
    void unrelated_file_unchanged() {
        String out = run("ms-platform/README.md", "# hello\n");
        assertThat(out).isEqualTo("# hello\n");
    }

    @Test
    void dockerfile_rewrite_is_idempotent() {
        String once = run("ms-platform/ms-eureka/Dockerfile", "FROM eclipse-temurin:17-jre\nWORKDIR /app\n");
        String twice = run("ms-platform/ms-eureka/Dockerfile", once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void env_block_is_idempotent() {
        String once = run("ms-platform/.env", "REDIS_HOST=redis\n");
        String twice = run("ms-platform/.env", once);
        assertThat(twice).isEqualTo(once);
    }
}
