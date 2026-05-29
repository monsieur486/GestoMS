package com.mr486.generator.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlatformGenerationRequest {
    private String name = "ms-platform";
    private String groupId = "com.mr486";
    private String basePackage = "com.mr486.msplatform";
    private String javaVersion = "17";
    private List<ResourceModuleRequest> resources = new ArrayList<>();
    private BatchOptions batch = new BatchOptions();
    private FeatureOptions features = new FeatureOptions();
}
