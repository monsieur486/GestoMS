package com.mr486.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du générateur de plateforme microservices Spring Boot.
 *
 * <p>Démarre le serveur HTTP qui expose {@link com.mr486.generator.controller.PlatformGeneratorController}.
 * Le générateur ne génère pas la plateforme à partir de zéro : il part d'un ZIP modèle embarqué
 * dans le classpath ({@code templates/ms-platform-template.zip}) et le transforme via un pipeline
 * de {@link com.mr486.generator.pipeline.FileProcessor}.
 */
@SpringBootApplication
public class SpringbootPlatformGeneratorApplication {
    /**
     * Démarre le contexte Spring Boot du générateur.
     *
     * @param args arguments de ligne de commande transmis à Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringbootPlatformGeneratorApplication.class, args);
    }
}
