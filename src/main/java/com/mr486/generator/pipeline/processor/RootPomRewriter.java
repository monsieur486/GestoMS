package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Réécrit le bloc {@code <modules>} du pom racine pour lister exactement les modules générés.
 *
 * <p>Sans cette réécriture, le pom conserverait les modules service-a/b/c par défaut (ou omettrait
 * les {@code resources[]}), et {@code mvn package} échouerait sur un module absent.
 */
@Component
@Order(10)
public class RootPomRewriter implements CrossCuttingRewriter {

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return f.path().equals(ctx.getTargetRoot() + "/pom.xml");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }
        String text = new String(f.content(), StandardCharsets.UTF_8);
        StringBuilder block = new StringBuilder("<modules>\n");
        for (String m : desiredModules(ctx)) {
            block.append("    <module>").append(m).append("</module>\n");
        }
        block.append("  </modules>");
        String newText = text.replaceAll(
                "(?s)<modules>.*?</modules>",
                Matcher.quoteReplacement(block.toString())
        );
        return new GeneratedFile(f.path(), newText.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    // Liste ordonnée des modules à déclarer dans le pom racine selon features/batch/resources.
    private List<String> desiredModules(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        final FeatureOptions f = req.getFeatures();
        final BatchOptions b = req.getBatch();
        final boolean hasResources = CrossCuttingRewriter.hasResources(ctx);

        List<String> modules = new ArrayList<>();
        modules.add("common-lib");
        modules.add("ms-eureka");
        modules.add("ms-gateway");
        modules.add("ms-auth");                 // keycloak permanent
        modules.add("admin-application");        // toujours installé
        if (!hasResources) {
            modules.add("service-a");
            modules.add("service-b");
            modules.add("service-c");
        }
        modules.add("service-consumer");
        if (b.isEnabled()) {
            modules.add("service-batch");
        }
        if (f.isSpringbootAdmin()) {
            modules.add("ms-admin");
        }
        if (f.isWebUI()) {
            modules.add("ms-webui");
        }
        if (hasResources) {
            for (ResourceModuleRequest r : req.getResources()) {
                modules.add(r.getServiceName());
            }
        }
        return modules;
    }
}
