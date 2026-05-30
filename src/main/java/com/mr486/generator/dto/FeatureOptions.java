package com.mr486.generator.dto;

import lombok.Data;

/**
 * Bascules d'activation des composants optionnels de la plateforme.
 * <p>
 * Quand un drapeau passe à {@code false}, le {@link com.mr486.generator.pipeline.processor.FeatureFilterProcessor}
 * retire les fichiers concernés et le {@link com.mr486.generator.pipeline.processor.CrossCuttingConfigProcessor}
 * nettoie les références correspondantes dans le pom racine, le docker-compose et les routes du gateway.
 */
@Data
public class FeatureOptions {
    /** Si {@code false}, retire {@code keycloak/} ET {@code ms-auth/} (ms-auth dépend de Keycloak). */
    private boolean keycloak = true;
    /** Si {@code false}, retire RedisConfig, RedisJobStore, RedisKeys et le service Docker redis. */
    private boolean redis = true;
    /** Si {@code false}, retire RabbitConfig, BatchNotificationListener et le service Docker rabbitmq. */
    private boolean rabbitmq = true;
    /** Si {@code false}, retire WebSocketConfig et la page batch-notifications.html. */
    private boolean websocket = true;
    /** Si {@code false}, retire le module ms-admin (Spring Boot Admin UI). */
    private boolean admin = true;
    /** Si {@code false} (défaut), retire {@code observability/grafana/}. */
    private boolean grafana = false;
    /** Si {@code false} (défaut), retire {@code observability/loki/} et {@code observability/promtail/}. */
    private boolean loki = false;
}
