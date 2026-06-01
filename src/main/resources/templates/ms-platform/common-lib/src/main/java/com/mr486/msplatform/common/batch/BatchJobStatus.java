package com.mr486.msplatform.common.batch;

/**
 * Énumération des états possibles d'un job batch tout au long de son cycle de vie :
 * de la mise en file d'attente ({@code PENDING}) jusqu'à la complétion ou à l'échec
 * définitif ({@code DEAD}).
 */
public enum BatchJobStatus {
    PENDING,
    PROCESSING,
    RETRYING,
    COMPLETED,
    FAILED,
    DEAD
}
