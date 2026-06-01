package com.mr486.msplatform.consumer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrôleur d'agrégation : interroge en parallèle les services métier et fusionne leurs réponses.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AggregateController {

    private final WebClient.Builder webClientBuilder;

    /**
     * Agrège les réponses des services {@code service-a/b/c} en une seule map.
     *
     * @param authorization l'en-tête {@code Authorization} propagé aux services appelés
     * @return une map {nom de service → corps de réponse}
     */
    @GetMapping("/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, String>> aggregate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return Mono.zip(
                call("lb://service-a/api/resources-a", authorization),
                call("lb://service-b/api/resources-b", authorization),
                call("lb://service-c/api/resources-c", authorization))
            .map(tuple -> {
                Map<String, String> result = new LinkedHashMap<>();
                result.put("service-a", tuple.getT1());
                result.put("service-b", tuple.getT2());
                result.put("service-c", tuple.getT3());
                return result;
            });
    }

    private Mono<String> call(String uri, String authorization) {
        return webClientBuilder.build().get().uri(uri)
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .retrieve()
            .bodyToMono(String.class);
    }
}
