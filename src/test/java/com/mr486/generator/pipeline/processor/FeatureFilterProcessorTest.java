package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class FeatureFilterProcessorTest {

    private final FeatureFilterProcessor processor = new FeatureFilterProcessor();

    private List<GeneratedFile> sampleFiles() {
        return List.of(
            file("ms-platform/keycloak/import/realm.json", "{}"),
            file("ms-platform/ms-auth/pom.xml", "<project/>"),
            file("ms-platform/ms-admin/pom.xml", "<project/>"),
            file("ms-platform/ms-client/pom.xml", "<project/>"),
            file("ms-platform/observability/grafana/dashboards/d.json", "{}"),
            file("ms-platform/observability/promtail/config.yml", ""),
            file("ms-platform/observability/loki/config.yml", ""),
            file("ms-platform/service-batch/pom.xml", "<project/>"),
            file("ms-platform/service-consumer/src/main/java/x/RabbitConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/RedisConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/WebSocketConfig.java", "class W{}"),
            file("ms-platform/service-consumer/src/main/resources/static/batch-notifications.html", "<html/>"),
            file("ms-platform/docker-compose.yml", "services:")
        );
    }

    @Test
    void keeps_permanent_components_regardless_of_options() {
        // defaults: springbootAdmin=false, clientWebUI=false, batch.grafana=false, batch.enabled=true
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).anyMatch(e -> e.path().contains("/keycloak/"));
        assertThat(result).anyMatch(e -> e.path().contains("/ms-auth/"));
        assertThat(result).anyMatch(e -> e.path().endsWith("RedisConfig.java"));
        assertThat(result).anyMatch(e -> e.path().endsWith("RabbitConfig.java"));
        assertThat(result).anyMatch(e -> e.path().endsWith("WebSocketConfig.java"));
        assertThat(result).anyMatch(e -> e.path().endsWith("batch-notifications.html"));
    }

    @Test
    void removes_ms_admin_when_springboot_admin_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).noneMatch(e -> e.path().contains("/ms-admin/"));
    }

    @Test
    void keeps_ms_admin_when_springboot_admin_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setSpringbootAdmin(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).anyMatch(e -> e.path().contains("/ms-admin/"));
    }

    @Test
    void removes_ms_client_when_client_web_ui_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).noneMatch(e -> e.path().contains("/ms-client/"));
    }

    @Test
    void keeps_ms_client_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setClientWebUI(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).anyMatch(e -> e.path().contains("/ms-client/"));
    }

    @Test
    void removes_all_observability_when_grafana_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).noneMatch(e -> e.path().contains("/observability/"));
    }

    @Test
    void keeps_all_observability_when_grafana_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setGrafana(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).anyMatch(e -> e.path().contains("/observability/grafana/"));
        assertThat(result).anyMatch(e -> e.path().contains("/observability/loki/"));
        assertThat(result).anyMatch(e -> e.path().contains("/observability/promtail/"));
    }

    @Test
    void removes_service_batch_when_batch_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).noneMatch(e -> e.path().contains("/service-batch/"));
    }

    @Test
    void keeps_service_batch_when_batch_enabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).anyMatch(e -> e.path().contains("/service-batch/"));
    }

    @Test
    void filters_relative_to_target_root_not_source() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-app");
        // springbootAdmin=false -> ms-admin removed under the renamed root
        List<GeneratedFile> files = List.of(
            file("my-app/ms-admin/pom.xml", "<project/>"),
            file("my-app/service-consumer/pom.xml", "<project/>")
        );
        List<GeneratedFile> result = processor.process(files, GenerationContext.from(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("my-app/service-consumer/pom.xml");
    }

    @Test
    void always_keeps_docker_compose_and_root_files() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).anyMatch(e -> e.path().endsWith("docker-compose.yml"));
    }
}
