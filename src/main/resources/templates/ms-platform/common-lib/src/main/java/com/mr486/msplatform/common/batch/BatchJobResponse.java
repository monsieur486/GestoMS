package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobResponse {
    private String jobId;
    private Long userId;
    private BatchJobStatus status;
    private int requestedCount;
    private Instant createdAt;
    private Instant completedAt;
    private String error;
    private List<String> files;
    private int generatedCount;
    private double totalSeconds;
    private double averageSeconds;
    private String instance;
    private int attempt;
    private int maxAttempts;
    private Map<String, Object> result;
}
