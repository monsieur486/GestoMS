package com.mr486.generator.dto;

import lombok.Data;

/**
 * Paramètres de configuration de {@code service-batch}.
 * <p>
 * Les valeurs sont injectées par le {@link com.mr486.generator.pipeline.processor.BatchConfigProcessor}
 * dans le {@code .env} et le {@code docker-compose.yml} de la plateforme générée. Quand
 * {@link #enabled} est {@code false}, le service batch est entièrement retiré (au même titre que
 * lorsque {@code features.rabbitmq=false}, car batch dépend de RabbitMQ).
 */
@Data
public class BatchOptions {
    /** Si {@code false}, retire le module et le service Docker service-batch. */
    private boolean enabled = true;
    /** Nombre de replicas du service-batch lancés par Docker Compose. */
    private int replicas = 4;
    /** Nombre de fichiers traités en parallèle par chaque replica. */
    private int fileConcurrency = 5;
    /** Délai minimum (ms) entre deux traitements de fichier — sert au throttling. */
    private long minDelayMs = 500;
    /** Délai maximum (ms) entre deux traitements de fichier — borne supérieure du jitter. */
    private long maxDelayMs = 1500;
    /** Limite mémoire Docker pour chaque replica (ex: "768m", "1g"). */
    private String memoryLimit = "768m";
}
