package com.mr486.msplatform.servicec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du microservice {@code service-c}.
 */
@SpringBootApplication
public class ServiceCApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceCApplication.class, args);
    }
}
