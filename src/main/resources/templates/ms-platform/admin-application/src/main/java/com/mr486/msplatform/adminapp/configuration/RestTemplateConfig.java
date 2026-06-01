package com.mr486.msplatform.adminapp.configuration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration du {@link RestTemplate} partagé par l'application (timeouts de connexion et lecture).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Crée un {@link RestTemplate} avec un délai de connexion de 3 s et un délai de lecture de 5 s.
     *
     * @param builder le constructeur Spring Boot pré-configuré
     * @return l'instance {@link RestTemplate} prête à l'emploi
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
