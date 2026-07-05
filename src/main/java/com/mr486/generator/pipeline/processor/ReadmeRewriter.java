package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Réécrit la section « Utilisateurs Keycloak » du README généré pour lister les utilisateurs et
 * rôles de test de chaque ressource métier.
 *
 * <p>Ne s'applique qu'en présence de {@code resources[]} : sans ressource dynamique, le README
 * statique du modèle reste valide.
 */
@Component
@Order(60)
public class ReadmeRewriter implements CrossCuttingRewriter {

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return CrossCuttingRewriter.hasResources(ctx) && f.path().endsWith("/README.md");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }

        final String text = new String(f.content(), StandardCharsets.UTF_8);
        final List<ResourceModuleRequest> resources = ctx.getRequest().getResources();

        StringBuilder table = new StringBuilder();
        table.append("## Utilisateurs Keycloak\n");
        table.append("| Utilisateur | Mot de passe | Rôles |\n");
        table.append("|---|---|---|\n");
        table.append("| `test-admin` | `admin123` | ADMIN, USER_BATCH");
        for (ResourceModuleRequest r : resources) {
            table.append(", ").append(ResourceNaming.from(r).roleName());
        }
        table.append(" |\n");
        table.append("| `test-batch` | `user123` | USER_BATCH |\n");
        for (ResourceModuleRequest r : resources) {
            ResourceNaming n = ResourceNaming.from(r);
            table.append("| `")
                    .append(n.testUser())
                    .append("` | `user123` | ")
                    .append(n.roleName())
                    .append(" |\n");
        }

        String heading = "## Utilisateurs Keycloak";
        int sectionStart = text.indexOf(heading);
        if (sectionStart < 0) {
            return f;
        }

        int nextSection = text.indexOf("\n## ", sectionStart + heading.length());
        String updated;
        if (nextSection < 0) {
            updated = text.substring(0, sectionStart) + table;
        } else {
            updated = text.substring(0, sectionStart) + table + text.substring(nextSection);
        }

        return new GeneratedFile(f.path(), updated.getBytes(StandardCharsets.UTF_8), f.executable());
    }
}
