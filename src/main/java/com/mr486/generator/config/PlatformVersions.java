package com.mr486.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Versions centralisées injectées dans la plateforme générée.
 * <p>
 * Bean Spring lié au bloc {@code platform.versions} d'{@code application.yml}. Les valeurs par défaut
 * des champs ci-dessous DOIVENT rester égales aux littéraux présents dans le template
 * ({@code docker-compose.yml}, {@code Dockerfile}, poms) : le VersionInjectionProcessor (Task 2)
 * s'en sert à la fois comme constante de recherche (une instance neuve = littéraux du template) et,
 * via l'instance injectée et surchargée par le YAML, comme valeur cible.
 */
@Component
@ConfigurationProperties("platform.versions")
@Data
public class PlatformVersions {
    /** Image de base des 11 Dockerfile + arg build {@code JAVA_IMAGE}. */
    private String javaImage = "eclipse-temurin:17-jre";
    /** Version du parent {@code spring-boot-starter-parent} (root pom). */
    private String springBoot = "3.5.5";
    /** {@code <spring-cloud.version>} du root pom. */
    private String springCloud = "2025.0.0";
    /** {@code <mongock.version>} du root pom. */
    private String mongock = "5.5.1";
    /** Version de {@code spring-boot-admin-starter-server} (ms-admin). */
    private String springBootAdmin = "3.5.5";
    private String postgres = "16";
    private String keycloak = "26.5.6";
    private String rabbitmq = "3.13-management";
    private String redis = "7-alpine";
    private String mongo = "7";
    private String loki = "3.2.1";
    private String promtail = "3.2.1";
    private String grafana = "11.2.2";
}
