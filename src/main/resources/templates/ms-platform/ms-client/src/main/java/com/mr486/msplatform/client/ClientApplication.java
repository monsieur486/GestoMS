package com.mr486.msplatform.client;

import com.mr486.msplatform.client.config.ClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Point d'entrée du microservice {@code ms-client}.
 */
@SpringBootApplication
@EnableConfigurationProperties(ClientProperties.class)
public class ClientApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }
}
