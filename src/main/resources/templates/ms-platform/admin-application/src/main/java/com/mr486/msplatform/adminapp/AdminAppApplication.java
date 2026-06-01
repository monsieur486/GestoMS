package com.mr486.msplatform.adminapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'application d'administration ({@code admin-application}).
 */
@SpringBootApplication
public class AdminAppApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminAppApplication.class, args);
    }
}
