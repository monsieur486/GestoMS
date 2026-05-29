package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class BatchConfigProcessorTest {

    private final BatchConfigProcessor processor = new BatchConfigProcessor();

    private static final String SAMPLE_ENV =
        "BATCH_REPLICAS=4\n" +
        "BATCH_FILE_CONCURRENCY=5\n" +
        "BATCH_MIN_DELAY_MS=500\n" +
        "BATCH_MAX_DELAY_MS=1500\n" +
        "BATCH_MEMORY_LIMIT=768m\n";

    private GenerationContext ctxWithBatch(int replicas, int concurrency,
                                           long minDelay, long maxDelay, String memory) {
        BatchOptions b = new BatchOptions();
        b.setReplicas(replicas);
        b.setFileConcurrency(concurrency);
        b.setMinDelayMs(minDelay);
        b.setMaxDelayMs(maxDelay);
        b.setMemoryLimit(memory);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setBatch(b);
        return GenerationContext.from(req);
    }

    @Test
    void replaces_all_batch_values_in_env_file() {
        GenerationContext ctx = ctxWithBatch(8, 10, 200, 800, "512m");
        List<GeneratedFile> result = processor.process(
            List.of(file("ms-platform/.env", SAMPLE_ENV)), ctx);
        String content = contentOf(result.get(0));
        assertThat(content).contains("BATCH_REPLICAS=8");
        assertThat(content).contains("BATCH_FILE_CONCURRENCY=10");
        assertThat(content).contains("BATCH_MIN_DELAY_MS=200");
        assertThat(content).contains("BATCH_MAX_DELAY_MS=800");
        assertThat(content).contains("BATCH_MEMORY_LIMIT=512m");
    }

    @Test
    void is_noop_when_batch_disabled() {
        BatchOptions b = new BatchOptions();
        b.setEnabled(false);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setBatch(b);
        GenerationContext ctx = GenerationContext.from(req);
        List<GeneratedFile> input = List.of(file("ms-platform/.env", SAMPLE_ENV));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).isEqualTo(SAMPLE_ENV);
    }

    @Test
    void is_noop_when_defaults_unchanged() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = List.of(file("ms-platform/.env", SAMPLE_ENV));
        List<GeneratedFile> result = processor.process(input, ctx);
        // Same list reference proves the isDefault() guard fired (not just no-op replacements)
        assertThat(result).isSameAs(input);
    }
}
