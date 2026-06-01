package com.mr486.generator.pipeline.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synchronise les fichiers transverses qui référencent les services par leur nom :
 * pom racine ({@code <modules>}), {@code docker-compose.yml} et {@code ms-gateway/.../application.yml}.
 * <p>
 * Sans ce processor, les autres étapes laissent des références fantômes :
 * <ul>
 *   <li>modules listés dans le pom racine alors que le dossier a été retiré → {@code mvn package} échoue ;</li>
 *   <li>blocs Docker Compose pointant vers des services absents ou {@code depends_on} orphelins → {@code docker compose up} refuse le fichier ;</li>
 *   <li>routes du gateway pointant vers des services absents ou manquant pour les nouveaux {@code resources[]}.</li>
 * </ul>
 * Tourne en {@link Order @Order(60)} : après que {@link FeatureFilterProcessor} et
 * {@link ResourceExpandProcessor} aient déterminé l'ensemble final des services.
 */
@Component
@Order(60)
public class CrossCuttingConfigProcessor implements FileProcessor {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Pattern DEPENDS_ON_PATTERN = Pattern.compile("(depends_on: \\[)([^\\]]+)(\\])");

    /**
     * Point d'entrée du pipeline : réécrit les fichiers transverses (pom racine, docker-compose,
     * gateway YAML, realm Keycloak, test-all.sh, README, AggregateController, catalogue ms-webui)
     * pour refléter l'ensemble final des services défini dans {@code ctx}.
     *
     * @param files liste des fichiers générés en entrée
     * @param ctx   contexte de génération contenant la requête et le répertoire cible
     * @return liste des fichiers avec les fichiers transverses réécrits
     */
    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        String rootPomPath = ctx.getTargetRoot() + "/pom.xml";
        String composePath = ctx.getTargetRoot() + "/docker-compose.yml";
        String gatewayYml  = ctx.getTargetRoot() + "/ms-gateway/src/main/resources/application.yml";
        boolean hasResources = hasResources(ctx);

        List<GeneratedFile> result = new ArrayList<>(files.size());
        for (GeneratedFile f : files) {
            if (f.path().equals(rootPomPath)) {
                result.add(rewriteRootPom(f, ctx));
            } else if (f.path().equals(composePath)) {
                result.add(rewriteCompose(f, ctx));
            } else if (f.path().equals(gatewayYml)) {
                result.add(rewriteGatewayYml(f, ctx));
            } else if (hasResources && f.path().endsWith("/keycloak/import/ms-realm-realm.json")) {
                result.add(rewriteRealm(f, ctx));
            } else if (hasResources && f.path().endsWith("/test-all.sh")) {
                result.add(rewriteTestAll(f, ctx));
            } else if (hasResources && f.path().endsWith("/README.md")) {
                result.add(rewriteReadme(f, ctx));
            } else if (hasResources
                    && f.path().contains("/service-consumer/")
                    && f.path().endsWith("AggregateController.java")) {
                result.add(rewriteAggregate(f, ctx));
            } else if (hasResources
                    && f.path().endsWith("/ms-webui/src/main/resources/application.yml")) {
                result.add(rewriteWebUiCatalog(f, ctx));
            } else {
                result.add(f);
            }
        }
        return result;
    }

    private boolean hasResources(GenerationContext ctx) {
        List<ResourceModuleRequest> r = ctx.getRequest().getResources();
        return r != null && !r.isEmpty();
    }

    // ── Per-resource naming helpers ───────────────────────────────────────────

    private String roleName(ResourceModuleRequest r) {
        return "USER_" + r.getServiceName().replace("-", "_").toUpperCase();
    }

    private String tokenVar(ResourceModuleRequest r) {
        return "TOKEN_" + r.getServiceName().replace("-", "_").toUpperCase();
    }

    private String testUser(ResourceModuleRequest r) {
        return "test-" + r.getServiceName();
    }

    /**
     * Full gateway URL a client hits: {@code $GATEWAY_URL/<service><routePrefix>}
     * (Path=/&lt;service&gt;/** + StripPrefix=1).
     */
    private String gatewayUrl(ResourceModuleRequest r) {
        return "$GATEWAY_URL/" + r.getServiceName() + r.getEffectiveRoutePrefix();
    }

    /** Path under the gateway (no host), as fed to the routed_up readiness probe. */
    private String routePath(ResourceModuleRequest r) {
        return r.getServiceName() + r.getEffectiveRoutePrefix();
    }

    // ── Root pom <modules> ────────────────────────────────────────────────────

    private GeneratedFile rewriteRootPom(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;
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

    private List<String> desiredModules(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        BatchOptions b = req.getBatch();
        boolean hasResources = hasResources(ctx);

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
        if (b.isEnabled())            modules.add("service-batch");
        if (f.isSpringbootAdmin())    modules.add("ms-admin");
        if (f.isWebUI())              modules.add("ms-webui");
        if (hasResources) {
            for (ResourceModuleRequest r : req.getResources()) modules.add(r.getServiceName());
        }
        return modules;
    }

    // ── docker-compose service blocks ─────────────────────────────────────────

    private GeneratedFile rewriteCompose(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;
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
        Set<String> removed = new HashSet<>(removedBlocks);
        Matcher m = DEPENDS_ON_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String[] deps = m.group(2).split(",\\s*");
            List<String> kept = new ArrayList<>();
            for (String dep : deps) {
                String trimmed = dep.trim();
                if (!removed.contains(trimmed)) kept.add(trimmed);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(
                m.group(1) + String.join(", ", kept) + m.group(3)
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private List<String> volumesToRemove(GenerationContext ctx) {
        List<String> vols = new ArrayList<>();
        if (hasResources(ctx)) {
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
        boolean hasResources = hasResources(ctx);

        List<String> blocks = new ArrayList<>();
        if (!b.isEnabled())          blocks.add("service-batch");
        if (!b.isGrafana()) { blocks.add("loki"); blocks.add("promtail"); blocks.add("grafana"); }
        if (!f.isSpringbootAdmin())  blocks.add("ms-admin");
        if (!f.isWebUI())            blocks.add("ms-webui");
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

        StringBuilder newServices = new StringBuilder();
        StringBuilder newVolumes = new StringBuilder();
        for (ResourceModuleRequest r : req.getResources()) {
            newServices.append(buildResourceServiceBlock(r));
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

    private String buildResourceServiceBlock(ResourceModuleRequest r) {
        DatabaseType db = r.getDatabaseType() == null ? DatabaseType.POSTGRES : r.getDatabaseType();
        String name  = r.getServiceName();
        String snake = name.replace("-", "_");
        String upper = snake.toUpperCase();

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
        }
        sb.append("\n");
        return sb.toString();
    }

    private void appendAppService(StringBuilder sb, String name, String deps) {
        sb.append("  ").append(name).append(":\n")
          .append("    build: ./").append(name).append("\n")
          .append("    env_file: [.env]\n")
          .append("    depends_on: ").append(deps).append("\n")
          .append("    environment:\n")
          .append("      EUREKA_DEFAULT_ZONE: http://ms-eureka:8761/eureka/\n")
          .append("      KEYCLOAK_ISSUER_URI: http://localhost:8089/realms/ms-realm\n");
    }

    private String buildResourceVolumeEntry(ResourceModuleRequest r) {
        DatabaseType db = r.getDatabaseType() == null ? DatabaseType.POSTGRES : r.getDatabaseType();
        if (db == DatabaseType.H2) return "";
        return "  " + r.getServiceName().replace("-", "_") + "_db_data:\n";
    }

    // ── ms-gateway routes ─────────────────────────────────────────────────────

    private GeneratedFile rewriteGatewayYml(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;
        String text = new String(f.content(), StandardCharsets.UTF_8);
        PlatformGenerationRequest req = ctx.getRequest();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

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
            if (line.length() > 2 && line.charAt(0) == ' '
                    && line.charAt(1) == ' ' && line.charAt(2) != ' ') {
                endIdx = i;
                break;
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

    // ── Keycloak realm (roles + test users per resource) ──────────────────────

    private static final Set<String> DEFAULT_SERVICE_ROLES = Set.of(
        "USER_SERVICE_A", "USER_SERVICE_B", "USER_SERVICE_C"
    );
    private static final Set<String> DEFAULT_SERVICE_USERS = Set.of(
        "test-service-a", "test-service-b", "test-service-c"
    );

    private GeneratedFile rewriteRealm(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(f.content());

            // roles.realm: drop the three demo service roles, add one per resource
            ArrayNode roles = (ArrayNode) root.path("roles").path("realm");
            for (int i = roles.size() - 1; i >= 0; i--) {
                if (DEFAULT_SERVICE_ROLES.contains(roles.get(i).path("name").asText())) roles.remove(i);
            }
            for (ResourceModuleRequest r : resources) {
                roles.add(mapper.createObjectNode().put("name", roleName(r)));
            }

            // users: drop demo service users, re-point test-admin's roles
            ArrayNode users = (ArrayNode) root.get("users");
            for (int i = users.size() - 1; i >= 0; i--) {
                ObjectNode u = (ObjectNode) users.get(i);
                String username = u.path("username").asText();
                if (DEFAULT_SERVICE_USERS.contains(username)) { users.remove(i); continue; }
                if ("test-admin".equals(username)) {
                    ArrayNode rr = (ArrayNode) u.get("realmRoles");
                    for (int j = rr.size() - 1; j >= 0; j--) {
                        if (DEFAULT_SERVICE_ROLES.contains(rr.get(j).asText())) rr.remove(j);
                    }
                    for (ResourceModuleRequest r : resources) rr.add(roleName(r));
                }
            }
            // add one test user per resource
            for (ResourceModuleRequest r : resources) users.add(buildRealmUser(r));

            byte[] out = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
            return new GeneratedFile(f.path(), out, f.executable());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite Keycloak realm " + f.path(), e);
        }
    }

    private ObjectNode buildRealmUser(ResourceModuleRequest r) {
        ObjectNode u = mapper.createObjectNode();
        u.put("username", testUser(r));
        u.put("enabled", true);
        u.put("emailVerified", true);
        u.put("firstName", "Test");
        u.put("lastName", ProcessorUtils.toPascalCase(r.getServiceName()));
        u.put("email", r.getServiceName() + "@example.local");
        u.set("requiredActions", mapper.createArrayNode());
        ArrayNode creds = mapper.createArrayNode();
        creds.add(mapper.createObjectNode().put("type", "password").put("value", "user123").put("temporary", false));
        u.set("credentials", creds);
        ArrayNode rr = mapper.createArrayNode();
        rr.add(roleName(r));
        u.set("realmRoles", rr);
        return u;
    }


    // ── README (Keycloak users per resource) ────────────────────────────────

    private GeneratedFile rewriteReadme(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;

        String text = new String(f.content(), StandardCharsets.UTF_8);
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();

        StringBuilder table = new StringBuilder();
        table.append("## Utilisateurs Keycloak\n");
        table.append("| Utilisateur | Mot de passe | Rôles |\n");
        table.append("|---|---|---|\n");
        table.append("| `test-admin` | `admin123` | ADMIN, USER_BATCH");
        for (ResourceModuleRequest r : resources) {
            table.append(", ").append(roleName(r));
        }
        table.append(" |\n");
        table.append("| `test-batch` | `user123` | USER_BATCH |\n");
        for (ResourceModuleRequest r : resources) {
            table.append("| `")
                    .append(testUser(r))
                    .append("` | `user123` | ")
                    .append(roleName(r))
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

    // ── test-all.sh (role matrix + URLs derived from resources[]) ─────────────

    private GeneratedFile rewriteTestAll(GeneratedFile f, GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        List<ResourceModuleRequest> resources = req.getResources();
        FeatureOptions feat = req.getFeatures();
        BatchOptions batch = req.getBatch();
        boolean batchEnabled = batch != null && batch.isEnabled();

        StringBuilder sb = new StringBuilder(TEST_ALL_PROLOGUE);

        // Block until the whole stack actually serves, so this script can be run right after
        // ./prod-start.sh (full startup ~60s). Probes are real signals: a successful admin login
        // for ms-auth+Keycloak, and a routed 200/401/403 for each service (= registered in Eureka).
        sb.append("echo 'Waiting for the full stack to be ready (~60s on first start)...'\n");
        sb.append("wait_for 'ms-eureka' curl -fs http://localhost:8761\n");
        sb.append("wait_for 'ms-auth + keycloak' auth_ready\n"); // keycloak permanent
        for (ResourceModuleRequest r : resources) {
            sb.append("wait_for '").append(r.getServiceName()).append("' routed_up ").append(routePath(r)).append("\n");
        }
        sb.append("wait_for 'service-consumer' routed_up service-consumer/api/aggregate\n");
        if (feat.isSpringbootAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        if (feat.isWebUI()) sb.append("wait_for 'ms-webui' curl -fs http://localhost:8090/login\n");
        sb.append("wait_for 'admin-application' curl -fs http://localhost:9300/login\n"); // toujours installé
        sb.append("echo 'Stack is ready.'\n");
        sb.append("echo\n\n");

        sb.append("echo 'Getting tokens via ms-auth...'\n");
        sb.append("ADMIN_LOGIN=$(auth_login test-admin admin123)\n");
        sb.append("TOKEN_ADMIN=$(echo \"$ADMIN_LOGIN\" | jq -r '.access_token // empty')\n");
        sb.append("OPAQUE_ADMIN=$(echo \"$ADMIN_LOGIN\" | jq -r '.opaque_refresh_token // empty')\n\n");
        sb.append("BATCH_LOGIN=$(auth_login test-batch user123)\n");
        sb.append("TOKEN_BATCH=$(echo \"$BATCH_LOGIN\" | jq -r '.access_token // empty')\n\n");
        for (ResourceModuleRequest r : resources) {
            String var = tokenVar(r);
            sb.append(var).append("_LOGIN=$(auth_login ").append(testUser(r)).append(" user123)\n");
            sb.append(var).append("=$(echo \"$").append(var).append("_LOGIN\" | jq -r '.access_token // empty')\n\n");
        }

        sb.append("check_token TOKEN_ADMIN ADMIN\n");
        sb.append("check_token TOKEN_BATCH BATCH\n");
        for (ResourceModuleRequest r : resources) {
            sb.append("check_token ")
              .append(tokenVar(r)).append(" ")
              .append(tokenVar(r).substring("TOKEN_".length())).append("\n");
        }
        sb.append("\n");

        sb.append("cat > tokens.env <<TEOF\n");
        sb.append("TOKEN_ADMIN=${TOKEN_ADMIN}\n");
        sb.append("TOKEN_BATCH=${TOKEN_BATCH}\n");
        for (ResourceModuleRequest r : resources) {
            sb.append(tokenVar(r)).append("=${").append(tokenVar(r)).append("}\n");
        }
        sb.append("TEOF\n");
        sb.append("chmod 600 tokens.env\n\n");

        sb.append("echo 'Testing resource role matrix...'\n");
        for (ResourceModuleRequest target : resources) {
            String url = gatewayUrl(target);
            sb.append(assertHttp("ADMIN can access " + target.getServiceName(), "200", "$TOKEN_ADMIN", url));
            sb.append(assertHttp(
                target.getServiceName() + " user can access own resource",
                "200", "$" + tokenVar(target), url
            ));
            for (ResourceModuleRequest other : resources) {
                if (other == target) continue;
                sb.append(assertHttp(
                    other.getServiceName() + " user cannot access " + target.getServiceName(),
                    "403", "$" + tokenVar(other), url
                ));
            }
            sb.append("\n");
        }

        sb.append("echo 'Testing infrastructure...'\n");
        sb.append("curl -fs http://localhost:8761 >/dev/null && echo 'Eureka OK'\n");
        if (feat.isSpringbootAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
        if (feat.isWebUI()) sb.append("curl -fs http://localhost:8090/login >/dev/null && echo 'WebUI OK'\n");
        sb.append("curl -fs http://localhost:9300/login >/dev/null && echo 'Admin-app OK'\n"); // toujours installé
        sb.append("\n");

        sb.append("echo 'Testing service-consumer aggregation...'\n");
        sb.append("AGG_RESPONSE=$(curl -s \\\n  -H \"Authorization: Bearer $TOKEN_ADMIN\" \\\n  \"$GATEWAY_URL/service-consumer/api/aggregate\")\n\n");
        sb.append("AGG_STATUS=$(curl -s -o /tmp/aggregate-response.txt -w \"%{http_code}\" \\\n  -H \"Authorization: Bearer $TOKEN_ADMIN\" \\\n  \"$GATEWAY_URL/service-consumer/api/aggregate\")\n\n");
        sb.append("if [ \"$AGG_STATUS\" != \"200\" ]; then\n  echo \"FAIL ADMIN aggregate expected 200 got $AGG_STATUS\"\n  cat /tmp/aggregate-response.txt\n  exit 1\nfi\n");
        sb.append("echo 'OK ADMIN aggregate -> 200'\n");
        for (ResourceModuleRequest r : resources) {
            sb.append("assert_contains 'aggregate response' \"$AGG_RESPONSE\" '")
              .append(r.getServiceName()).append("'\n");
        }
        sb.append("\n");

        ResourceModuleRequest first = resources.get(0);
        String firstUrl = gatewayUrl(first);
        if (batchEnabled) {
            sb.append("echo 'Testing batch jobs...'\n");
            sb.append(assertHttp(
                "BATCH user cannot access " + first.getServiceName(), "403", "$TOKEN_BATCH", firstUrl
            ));
            sb.append(assertHttp(
                "BATCH job accepted", "202", "$TOKEN_BATCH",
                "$GATEWAY_URL/service-consumer/api/users/1/batch-jobs", "POST"
            ));
            sb.append("\n");
        }

        sb.append("echo 'Testing refresh token...'\n");
        sb.append("REFRESH_RESPONSE=$(auth_refresh \"$OPAQUE_ADMIN\")\n");
        sb.append("TOKEN_ADMIN_REFRESHED=$(echo \"$REFRESH_RESPONSE\" | jq -r '.access_token // empty')\n");
        sb.append("if [ -z \"$TOKEN_ADMIN_REFRESHED\" ]; then\n  echo \"FAIL refresh token — no access_token in response\"\n  echo \"$REFRESH_RESPONSE\"\n  exit 1\nfi\n");
        sb.append("echo \"OK refresh token -> new access_token received\"\n");
        sb.append(assertHttp(
            "Refreshed token works on " + first.getServiceName(), "200", "$TOKEN_ADMIN_REFRESHED", firstUrl
        ));
        sb.append("\n");

        sb.append("echo 'Testing logout and blacklist...'\n");
        sb.append("LOGOUT_LOGIN=$(auth_login ").append(testUser(first)).append(" user123)\n");
        sb.append("LOGOUT_ACCESS=$(echo \"$LOGOUT_LOGIN\" | jq -r '.access_token // empty')\n");
        sb.append("LOGOUT_OPAQUE=$(echo \"$LOGOUT_LOGIN\" | jq -r '.opaque_refresh_token // empty')\n\n");
        sb.append(assertHttp("Token works before logout", "200", "$LOGOUT_ACCESS", firstUrl));
        sb.append("LOGOUT_STATUS=$(curl -s -o /dev/null -w \"%{http_code}\" \\\n  -X POST \"$GATEWAY_URL/auth/logout\" \\\n  -H \"Authorization: Bearer $LOGOUT_ACCESS\" \\\n  -H \"Content-Type: application/json\" \\\n  -d \"{\\\"opaque_refresh_token\\\":\\\"$LOGOUT_OPAQUE\\\"}\")\n");
        sb.append("if [ \"$LOGOUT_STATUS\" != \"204\" ]; then\n  echo \"FAIL logout expected 204 got $LOGOUT_STATUS\"\n  exit 1\nfi\n");
        sb.append("echo \"OK logout -> 204\"\n");
        sb.append(assertHttp("Blacklisted token rejected by gateway", "401", "$LOGOUT_ACCESS", firstUrl));
        sb.append("STALE_REFRESH_STATUS=$(curl -s -o /dev/null -w \"%{http_code}\" \\\n  -X POST \"$GATEWAY_URL/auth/refresh\" \\\n  -H \"Content-Type: application/json\" \\\n  -d \"{\\\"opaque_refresh_token\\\":\\\"$LOGOUT_OPAQUE\\\"}\")\n");
        sb.append("if [ \"$STALE_REFRESH_STATUS\" != \"401\" ]; then\n  echo \"FAIL stale refresh expected 401 got $STALE_REFRESH_STATUS\"\n  exit 1\nfi\n");
        sb.append("echo \"OK stale refresh token -> 401\"\n\n");

        sb.append("echo 'Testing admin user creation + self password change...'\n");
        sb.append("KC_ADMIN=${KEYCLOAK_ADMIN:-admin}\n");
        sb.append("KC_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}\n");
        sb.append("KC_ADMIN_TOKEN=$(curl -s -X POST \"$KEYCLOAK_URL/realms/master/protocol/openid-connect/token\" "
                + "-H \"Content-Type: application/x-www-form-urlencoded\" "
                + "--data-urlencode \"grant_type=password\" --data-urlencode \"client_id=admin-cli\" "
                + "--data-urlencode \"username=$KC_ADMIN\" --data-urlencode \"password=$KC_ADMIN_PASSWORD\" "
                + "| jq -r '.access_token // empty')\n");
        sb.append("if [ -z \"$KC_ADMIN_TOKEN\" ]; then "
                + "echo 'FAIL could not obtain Keycloak master admin token'; exit 1; fi\n");
        sb.append("echo 'OK Keycloak master admin token obtained'\n\n");

        sb.append("CREATE_ADMIN2_STATUS=$(curl -s -o /dev/null -w \"%{http_code}\" -X POST "
                + "\"$KEYCLOAK_URL/admin/realms/ms-realm/users\" "
                + "-H \"Authorization: Bearer $KC_ADMIN_TOKEN\" -H \"Content-Type: application/json\" "
                + "-d '{\"username\":\"admin2\",\"enabled\":true,\"email\":\"admin2@example.com\","
                + "\"firstName\":\"Admin\",\"lastName\":\"Two\","
                + "\"credentials\":[{\"type\":\"password\",\"value\":\"admin2pass\",\"temporary\":false}]}')\n");
        sb.append("if [ \"$CREATE_ADMIN2_STATUS\" != \"201\" ] && [ \"$CREATE_ADMIN2_STATUS\" != \"409\" ]; then\n");
        sb.append("  echo \"FAIL create admin2 expected 201 or 409 got $CREATE_ADMIN2_STATUS\"; exit 1\n");
        sb.append("fi\n");
        sb.append("echo \"OK admin2 created by admin (status $CREATE_ADMIN2_STATUS)\"\n\n");

        sb.append("ADMIN2_ID=$(curl -s "
                + "\"$KEYCLOAK_URL/admin/realms/ms-realm/users?username=admin2&exact=true\" "
                + "-H \"Authorization: Bearer $KC_ADMIN_TOKEN\" | jq -r '.[0].id // empty')\n");
        sb.append("if [ -z \"$ADMIN2_ID\" ]; then echo 'FAIL could not resolve admin2 id'; exit 1; fi\n");
        sb.append("curl -s -o /dev/null -X PUT "
                + "\"$KEYCLOAK_URL/admin/realms/ms-realm/users/$ADMIN2_ID/reset-password\" "
                + "-H \"Authorization: Bearer $KC_ADMIN_TOKEN\" -H \"Content-Type: application/json\" "
                + "-d '{\"type\":\"password\",\"value\":\"admin2pass\",\"temporary\":false}'\n");
        sb.append("ADMIN_ROLE_JSON=$(curl -s \"$KEYCLOAK_URL/admin/realms/ms-realm/roles/ADMIN\" "
                + "-H \"Authorization: Bearer $KC_ADMIN_TOKEN\")\n");
        sb.append("curl -s -o /dev/null -X POST "
                + "\"$KEYCLOAK_URL/admin/realms/ms-realm/users/$ADMIN2_ID/role-mappings/realm\" "
                + "-H \"Authorization: Bearer $KC_ADMIN_TOKEN\" -H \"Content-Type: application/json\" "
                + "-d \"[$ADMIN_ROLE_JSON]\"\n");
        sb.append("echo 'OK admin2 granted realm role ADMIN'\n\n");

        sb.append("ADMIN2_LOGIN=$(auth_login admin2 admin2pass)\n");
        sb.append("TOKEN_ADMIN2=$(echo \"$ADMIN2_LOGIN\" | jq -r '.access_token // empty')\n");
        sb.append("check_token TOKEN_ADMIN2 ADMIN2\n");
        sb.append(assertHttp("admin2 (ADMIN) can access " + first.getServiceName(),
                "200", "$TOKEN_ADMIN2", firstUrl));
        sb.append("\n");

        sb.append("PWD_CHANGE_STATUS=$(curl -s -o /dev/null -w \"%{http_code}\" -X POST "
                + "\"$GATEWAY_URL/auth/account/password\" "
                + "-H \"Authorization: Bearer $TOKEN_ADMIN2\" -H \"Content-Type: application/json\" "
                + "-d '{\"oldPassword\":\"admin2pass\",\"newPassword\":\"admin2new\"}')\n");
        sb.append("if [ \"$PWD_CHANGE_STATUS\" != \"204\" ]; then\n");
        sb.append("  echo \"FAIL admin2 self password change expected 204 got $PWD_CHANGE_STATUS\"; exit 1\n");
        sb.append("fi\n");
        sb.append("echo 'OK admin2 self password change -> 204'\n\n");

        sb.append("NEW_PWD_TOKEN=$(auth_login admin2 admin2new | jq -r '.access_token // empty')\n");
        sb.append("if [ -z \"$NEW_PWD_TOKEN\" ]; then echo 'FAIL admin2 login with new password failed'; exit 1; fi\n");
        sb.append("echo 'OK admin2 logs in with new password'\n\n");

        sb.append("OLD_PWD_CODE=$(curl -s -o /dev/null -w \"%{http_code}\" -X POST \"$GATEWAY_URL/auth/login\" "
                + "-H \"Content-Type: application/json\" -d '{\"username\":\"admin2\",\"password\":\"admin2pass\"}')\n");
        sb.append("if [ \"$OLD_PWD_CODE\" != \"401\" ]; then "
                + "echo \"FAIL admin2 old password expected 401 got $OLD_PWD_CODE\"; exit 1; fi\n");
        sb.append("echo 'OK admin2 old password rejected -> 401'\n\n");

        sb.append("WRONG_OLD_CODE=$(curl -s -o /dev/null -w \"%{http_code}\" -X POST "
                + "\"$GATEWAY_URL/auth/account/password\" "
                + "-H \"Authorization: Bearer $NEW_PWD_TOKEN\" -H \"Content-Type: application/json\" "
                + "-d '{\"oldPassword\":\"definitelywrong\",\"newPassword\":\"whatever123\"}')\n");
        sb.append("if [ \"$WRONG_OLD_CODE\" != \"422\" ]; then "
                + "echo \"FAIL wrong old password expected 422 got $WRONG_OLD_CODE\"; exit 1; fi\n");
        sb.append("echo 'OK wrong old password rejected -> 422'\n\n");

        sb.append("echo 'All tests passed. tokens.env generated.'\n");

        return new GeneratedFile(f.path(), sb.toString().getBytes(StandardCharsets.UTF_8), f.executable());
    }

    private String assertHttp(String label, String expected, String token, String url) {
        return assertHttp(label, expected, token, url, "GET");
    }

    private String assertHttp(String label, String expected, String token, String url, String method) {
        return "assert_http '" + label + "' " + expected + " " + method + " \"" + token + "\" \"" + url + "\"\n";
    }

    private static final String TEST_ALL_PROLOGUE =
        "#!/usr/bin/env bash\n" +
        "set -euo pipefail\n\n" +
        "KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8089}\n" +
        "GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}\n\n" +
        "WAIT_TIMEOUT=${WAIT_TIMEOUT:-180}\n" +
        "WAIT_INTERVAL=${WAIT_INTERVAL:-3}\n\n" +
        "wait_for(){\n" +
        "  local label=$1; shift\n" +
        "  local deadline=$(( SECONDS + WAIT_TIMEOUT ))\n" +
        "  printf 'Waiting for %s ' \"$label\"\n" +
        "  until \"$@\" >/dev/null 2>&1; do\n" +
        "    if (( SECONDS >= deadline )); then\n" +
        "      printf '\\nFAIL %s not ready after %ss\\n' \"$label\" \"$WAIT_TIMEOUT\"; exit 1\n" +
        "    fi\n" +
        "    printf '.'; sleep \"$WAIT_INTERVAL\"\n" +
        "  done\n" +
        "  printf ' OK\\n'\n" +
        "}\n\n" +
        "auth_ready(){\n" +
        "  local t\n" +
        "  t=$(curl -s -X POST \"$GATEWAY_URL/auth/login\" -H \"Content-Type: application/json\" \\\n" +
        "        -d '{\"username\":\"test-admin\",\"password\":\"admin123\"}' | jq -r '.access_token // empty')\n" +
        "  [ -n \"$t\" ]\n" +
        "}\n\n" +
        "routed_up(){\n" +
        "  local code\n" +
        "  code=$(curl -s -o /dev/null -w '%{http_code}' \"$GATEWAY_URL/$1\" || true)\n" +
        "  [[ \"$code\" =~ ^(200|401|403)$ ]]\n" +
        "}\n\n" +
        "auth_login(){\n" +
        "  curl -s -X POST \"$GATEWAY_URL/auth/login\" \\\n" +
        "    -H \"Content-Type: application/json\" \\\n" +
        "    -d \"{\\\"username\\\":\\\"$1\\\",\\\"password\\\":\\\"$2\\\"}\"\n" +
        "}\n\n" +
        "auth_refresh(){\n" +
        "  curl -s -X POST \"$GATEWAY_URL/auth/refresh\" \\\n" +
        "    -H \"Content-Type: application/json\" \\\n" +
        "    -d \"{\\\"opaque_refresh_token\\\":\\\"$1\\\"}\"\n" +
        "}\n\n" +
        "check_token(){\n" +
        "  if [ -z \"${!1}\" ]; then\n" +
        "    echo \"Unable to get $2 token\"\n" +
        "    exit 1\n" +
        "  fi\n" +
        "}\n\n" +
        "assert_http(){\n" +
        "  local label=$1\n" +
        "  local expected=$2\n" +
        "  local method=$3\n" +
        "  local token=$4\n" +
        "  local url=$5\n\n" +
        "  local response_file\n" +
        "  response_file=$(mktemp)\n\n" +
        "  local status\n" +
        "  status=$(curl -s -o \"$response_file\" -w \"%{http_code}\" \\\n" +
        "    -X \"$method\" \\\n" +
        "    -H \"Authorization: Bearer $token\" \\\n" +
        "    \"$url\")\n\n" +
        "  if [ \"$status\" = \"$expected\" ]; then\n" +
        "    echo \"OK $label -> $status\"\n" +
        "  else\n" +
        "    echo \"FAIL $label expected $expected got $status\"\n" +
        "    cat \"$response_file\"\n" +
        "    rm -f \"$response_file\"\n" +
        "    exit 1\n" +
        "  fi\n\n" +
        "  rm -f \"$response_file\"\n" +
        "}\n\n" +
        "assert_contains(){\n" +
        "  local label=$1\n" +
        "  local haystack=$2\n" +
        "  local needle=$3\n\n" +
        "  if echo \"$haystack\" | grep -q \"$needle\"; then\n" +
        "    echo \"OK $label contains $needle\"\n" +
        "  else\n" +
        "    echo \"FAIL $label missing $needle\"\n" +
        "    echo \"$haystack\"\n" +
        "    exit 1\n" +
        "  fi\n" +
        "}\n\n";

    // ── service-consumer AggregateController (zip over N resources) ────────────

    private GeneratedFile rewriteAggregate(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;
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

    /**
     * Réécrit le bloc {@code webui:} (dernière section de l'application.yml de ms-webui) avec le
     * catalogue construit depuis {@code resources[]}. Le bloc étant en fin de fichier, on remplace de
     * {@code ^webui:} jusqu'à la fin du contenu — pas de chirurgie d'indentation.
     */
    private GeneratedFile rewriteWebUiCatalog(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) return f;
        String text = new String(f.content(), StandardCharsets.UTF_8);
        StringBuilder block = new StringBuilder("webui:\n  resources:\n");
        for (ResourceModuleRequest r : ctx.getRequest().getResources()) {
            block.append("    - serviceName: ").append(r.getServiceName()).append("\n");
            block.append("      routePrefix: ").append(r.getEffectiveRoutePrefix()).append("\n");
            block.append("      label: ").append(r.getClassName()).append("\n");
            block.append("      role: ").append(roleName(r)).append("\n");
        }
        String newText = text.replaceAll("(?ms)^webui:.*\\z",
                Matcher.quoteReplacement(block.toString().stripTrailing() + "\n"));
        return new GeneratedFile(f.path(), newText.getBytes(StandardCharsets.UTF_8), f.executable());
    }

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
