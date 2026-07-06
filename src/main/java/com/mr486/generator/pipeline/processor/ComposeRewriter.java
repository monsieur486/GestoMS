package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Synchronise {@code docker-compose.yml} : retire les blocs de services désactivés/par défaut,
 * nettoie les {@code depends_on} orphelins, retire les volumes correspondants et ajoute un bloc
 * par entrée de {@code resources[]} (service applicatif + base de données + volume).
 *
 * <p>Sans cette réécriture, {@code docker compose up} refuserait le fichier (services absents,
 * {@code depends_on} pointant vers un bloc retiré).
 */
@Component
@Order(20)
public class ComposeRewriter implements CrossCuttingRewriter {

    private static final Pattern DEPENDS_ON_PATTERN = Pattern.compile("(depends_on: \\[)([^\\]]+)(\\])");

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return f.path().equals(ctx.getTargetRoot() + "/docker-compose.yml");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }
        String text = new String(f.content(), StandardCharsets.UTF_8);
        List<String> removedBlocks = blocksToRemove(ctx);
        for (String block : removedBlocks) {
            text = removeServiceBlock(text, block);
        }
        text = cleanDependsOnReferences(text, removedBlocks);
        text = removeDefaultVolumes(text, ctx);
        text = addResourceBlocks(text, ctx);
        return new GeneratedFile(f.path(), text.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    // Retire des `depends_on: [a, b, c]` inline les entrées correspondant à un service retiré.
    private String cleanDependsOnReferences(String text, List<String> removedBlocks) {
        if (removedBlocks.isEmpty()) {
            return text;
        }
        Set<String> removed = new HashSet<>(removedBlocks);
        Matcher m = DEPENDS_ON_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String[] deps = m.group(2).split(",\\s*");
            List<String> kept = new ArrayList<>();
            for (String dep : deps) {
                String trimmed = dep.trim();
                if (!removed.contains(trimmed)) {
                    kept.add(trimmed);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(
                m.group(1) + String.join(", ", kept) + m.group(3)
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Retire les volumes des services par défaut (indentés à 2) remplacés par des ressources.
    private String removeDefaultVolumes(String text, GenerationContext ctx) {
        if (!CrossCuttingRewriter.hasResources(ctx)) {
            return text;
        }
        String out = text;
        for (String vol : List.of("service_a_db_data", "service_b_db_data")) {
            out = out.replaceAll("(?m)^  " + Pattern.quote(vol) + ":[^\\n]*\\n?", "");
        }
        return out;
    }

    // Blocs de services à retirer selon features/batch/resources.
    private List<String> blocksToRemove(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        BatchOptions b = req.getBatch();
        final boolean hasResources = CrossCuttingRewriter.hasResources(ctx);

        List<String> blocks = new ArrayList<>();
        if (!b.isEnabled()) {
            blocks.add("service-batch");
        }
        if (!b.isGrafana()) {
            blocks.add("loki");
            blocks.add("promtail");
            blocks.add("grafana");
        }
        if (!f.isSpringbootAdmin()) {
            blocks.add("ms-admin");
        }
        if (!f.isWebUI()) {
            blocks.add("ms-webui");
        }
        if (hasResources) {
            blocks.add("service-a-db");
            blocks.add("service-b-db");
            blocks.add("service-a");
            blocks.add("service-b");
            blocks.add("service-c");
        }
        return blocks;
    }

    // Insère un bloc service (+ base + volume) par ressource, autour des sections services/volumes.
    private String addResourceBlocks(String text, GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        if (req.getResources() == null || req.getResources().isEmpty()) {
            return text;
        }

        StringBuilder newServices = new StringBuilder();
        StringBuilder newVolumes = new StringBuilder();
        for (ResourceModuleRequest r : req.getResources()) {
            newServices.append(buildResourceServiceBlock(r));
            newVolumes.append(buildResourceVolumeEntry(r));
        }

        String out = text;
        int volIdx = out.indexOf("\nvolumes:");
        if (volIdx >= 0) {
            out = out.substring(0, volIdx + 1) + newServices + out.substring(volIdx + 1);
            if (newVolumes.length() > 0) {
                if (!out.endsWith("\n")) {
                    out = out + "\n";
                }
                out = out + newVolumes;
            }
        } else {
            out = (out.endsWith("\n") ? out : out + "\n") + newServices;
            if (newVolumes.length() > 0) {
                out = out + "\nvolumes:\n" + newVolumes;
            }
        }
        return out;
    }

    // Construit le bloc compose (base + service applicatif) d'une ressource selon son type de base.
    private String buildResourceServiceBlock(ResourceModuleRequest r) {
        DatabaseType db = r.getDatabaseType() == null ? DatabaseType.POSTGRES : r.getDatabaseType();
        String name  = r.getServiceName();
        String snake = name.replace("-", "_");
        String upper = snake.toUpperCase(Locale.ROOT);

        StringBuilder sb = new StringBuilder();
        switch (db) {
            case POSTGRES -> {
                sb.append("  ").append(name).append("-db:\n")
                  .append("    image: postgres:16\n")
                  .append("    env_file: [.env]\n")
                  .append("    environment:\n")
                  .append("      POSTGRES_DB: ").append(snake).append("_db\n")
                  .append("      POSTGRES_USER: ").append(snake).append("\n")
                  .append("      POSTGRES_PASSWORD: ").append(snake).append("\n")
                  .append("    volumes: [").append(snake).append("_db_data:/var/lib/postgresql/data]\n")
                  .append("    healthcheck:\n")
                  .append("      test: [\"CMD-SHELL\", \"pg_isready -U ").append(snake).append("\"]\n")
                  .append("      interval: 5s\n")
                  .append("      timeout: 5s\n")
                  .append("      retries: 20\n\n");
                appendAppService(sb, name, "[ms-eureka, keycloak, " + name + "-db]");
                sb.append("      ").append(upper).append("_DATASOURCE_URL: jdbc:postgresql://")
                  .append(name).append("-db:5432/").append(snake).append("_db\n")
                  .append("      ").append(upper).append("_DB_USERNAME: ").append(snake).append("\n")
                  .append("      ").append(upper).append("_DB_PASSWORD: ").append(snake).append("\n");
            }
            case MONGO -> {
                sb.append("  ").append(name).append("-db:\n")
                  .append("    image: mongo:7\n")
                  .append("    env_file: [.env]\n")
                  .append("    environment:\n")
                  .append("      MONGO_INITDB_ROOT_USERNAME: ").append(snake).append("\n")
                  .append("      MONGO_INITDB_ROOT_PASSWORD: ").append(snake).append("\n")
                  .append("      MONGO_INITDB_DATABASE: ").append(snake).append("_db\n")
                  .append("    volumes: [").append(snake).append("_db_data:/data/db]\n\n");
                appendAppService(sb, name, "[ms-eureka, keycloak, " + name + "-db]");
                sb.append("      ").append(upper).append("_MONGO_URI: mongodb://").append(snake)
                  .append(":").append(snake).append("@").append(name).append("-db:27017/")
                  .append(snake).append("_db?authSource=admin\n");
            }
            case H2 -> appendAppService(sb, name, "[ms-eureka, keycloak]");
            default -> throw new IllegalStateException("Type de base non supporté : " + db);
        }
        sb.append("\n");
        return sb.toString();
    }

    // Ajoute le bloc du service applicatif (build, env, depends_on, variables communes).
    private void appendAppService(StringBuilder sb, String name, String deps) {
        sb.append("  ").append(name).append(":\n")
          .append("    build: ./").append(name).append("\n")
          .append("    env_file: [.env]\n")
          .append("    depends_on: ").append(deps).append("\n")
          .append("    environment:\n")
          .append("      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n")
          .append("      KEYCLOAK_ISSUER_URI: http://localhost:8089/realms/ms-realm\n");
    }

    // Entrée de volume nommée pour une ressource (vide pour H2, sans base).
    private String buildResourceVolumeEntry(ResourceModuleRequest r) {
        DatabaseType db = r.getDatabaseType() == null ? DatabaseType.POSTGRES : r.getDatabaseType();
        if (db == DatabaseType.H2) {
            return "";
        }
        return "  " + r.getServiceName().replace("-", "_") + "_db_data:\n";
    }

    // Retire un bloc de service top-level (indenté à 2) : l'en-tête + tout ce qui est plus indenté.
    private String removeServiceBlock(String text, String blockName) {
        String startMarker = "  " + blockName + ":";
        return YamlBlocks.removeBlock(text,
            line -> line.equals(startMarker)
                || (line.startsWith(startMarker)
                    && line.length() > startMarker.length()
                    && Character.isWhitespace(line.charAt(startMarker.length()))),
            line -> !line.startsWith(" ")
                || (line.length() > 2 && line.charAt(0) == ' '
                    && line.charAt(1) == ' ' && line.charAt(2) != ' '));
    }
}
