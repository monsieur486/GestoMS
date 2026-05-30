package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(60)
public class CrossCuttingConfigProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        String rootPomPath  = ctx.getTargetRoot() + "/pom.xml";
        String composePath  = ctx.getTargetRoot() + "/docker-compose.yml";
        String gatewayYml   = ctx.getTargetRoot() + "/ms-gateway/src/main/resources/application.yml";

        List<GeneratedFile> result = new ArrayList<>(files.size());
        for (GeneratedFile f : files) {
            if (f.path().equals(rootPomPath))      result.add(rewriteRootPom(f, ctx));
            else if (f.path().equals(composePath)) result.add(rewriteCompose(f, ctx));
            else if (f.path().equals(gatewayYml))  result.add(rewriteGatewayYml(f, ctx));
            else                                    result.add(f);
        }
        return result;
    }

    // ── Root pom <modules> ────────────────────────────────────────────────────

    private GeneratedFile rewriteRootPom(GeneratedFile f, GenerationContext ctx) {
        if (containsNullByte(f.content())) return f;
        String text = new String(f.content(), StandardCharsets.UTF_8);
        StringBuilder block = new StringBuilder("<modules>\n");
        for (String m : desiredModules(ctx)) {
            block.append("    <module>").append(m).append("</module>\n");
        }
        block.append("  </modules>");
        String newText = text.replaceAll("(?s)<modules>.*?</modules>", Matcher.quoteReplacement(block.toString()));
        return new GeneratedFile(f.path(), newText.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    private List<String> desiredModules(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        BatchOptions b = req.getBatch();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        List<String> modules = new ArrayList<>();
        modules.add("common-lib");
        modules.add("ms-eureka");
        modules.add("ms-gateway");
        if (f.isAdmin())                       modules.add("ms-admin");
        if (!hasResources) {
            modules.add("service-a");
            modules.add("service-b");
            modules.add("service-c");
        }
        modules.add("service-consumer");
        if (f.isRabbitmq() && b.isEnabled())   modules.add("service-batch");
        if (f.isKeycloak())                    modules.add("ms-auth");
        if (hasResources) {
            for (ResourceModuleRequest r : req.getResources()) modules.add(r.getServiceName());
        }
        return modules;
    }

    // ── docker-compose service blocks ─────────────────────────────────────────

    private GeneratedFile rewriteCompose(GeneratedFile f, GenerationContext ctx) {
        if (containsNullByte(f.content())) return f;
        String text = new String(f.content(), StandardCharsets.UTF_8);
        List<String> removedBlocks = blocksToRemove(ctx);
        for (String block : removedBlocks) {
            text = removeServiceBlock(text, block);
        }
        text = cleanDependsOnReferences(text, removedBlocks);
        for (String vol : volumesToRemove(ctx)) {
            text = removeVolumeEntry(text, vol);
        }
        text = addResourceBlocks(text, ctx);
        return new GeneratedFile(f.path(), text.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    /**
     * For every `depends_on: [a, b, c]` line, drop entries that match a removed service.
     * Only handles the inline-array form; multi-line `depends_on:\n  x:\n    condition: ...`
     * is left untouched (in this template it appears only for keycloak depending on keycloak-db,
     * which is removed as a whole block when keycloak=false).
     */
    private String cleanDependsOnReferences(String text, List<String> removedBlocks) {
        if (removedBlocks.isEmpty()) return text;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(depends_on: \\[)([^\\]]+)(\\])");
        java.util.regex.Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String[] deps = m.group(2).split(",\\s*");
            List<String> kept = new ArrayList<>();
            for (String dep : deps) {
                String trimmed = dep.trim();
                if (!removedBlocks.contains(trimmed)) kept.add(trimmed);
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                m.group(1) + String.join(", ", kept) + m.group(3)
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private List<String> volumesToRemove(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        List<String> vols = new ArrayList<>();
        if (!f.isKeycloak()) vols.add("keycloak_db_data");
        if (!f.isRedis())    vols.add("redis_data");
        if (hasResources) {
            vols.add("service_a_db_data");
            vols.add("service_b_db_data");
        }
        return vols;
    }

    private String removeVolumeEntry(String text, String volumeName) {
        return text.replaceAll("(?m)^  " + Pattern.quote(volumeName) + ":[^\\n]*\\n?", "");
    }

    private List<String> blocksToRemove(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        BatchOptions b = req.getBatch();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        List<String> blocks = new ArrayList<>();
        if (!f.isKeycloak()) {
            blocks.add("keycloak-db");
            blocks.add("keycloak");
            blocks.add("ms-auth");
        }
        if (!f.isAdmin())                       blocks.add("ms-admin");
        if (!f.isRabbitmq())                    blocks.add("rabbitmq");
        if (!f.isRedis())                       blocks.add("redis");
        if (!f.isRabbitmq() || !b.isEnabled())  blocks.add("service-batch");
        if (!f.isLoki()) { blocks.add("loki"); blocks.add("promtail"); }
        if (!f.isGrafana())                     blocks.add("grafana");
        if (hasResources) {
            blocks.add("service-a-db");
            blocks.add("service-b-db");
            blocks.add("service-a");
            blocks.add("service-b");
            blocks.add("service-c");
        }
        return blocks;
    }

    private String addResourceBlocks(String text, GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        if (req.getResources() == null || req.getResources().isEmpty()) return text;
        boolean keycloak = req.getFeatures().isKeycloak();

        StringBuilder newServices = new StringBuilder();
        StringBuilder newVolumes = new StringBuilder();
        for (ResourceModuleRequest r : req.getResources()) {
            newServices.append(buildResourceServiceBlock(r, keycloak));
            newVolumes.append(buildResourceVolumeEntry(r));
        }

        int volIdx = text.indexOf("\nvolumes:");
        if (volIdx >= 0) {
            text = text.substring(0, volIdx + 1) + newServices + text.substring(volIdx + 1);
            if (newVolumes.length() > 0) {
                if (!text.endsWith("\n")) text = text + "\n";
                text = text + newVolumes;
            }
        } else {
            text = (text.endsWith("\n") ? text : text + "\n") + newServices;
            if (newVolumes.length() > 0) text = text + "\nvolumes:\n" + newVolumes;
        }
        return text;
    }

    private String buildResourceServiceBlock(ResourceModuleRequest r, boolean keycloak) {
        DatabaseType db = r.getDatabaseType() == null ? DatabaseType.POSTGRES : r.getDatabaseType();
        String name = r.getServiceName();
        String snake = name.replace("-", "_");
        String upper = snake.toUpperCase();
        String depsApp = keycloak
            ? (db == DatabaseType.H2 ? "[ms-eureka, keycloak]" : "[ms-eureka, keycloak, " + name + "-db]")
            : (db == DatabaseType.H2 ? "[ms-eureka]" : "[ms-eureka, " + name + "-db]");
        String kcEnv = keycloak
            ? "      KEYCLOAK_ISSUER_URI: http://localhost:8089/realms/ms-realm\n"
            : "";

        StringBuilder sb = new StringBuilder();
        switch (db) {
            case MONGO -> sb
                .append("  ").append(name).append("-db:\n")
                .append("    image: mongo:7\n")
                .append("    env_file: [.env]\n")
                .append("    environment:\n")
                .append("      MONGO_INITDB_ROOT_USERNAME: ").append(snake).append("\n")
                .append("      MONGO_INITDB_ROOT_PASSWORD: ").append(snake).append("\n")
                .append("      MONGO_INITDB_DATABASE: ").append(snake).append("_db\n")
                .append("    volumes: [").append(snake).append("_db_data:/data/db]\n\n")
                .append("  ").append(name).append(":\n")
                .append("    build: ./").append(name).append("\n")
                .append("    env_file: [.env]\n")
                .append("    depends_on: ").append(depsApp).append("\n")
                .append("    environment:\n")
                .append("      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n")
                .append(kcEnv)
                .append("      ").append(upper).append("_MONGO_URI: mongodb://").append(snake).append(":").append(snake)
                .append("@").append(name).append("-db:27017/").append(snake).append("_db?authSource=admin\n");
            case H2 -> sb
                .append("  ").append(name).append(":\n")
                .append("    build: ./").append(name).append("\n")
                .append("    env_file: [.env]\n")
                .append("    depends_on: ").append(depsApp).append("\n")
                .append("    environment:\n")
                .append("      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n")
                .append(kcEnv);
            default -> sb     // POSTGRES
                .append("  ").append(name).append("-db:\n")
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
                .append("      retries: 20\n\n")
                .append("  ").append(name).append(":\n")
                .append("    build: ./").append(name).append("\n")
                .append("    env_file: [.env]\n")
                .append("    depends_on: ").append(depsApp).append("\n")
                .append("    environment:\n")
                .append("      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n")
                .append(kcEnv)
                .append("      ").append(upper).append("_DATASOURCE_URL: jdbc:postgresql://").append(name).append("-db:5432/").append(snake).append("_db\n")
                .append("      ").append(upper).append("_DB_USERNAME: ").append(snake).append("\n")
                .append("      ").append(upper).append("_DB_PASSWORD: ").append(snake).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildResourceVolumeEntry(ResourceModuleRequest r) {
        DatabaseType db = r.getDatabaseType() == null ? DatabaseType.POSTGRES : r.getDatabaseType();
        if (db == DatabaseType.H2) return "";
        return "  " + r.getServiceName().replace("-", "_") + "_db_data:\n";
    }

    // ── ms-gateway routes ─────────────────────────────────────────────────────

    private GeneratedFile rewriteGatewayYml(GeneratedFile f, GenerationContext ctx) {
        if (containsNullByte(f.content())) return f;
        String text = new String(f.content(), StandardCharsets.UTF_8);
        PlatformGenerationRequest req = ctx.getRequest();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        if (!req.getFeatures().isKeycloak()) {
            text = removeGatewayRoute(text, "ms-auth");
        }
        if (hasResources) {
            text = removeGatewayRoute(text, "service-a");
            text = removeGatewayRoute(text, "service-b");
            text = removeGatewayRoute(text, "service-c");
            text = addGatewayRoutes(text, req.getResources());
        }
        return new GeneratedFile(f.path(), text.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    /**
     * Remove a single route block from the gateway's `spring.cloud.gateway.server.webflux.routes:`
     * list. Each route starts at `            - id: <name>` (12-space indent) and ends at the
     * next sibling `- id:` or any line with strictly less indent (e.g. the `eureka:` top-level key).
     */
    private String removeGatewayRoute(String text, String routeId) {
        String[] lines = text.split("\n", -1);
        String startMarker = "            - id: " + routeId;
        int startIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals(startMarker)) { startIdx = i; break; }
        }
        if (startIdx == -1) return text;
        int endIdx = lines.length;
        for (int i = startIdx + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("            - id:")) { endIdx = i; break; }
            if (line.isEmpty()) continue;
            if (!line.startsWith("              ")) { endIdx = i; break; }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= startIdx && i < endIdx) continue;
            out.append(lines[i]);
            if (i < lines.length - 1) out.append("\n");
        }
        return out.toString();
    }

    /**
     * Append one route block per resource[] entry, inserted just before the top-level `eureka:`
     * key (which sits right after the routes list in the template). Each new route uses
     * `Path=/{serviceName}/**` + `StripPrefix=1`, matching the convention of the default services.
     */
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

    /**
     * Remove a 2-space-indented top-level docker-compose service block (the block name
     * line plus everything more deeply indented, including trailing blank lines).
     */
    private String removeServiceBlock(String text, String blockName) {
        String[] lines = text.split("\n", -1);
        String startMarker = "  " + blockName + ":";

        int startIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.equals(startMarker)
                || (line.startsWith(startMarker)
                    && line.length() > startMarker.length()
                    && Character.isWhitespace(line.charAt(startMarker.length())))) {
                startIdx = i;
                break;
            }
        }
        if (startIdx == -1) return text;

        int endIdx = lines.length;
        for (int i = startIdx + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            if (!line.startsWith(" ")) { endIdx = i; break; }
            if (line.length() > 2 && line.charAt(0) == ' ' && line.charAt(1) == ' ' && line.charAt(2) != ' ') {
                endIdx = i; break;
            }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= startIdx && i < endIdx) continue;
            out.append(lines[i]);
            if (i < lines.length - 1) out.append("\n");
        }
        return out.toString();
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
