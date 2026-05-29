package com.mr486.generator.model;

import com.mr486.generator.dto.PlatformGenerationRequest;
import lombok.Value;
import java.util.Objects;

@Value
public class GenerationContext {
    PlatformGenerationRequest request;
    String targetRoot;
    String sourceRoot;

    public static GenerationContext from(PlatformGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String name = request.getName();
        String target = (name == null || name.isBlank()) ? "ms-platform" : name.trim();
        return new GenerationContext(request, target, "ms-platform");
    }
}
