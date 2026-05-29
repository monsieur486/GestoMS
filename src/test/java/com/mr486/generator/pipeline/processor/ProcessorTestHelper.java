package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProcessorTestHelper {

    private ProcessorTestHelper() {}

    public static GeneratedFile file(String path, String content) {
        return new GeneratedFile(path, content.getBytes(StandardCharsets.UTF_8), false);
    }

    public static GeneratedFile file(String path, String content, boolean executable) {
        return new GeneratedFile(path, content.getBytes(StandardCharsets.UTF_8), executable);
    }

    public static String contentOf(GeneratedFile f) {
        return new String(f.content(), StandardCharsets.UTF_8);
    }

    public static GenerationContext defaultCtx() {
        return GenerationContext.from(new PlatformGenerationRequest());
    }

    public static GenerationContext ctxWithName(String name) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName(name);
        return GenerationContext.from(req);
    }

    public static GenerationContext ctxWithPackage(String groupId, String basePackage) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setGroupId(groupId);
        req.setBasePackage(basePackage);
        return GenerationContext.from(req);
    }

    public static GenerationContext ctxWithFeatures(FeatureOptions features) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setFeatures(features);
        return GenerationContext.from(req);
    }
}
