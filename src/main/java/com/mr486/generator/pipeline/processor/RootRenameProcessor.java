package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Order(10)
public class RootRenameProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        if (ctx.getSourceRoot().equals(ctx.getTargetRoot())) return files;
        String prefix = ctx.getSourceRoot() + "/";
        return files.stream()
            .map(f -> f.path().startsWith(prefix)
                ? new GeneratedFile(ctx.getTargetRoot() + f.path().substring(prefix.length() - 1), f.content(), f.executable())
                : f)
            .toList();
    }
}
