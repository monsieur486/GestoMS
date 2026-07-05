package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Filtre les fichiers du modèle selon les bascules de {@link com.mr486.generator.dto.FeatureOptions}
 * et l'état de {@link com.mr486.generator.dto.BatchOptions}.
 *
 * <p>N'opère que sur les <em>chemins</em> ; la suppression cohérente des références à ces fichiers dans
 * les fichiers transverses (pom racine, docker-compose, routes du gateway) est faite plus tard par
 * {@link CrossCuttingConfigProcessor}.
 */
@Component
@Order(20)
public class FeatureFilterProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        FeatureOptions f = ctx.getRequest().getFeatures();
        BatchOptions b = ctx.getRequest().getBatch();
        String root = ctx.getTargetRoot();
        return files.stream()
            .filter(e -> include(e.path(), root, f, b))
            .toList();
    }

    private boolean include(String path, String root, FeatureOptions f, BatchOptions b) {
        String rel = ProcessorUtils.relative(path, root);
        if (!f.isSpringbootAdmin() && rel.startsWith("ms-admin/")) {
            return false;
        }
        if (!f.isWebUI() && rel.startsWith("ms-webui/")) {
            return false;
        }
        if (!b.isGrafana() && rel.startsWith("observability/")) {
            return false;
        }
        if (!b.isEnabled() && rel.startsWith("service-batch/")) {
            return false;
        }
        return true;
    }
}
