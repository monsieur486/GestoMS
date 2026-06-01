package com.mr486.msplatform.admin;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du microservice {@code ms-admin} (Spring Boot Admin Server).
 */
@EnableAdminServer
@SpringBootApplication
public class AdminApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
