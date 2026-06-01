package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notification RabbitMQ émise par le consumer batch à destination des clients
 * en attente de résultat : contient l'identifiant du job, le statut final,
 * les compteurs de génération et les métriques de performance.
 */
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
