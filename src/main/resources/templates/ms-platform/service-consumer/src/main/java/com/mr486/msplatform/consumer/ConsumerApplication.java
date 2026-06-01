package com.mr486.msplatform.consumer;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du microservice {@code service-consumer}.
 */
@EnableRabbit
@SpringBootApplication
public class ConsumerApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
