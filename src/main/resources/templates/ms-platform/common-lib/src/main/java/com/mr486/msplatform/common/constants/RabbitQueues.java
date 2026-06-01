package com.mr486.msplatform.common.constants;

/**
 * Constantes des noms de queues RabbitMQ utilisées par la plateforme.
 * Classe utilitaire non instanciable.
 */
public final class RabbitQueues {

    /** Queue de soumission des jobs batch vers le consumer. */
    public static final String BATCH_JOBS = "batch.jobs";

    /** Queue de notifications de résultat des jobs batch vers les clients. */
    public static final String BATCH_NOTIFICATIONS = "batch.notifications";

    private RabbitQueues() {
    }
}
