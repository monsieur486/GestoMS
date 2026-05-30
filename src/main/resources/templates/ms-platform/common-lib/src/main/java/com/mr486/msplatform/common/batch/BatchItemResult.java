package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchItemResult {
    private String reference;
    private String fileName;
    private long durationMillis;
    private String instance;
    private boolean success;
    private String error;
}
