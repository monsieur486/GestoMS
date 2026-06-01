package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message RabbitMQ décrivant un job batch à exécuter : identifiant du job,
 * identifiant de l'utilisateur demandeur, liste de références à traiter,
 * numéro de tentative et paramètres de simulation d'erreurs.
 */
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
