package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Injecte les valeurs de {@link BatchOptions} dans les fichiers de configuration de la plateforme
 * ({@code .env}, blocs {@code docker-compose.yml}).
 * <p>
 * Court-circuité si le batch est désactivé (le service est déjà supprimé par
 * {@link FeatureFilterProcessor}) ou si toutes les valeurs sont égales aux défauts du template.
 */
@Component
@Order(40)
public class BatchConfigProcessor implements FileProcessor {

    private static final BatchOptions DEFAULTS = new BatchOptions();

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        BatchOptions b = ctx.getRequest().getBatch();
        if (!b.isEnabled()) return files;
        if (isDefault(b)) return files;
        return files.stream().map(f -> replace(f, b)).toList();
    }

    private GeneratedFile replace(GeneratedFile f, BatchOptions b) {
        if (containsNullByte(f.content())) return f;
        try {
            String text = new String(f.content(), StandardCharsets.UTF_8);
            text = text.replace("BATCH_REPLICAS="      + DEFAULTS.getReplicas(),      "BATCH_REPLICAS="      + b.getReplicas());
            text = text.replace("BATCH_FILE_CONCURRENCY=" + DEFAULTS.getFileConcurrency(), "BATCH_FILE_CONCURRENCY=" + b.getFileConcurrency());
            text = text.replace("BATCH_MIN_DELAY_MS="  + DEFAULTS.getMinDelayMs(),    "BATCH_MIN_DELAY_MS="  + b.getMinDelayMs());
            text = text.replace("BATCH_MAX_DELAY_MS="  + DEFAULTS.getMaxDelayMs(),    "BATCH_MAX_DELAY_MS="  + b.getMaxDelayMs());
            text = text.replace("BATCH_MEMORY_LIMIT="  + DEFAULTS.getMemoryLimit(),   "BATCH_MEMORY_LIMIT="  + b.getMemoryLimit());
            return new GeneratedFile(f.path(), text.getBytes(StandardCharsets.UTF_8), f.executable());
        } catch (Exception e) {
            return f;
        }
    }

    private boolean isDefault(BatchOptions b) {
        return b.getReplicas()       == DEFAULTS.getReplicas()
            && b.getFileConcurrency() == DEFAULTS.getFileConcurrency()
            && b.getMinDelayMs()      == DEFAULTS.getMinDelayMs()
            && b.getMaxDelayMs()      == DEFAULTS.getMaxDelayMs()
            && b.getMemoryLimit().equals(DEFAULTS.getMemoryLimit());
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
