package com.mr486.msplatform.auth.configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration Spring exposant un bean {@link RestTemplate} partagé
 * pour les appels HTTP sortants vers Keycloak.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Crée et expose un {@link RestTemplate} avec la configuration par défaut.
     *
     * @return une instance de {@link RestTemplate}
     */
    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
