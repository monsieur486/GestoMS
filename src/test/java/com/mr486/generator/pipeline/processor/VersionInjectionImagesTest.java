package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.file;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.config.PlatformVersions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link VersionInjectionProcessor} injecte les versions d'images dans
 * docker-compose (interpolation env), les Dockerfiles (ARG JAVA_IMAGE) et les fichiers
 * {@code .env} (bloc de versions) — et que ces transformations sont idempotentes.
 */
class VersionInjectionImagesTest {

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
