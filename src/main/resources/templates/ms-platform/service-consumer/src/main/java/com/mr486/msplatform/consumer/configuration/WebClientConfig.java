package com.mr486.msplatform.consumer.configuration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration du {@link WebClient} avec équilibrage de charge côté client.
 */
@Configuration
public class WebClientConfig {

    /**
     * Fournit un {@link WebClient.Builder} annoté {@code @LoadBalanced} pour
     * résoudre les noms de service via Eureka.
     *
     * @return le builder WebClient avec équilibrage de charge activé
     */
    @Bean
    @LoadBalanced
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
