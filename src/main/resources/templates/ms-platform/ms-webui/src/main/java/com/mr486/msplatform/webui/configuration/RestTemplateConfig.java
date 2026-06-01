package com.mr486.msplatform.webui.configuration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** Configuration du {@link RestTemplate} partagé : timeout de connexion 3 s, lecture 5 s. */
@Configuration
public class RestTemplateConfig {

    /**
     * Crée et expose le {@link RestTemplate} avec les timeouts définis par défaut.
     *
     * @param builder le constructeur fourni par Spring Boot
     * @return une instance {@link RestTemplate} prête à l'emploi
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
