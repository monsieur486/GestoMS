package com.mr486.msplatform.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Catalogue des resources exposées par l'UI CRUD (injecté par le générateur dans application.yml). */
@ConfigurationProperties(prefix = "client")
public record ClientProperties(List<ResourceEntry> resources, String keycloakAccountUrl) {

    /**
     * Entrée du catalogue décrivant un service CRUD exposé par le client.
     *
     * @param serviceName  nom du service (utilisé comme segment d'URL dans le gateway)
     * @param routePrefix  préfixe de route REST de la ressource (ex. {@code /api/orders})
     * @param label        libellé affiché dans le menu de navigation
     * @param role         nom du rôle (sans le préfixe {@code ROLE_}) requis pour accéder à la ressource
     */
    public record ResourceEntry(String serviceName, String routePrefix, String label, String role) {}
}
