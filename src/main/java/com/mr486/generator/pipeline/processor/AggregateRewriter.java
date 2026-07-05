package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Régénère l'{@code AggregateController} de service-consumer pour interroger en parallèle les
 * ressources métier de {@code resources[]} et fusionner leurs réponses.
 *
 * <p>Ne s'applique qu'en présence de {@code resources[]} : sans ressource dynamique, le contrôleur
 * statique du modèle reste valide.
 */
@Component
@Order(70)
public class AggregateRewriter implements CrossCuttingRewriter {

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return CrossCuttingRewriter.hasResources(ctx)
            && f.path().contains("/service-consumer/")
            && f.path().endsWith("AggregateController.java");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }
        String original = new String(f.content(), StandardCharsets.UTF_8);
        String pkg = firstPackage(original);
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();

        StringBuilder calls = new StringBuilder();
        StringBuilder puts = new StringBuilder();
        for (int i = 0; i < resources.size(); i++) {
            ResourceModuleRequest r = resources.get(i);
            calls.append("                call(\"lb://").append(r.getServiceName()).append(r.getEffectiveRoutePrefix())
                 .append("\", authorization)").append(i < resources.size() - 1 ? ",\n" : "\n");
            puts.append("                result.put(\"").append(r.getServiceName())
                .append("\", (String) results[").append(i).append("]);\n");
        }

        String body = ""
            + "package " + pkg + ";\n\n"
            + "import lombok.RequiredArgsConstructor;\n"
            + "import org.springframework.http.HttpHeaders;\n"
            + "import org.springframework.security.access.prepost.PreAuthorize;\n"
            + "import org.springframework.web.bind.annotation.*;\n"
            + "import org.springframework.web.reactive.function.client.WebClient;\n"
            + "import reactor.core.publisher.Mono;\n"
            + "import java.util.LinkedHashMap;\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n\n"
            + "/**\n"
            + " * Contrôleur d'agrégation : interroge en parallèle les services métier et fusionne leurs réponses.\n"
            + " */\n"
            + "@RestController\n@RequiredArgsConstructor\n@RequestMapping(\"/api\")\n"
            + "public class AggregateController {\n\n"
            + "    private final WebClient.Builder webClientBuilder;\n\n"
            + "    /**\n"
            + "     * Agrège les réponses des services métier configurés en une seule map.\n"
            + "     *\n"
            + "     * @param authorization l'en-tête {@code Authorization} propagé aux services appelés\n"
            + "     * @return une map {nom de service → corps de réponse}\n"
            + "     */\n"
            + "    @GetMapping(\"/aggregate\")\n    @PreAuthorize(\"hasRole('ADMIN')\")\n"
            + "    public Mono<Map<String, String>> aggregate(\n"
            + "            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {\n"
            + "        return Mono.zip(List.of(\n"
            + calls
            + "            ), results -> {\n"
            + "                Map<String, String> result = new LinkedHashMap<>();\n"
            + puts
            + "                return result;\n"
            + "            });\n"
            + "    }\n\n"
            + "    private Mono<String> call(String uri, String authorization) {\n"
            + "        return webClientBuilder.build().get().uri(uri)\n"
            + "            .header(HttpHeaders.AUTHORIZATION, authorization)\n"
            + "            .retrieve().bodyToMono(String.class);\n"
            + "    }\n"
            + "}\n";
        return new GeneratedFile(f.path(), body.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    // Extrait le nom de package de la première déclaration `package ...;`, ou un défaut.
    private String firstPackage(String text) {
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("package ") && t.endsWith(";")) {
                return t.substring("package ".length(), t.length() - 1).trim();
            }
        }
        return "consumer.controller";
    }
}
