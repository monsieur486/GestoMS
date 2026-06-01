package com.mr486.msplatform.webui;

import com.mr486.msplatform.webui.config.WebUiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Point d'entrée du microservice {@code ms-webui}.
 */
@SpringBootApplication
@EnableConfigurationProperties(WebUiProperties.class)
public class WebUiApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(WebUiApplication.class, args);
    }
}
