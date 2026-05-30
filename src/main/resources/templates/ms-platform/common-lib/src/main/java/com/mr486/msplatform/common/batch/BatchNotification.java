package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchNotification {
    private String jobId;
    private BatchJobStatus status;
    private int generatedCount;
    private double totalSeconds;
    private String instance;
}
