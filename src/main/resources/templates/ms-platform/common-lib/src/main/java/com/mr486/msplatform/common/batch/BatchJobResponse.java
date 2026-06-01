package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO de réponse décrivant l'état complet d'un job batch : statut, compteurs,
 * dates de création et de complétion, liste de fichiers générés, métriques de
 * performance et résultat détaillé par référence.
 */
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
