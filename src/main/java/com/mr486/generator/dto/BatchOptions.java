package com.mr486.generator.dto;

import lombok.Data;

@Data
public class BatchOptions {
    private boolean enabled = true;
    private int replicas = 4;
    private int fileConcurrency = 5;
    private long minDelayMs = 500;
    private long maxDelayMs = 1500;
    private String memoryLimit = "768m";
}
