package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Réécrit le bloc {@code webui:} (dernière section de l'application.yml de ms-webui) avec le
 * catalogue construit depuis {@code resources[]}.
 *
 * <p>Le bloc étant en fin de fichier, on remplace de {@code ^webui:} jusqu'à la fin du contenu —
 * pas de chirurgie d'indentation. Ne s'applique qu'en présence de {@code resources[]}.
 */
@Component
@Order(80)
public class WebUiCatalogRewriter implements CrossCuttingRewriter {

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return CrossCuttingRewriter.hasResources(ctx)
            && f.path().endsWith("/ms-webui/src/main/resources/application.yml");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }
        String text = new String(f.content(), StandardCharsets.UTF_8);
        StringBuilder block = new StringBuilder("webui:\n  resources:\n");
        for (ResourceModuleRequest r : ctx.getRequest().getResources()) {
            block.append("    - serviceName: ").append(r.getServiceName()).append("\n");
            block.append("      routePrefix: ").append(r.getEffectiveRoutePrefix()).append("\n");
            block.append("      label: ").append(r.getClassName()).append("\n");
            block.append("      role: ").append(ResourceNaming.from(r).roleName()).append("\n");
        }
        String newText = text.replaceAll("(?ms)^webui:.*\\z",
                Matcher.quoteReplacement(block.toString().stripTrailing() + "\n"));
        return new GeneratedFile(f.path(), newText.getBytes(StandardCharsets.UTF_8), f.executable());
    }
}
