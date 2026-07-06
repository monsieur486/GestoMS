package com.mr486.generator.dto;

import lombok.Data;

/**
 * Bascules d'activation des composants optionnels de la plateforme.
 *
 * <p>keycloak (+ ms-auth), redis, rabbitmq, websocket et admin-application sont désormais TOUJOURS
 * installés et n'ont plus de bascule. L'observabilité (loki + promtail + grafana) est pilotée par
 * le champ {@code grafana} de {@link BatchOptions}. Seuls les deux modules ci-dessous restent
 * optionnels.
 */
@Data
public class FeatureOptions {
    /** Si {@code false} (défaut), retire le module {@code ms-admin} (monitoring Spring Boot Admin). */
    private boolean springbootAdmin = false;
    /** Si {@code false} (défaut), retire le module {@code ms-webui} (UI Thymeleaf). [module créé en Phase 2] */
    private boolean webUI = false;
}
