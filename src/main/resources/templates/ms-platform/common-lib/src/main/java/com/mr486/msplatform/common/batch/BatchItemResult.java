package com.mr486.msplatform.common.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO représentant le résultat du traitement d'un élément individuel dans un job batch :
 * référence traitée, fichier généré, durée, instance ayant exécuté le traitement
 * et éventuelle erreur.
 */
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
