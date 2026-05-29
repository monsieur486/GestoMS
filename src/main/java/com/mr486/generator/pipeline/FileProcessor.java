package com.mr486.generator.pipeline;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;

public interface FileProcessor {
    List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx);
}
