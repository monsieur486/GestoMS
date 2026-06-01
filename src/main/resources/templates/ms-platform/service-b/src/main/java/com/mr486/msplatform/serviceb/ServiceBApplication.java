package com.mr486.msplatform.serviceb;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du microservice {@code service-b}.
 */
@EnableMongock
@SpringBootApplication
public class ServiceBApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceBApplication.class, args);
    }
}
