package com.mr486.msplatform.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Catalogue des resources exposées par l'UI CRUD (injecté par le générateur dans application.yml). */
@ConfigurationProperties(prefix = "client")
public record ClientProperties(List<ResourceEntry> resources) {

    public record ResourceEntry(String serviceName, String routePrefix, String label, String role) {}
}
