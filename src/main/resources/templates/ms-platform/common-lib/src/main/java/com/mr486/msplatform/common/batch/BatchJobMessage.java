package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobMessage {
    private String jobId;
    private Long userId;
    private List<String> references;
    private int attempt;
    private int maxAttempts;
    private int failureRate;
}
