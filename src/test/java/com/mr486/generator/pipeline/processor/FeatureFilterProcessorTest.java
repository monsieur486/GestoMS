package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
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
            file("ms-platform/ms-admin/pom.xml", "<project/>"),
            file("ms-platform/observability/grafana/dashboards/d.json", "{}"),
            file("ms-platform/observability/promtail/config.yml", ""),
            file("ms-platform/service-batch/pom.xml", "<project/>"),
            file("ms-platform/service-consumer/src/main/java/x/RabbitConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/RedisConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/WebSocketConfig.java", "class W{}"),
            file("ms-platform/service-consumer/src/main/resources/static/batch-notifications.html", "<html/>"),
            file("ms-platform/docker-compose.yml", "services:")
        );
    }

    @Test
    void keeps_all_files_when_all_features_enabled() {
        FeatureOptions all = new FeatureOptions();
        // defaults: all enabled except grafana/loki
        all.setGrafana(true);
        all.setLoki(true);
        GenerationContext ctx = ctxWithFeatures(all);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        assertThat(result).hasSize(sampleFiles().size());
    }

    @Test
    void excludes_keycloak_dir_when_keycloak_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setKeycloak(false);
        GenerationContext ctx = ctxWithFeatures(f);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        assertThat(result).noneMatch(e -> e.path().contains("/keycloak/"));
    }

    @Test
    void excludes_ms_admin_when_admin_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setAdmin(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().contains("/ms-admin/"));
    }

    @Test
    void excludes_grafana_dir_when_grafana_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setGrafana(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().contains("/grafana/"));
    }

    @Test
    void excludes_promtail_dir_when_loki_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setLoki(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().contains("/promtail/"));
    }

    @Test
    void excludes_service_batch_when_batch_disabled() {
        FeatureOptions f = new FeatureOptions();
        BatchOptions batch = new BatchOptions();
        batch.setEnabled(false);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setFeatures(f);
        req.setBatch(batch);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).noneMatch(e -> e.path().contains("/service-batch/"));
    }

    @Test
    void excludes_rabbitconfig_when_rabbitmq_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setRabbitmq(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().endsWith("RabbitConfig.java"));
    }

    @Test
    void excludes_redisconfig_when_redis_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setRedis(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().endsWith("RedisConfig.java"));
    }

    @Test
    void excludes_websocketconfig_when_websocket_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setWebsocket(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().endsWith("WebSocketConfig.java"));
        assertThat(result).noneMatch(e -> e.path().endsWith("batch-notifications.html"));
    }

    @Test
    void always_keeps_docker_compose_and_other_root_files() {
        FeatureOptions none = new FeatureOptions();
        none.setKeycloak(false); none.setAdmin(false); none.setRabbitmq(false);
        none.setRedis(false); none.setWebsocket(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(none));
        assertThat(result).anyMatch(e -> e.path().endsWith("docker-compose.yml"));
    }
}
