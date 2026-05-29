package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.pipeline.ZipTemplateLoader;
import com.mr486.generator.zip.GeneratedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformGeneratorService {

    private final ZipTemplateLoader loader;
    // Spring injects and sorts by @Order automatically
    private final List<FileProcessor> processors;

    public List<GeneratedFile> generate(PlatformGenerationRequest request) {
        GenerationContext ctx = GenerationContext.from(request);
        List<GeneratedFile> files = loader.load();
        for (FileProcessor processor : processors) {
            files = processor.process(files, ctx);
        }
        return files;
    }
}
