package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Réécrit les routes de {@code ms-gateway/.../application.yml} : retire les routes des services
 * par défaut et ajoute une route par entrée de {@code resources[]}.
 *
 * <p>Sans cette réécriture, la passerelle router­ait vers des services absents ou ne router­ait pas
 * les nouveaux services dynamiques.
 */
@Component
@Order(30)
public class GatewayRewriter implements CrossCuttingRewriter {

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return f.path().equals(ctx.getTargetRoot() + "/ms-gateway/src/main/resources/application.yml");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }
        String text = new String(f.content(), StandardCharsets.UTF_8);
        PlatformGenerationRequest req = ctx.getRequest();

        if (CrossCuttingRewriter.hasResources(ctx)) {
            text = removeGatewayRoute(text, "service-a");
            text = removeGatewayRoute(text, "service-b");
            text = removeGatewayRoute(text, "service-c");
            text = addGatewayRoutes(text, req.getResources());
        }
        return new GeneratedFile(f.path(), text.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    // Retire un bloc de route (`- id: <name>` indenté à 12) jusqu'à la route sœur ou la désindentation.
    private String removeGatewayRoute(String text, String routeId) {
        String startMarker = "            - id: " + routeId;
        return YamlBlocks.removeBlock(text,
            line -> line.equals(startMarker),
            line -> line.startsWith("            - id:") || !line.startsWith("              "));
    }

    // Ajoute une route par ressource, insérée juste avant la clé top-level `eureka:`.
    private String addGatewayRoutes(String text, List<ResourceModuleRequest> resources) {
        StringBuilder newRoutes = new StringBuilder();
        for (ResourceModuleRequest r : resources) {
            newRoutes
                .append("            - id: ").append(r.getServiceName()).append("\n")
                .append("              uri: lb://").append(r.getServiceName()).append("\n")
                .append("              predicates:\n")
                .append("                - Path=/").append(r.getServiceName()).append("/**\n")
                .append("              filters:\n")
                .append("                - StripPrefix=1\n");
        }
        int eurekaIdx = text.indexOf("\neureka:");
        if (eurekaIdx >= 0) {
            return text.substring(0, eurekaIdx + 1) + newRoutes + text.substring(eurekaIdx + 1);
        }
        return (text.endsWith("\n") ? text : text + "\n") + newRoutes;
    }
}
