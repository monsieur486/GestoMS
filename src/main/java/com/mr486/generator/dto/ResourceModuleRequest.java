package com.mr486.generator.dto;

import lombok.Data;

@Data
public class ResourceModuleRequest {
    private String serviceName;
    private String className;
    private String routePrefix;
    private DatabaseType databaseType;
    private IdType idType;
}
