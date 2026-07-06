package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Régénère le script {@code test-all.sh} : matrice de rôles et URLs dérivées de {@code resources[]}
 * (attente du démarrage, obtention des jetons, tests d'accès par service, agrégation, batch, refresh,
 * logout/blacklist, création d'utilisateur admin et changement de mot de passe).
 *
 * <p>Ne s'applique qu'en présence de {@code resources[]} ; sans ressource dynamique, le script
 * statique du modèle est utilisé tel quel (piège « dual-source » documenté dans la mémoire projet).
 *
 * <p>Les gros blocs bash invariants sont externalisés en fragments de ressources
 * ({@code fragments/test-all/*.sh}) chargés par {@link #fragment(String)} ; seules les sections
 * dérivées de {@code resources[]} (attente, jetons, matrice de rôles, agrégation, batch) restent
 * assemblées en Java. Les rares valeurs dynamiques des fragments sont injectées par jetons
 * {@code {{...}}}.
 */
@Component
@Order(50)
// Faux positif PMD : nom en « Test… » mais classe de production, pas une classe de test JUnit.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class TestAllRewriter implements CrossCuttingRewriter {

    private static final String FRAGMENT_DIR = "fragments/test-all/";

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return CrossCuttingRewriter.hasResources(ctx) && f.path().endsWith("/test-all.sh");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        final PlatformGenerationRequest req = ctx.getRequest();
        final List<ResourceModuleRequest> resources = req.getResources();
        final FeatureOptions feat = req.getFeatures();
        final BatchOptions batch = req.getBatch();
        final boolean batchEnabled = batch != null && batch.isEnabled();
        final ResourceModuleRequest first = resources.get(0);
        final String firstUrl = ResourceNaming.from(first).gatewayUrl();

        StringBuilder sb = new StringBuilder(fragment("prologue.sh"));
        appendReadiness(sb, resources, feat);
        appendTokens(sb, resources);
        appendRoleMatrix(sb, resources);
        appendInfra(sb, feat);
        appendAggregation(sb, resources);
        if (batchEnabled) {
            appendBatch(sb, first, firstUrl);
        }

        // refresh : bloc statique + une assertion sur la première ressource
        sb.append(fragment("refresh.sh"));
        sb.append(assertHttp("Refreshed token works on " + first.getServiceName(),
            "200", "GET", "$TOKEN_ADMIN_REFRESHED", firstUrl));
        sb.append("\n");

        // logout / blacklist et cycle de vie admin : fragments avec valeurs de la première ressource
        sb.append(fragment("logout.sh")
            .replace("{{FIRST_USER}}", ResourceNaming.from(first).testUser())
            .replace("{{FIRST_URL}}", firstUrl));
        sb.append(fragment("admin-lifecycle.sh")
            .replace("{{FIRST_SERVICE}}", first.getServiceName())
            .replace("{{FIRST_URL}}", firstUrl));
        sb.append(fragment("password-change.sh"));

        return new GeneratedFile(f.path(), sb.toString().getBytes(StandardCharsets.UTF_8), f.executable());
    }

    // Attente du démarrage complet de la pile (sondes réelles : login admin, routes 200/401/403).
    private void appendReadiness(StringBuilder sb, List<ResourceModuleRequest> resources, FeatureOptions feat) {
        sb.append("echo 'Waiting for the full stack to be ready (~60s on first start)...'\n");
        sb.append("wait_for 'ms-eureka' curl -fs http://localhost:8761\n");
        sb.append("wait_for 'ms-auth + keycloak' auth_ready\n"); // keycloak permanent
        for (ResourceModuleRequest r : resources) {
            sb.append("wait_for '").append(r.getServiceName()).append("' routed_up ")
              .append(ResourceNaming.from(r).routePath()).append("\n");
        }
        sb.append("wait_for 'service-consumer' routed_up service-consumer/api/aggregate\n");
        if (feat.isSpringbootAdmin()) {
            sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        }
        if (feat.isWebUI()) {
            sb.append("wait_for 'ms-webui' curl -fs http://localhost:8090/login\n");
        }
        sb.append("wait_for 'admin-application' curl -fs http://localhost:9300/login\n"); // toujours installé
        sb.append("echo 'Stack is ready.'\n");
        sb.append("echo\n\n");
    }

    // Obtention et vérification des jetons (admin, batch, un par ressource) et écriture de tokens.env.
    private void appendTokens(StringBuilder sb, List<ResourceModuleRequest> resources) {
        sb.append("echo 'Getting tokens via ms-auth...'\n");
        sb.append("ADMIN_LOGIN=$(auth_login test-admin admin123)\n");
        sb.append("TOKEN_ADMIN=$(echo \"$ADMIN_LOGIN\" | jq -r '.access_token // empty')\n");
        sb.append("OPAQUE_ADMIN=$(echo \"$ADMIN_LOGIN\" | jq -r '.opaque_refresh_token // empty')\n\n");
        sb.append("BATCH_LOGIN=$(auth_login test-batch user123)\n");
        sb.append("TOKEN_BATCH=$(echo \"$BATCH_LOGIN\" | jq -r '.access_token // empty')\n\n");
        for (ResourceModuleRequest r : resources) {
            String var = ResourceNaming.from(r).tokenVar();
            sb.append(var).append("_LOGIN=$(auth_login ")
              .append(ResourceNaming.from(r).testUser()).append(" user123)\n");
            sb.append(var).append("=$(echo \"$").append(var)
              .append("_LOGIN\" | jq -r '.access_token // empty')\n\n");
        }

        sb.append("check_token TOKEN_ADMIN ADMIN\n");
        sb.append("check_token TOKEN_BATCH BATCH\n");
        for (ResourceModuleRequest r : resources) {
            String var = ResourceNaming.from(r).tokenVar();
            sb.append("check_token ").append(var).append(" ")
              .append(var.substring("TOKEN_".length())).append("\n");
        }
        sb.append("\n");

        sb.append("cat > tokens.env <<TEOF\n");
        sb.append("TOKEN_ADMIN=${TOKEN_ADMIN}\n");
        sb.append("TOKEN_BATCH=${TOKEN_BATCH}\n");
        for (ResourceModuleRequest r : resources) {
            String var = ResourceNaming.from(r).tokenVar();
            sb.append(var).append("=${").append(var).append("}\n");
        }
        sb.append("TEOF\n");
        sb.append("chmod 600 tokens.env\n\n");
    }

    // Matrice de rôles : ADMIN et le propriétaire accèdent (200), les autres sont refusés (403).
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // identité volontaire : même élément de la liste
    private void appendRoleMatrix(StringBuilder sb, List<ResourceModuleRequest> resources) {
        sb.append("echo 'Testing resource role matrix...'\n");
        for (ResourceModuleRequest target : resources) {
            String url = ResourceNaming.from(target).gatewayUrl();
            sb.append(assertHttp("ADMIN can access " + target.getServiceName(),
                "200", "GET", "$TOKEN_ADMIN", url));
            sb.append(assertHttp(target.getServiceName() + " user can access own resource",
                "200", "GET", "$" + ResourceNaming.from(target).tokenVar(), url));
            for (ResourceModuleRequest other : resources) {
                if (other == target) {
                    continue;
                }
                sb.append(assertHttp(other.getServiceName() + " user cannot access " + target.getServiceName(),
                    "403", "GET", "$" + ResourceNaming.from(other).tokenVar(), url));
            }
            sb.append("\n");
        }
    }

    // Sondes d'infrastructure (Eureka, admin et webUI selon les features, admin-application).
    private void appendInfra(StringBuilder sb, FeatureOptions feat) {
        sb.append("echo 'Testing infrastructure...'\n");
        sb.append("curl -fs http://localhost:8761 >/dev/null && echo 'Eureka OK'\n");
        if (feat.isSpringbootAdmin()) {
            sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
        }
        if (feat.isWebUI()) {
            sb.append("curl -fs http://localhost:8090/login >/dev/null && echo 'WebUI OK'\n");
        }
        sb.append("curl -fs http://localhost:9300/login >/dev/null && echo 'Admin-app OK'\n"); // toujours installé
        sb.append("\n");
    }

    // Agrégation service-consumer : bloc statique + une assertion de présence par ressource.
    private void appendAggregation(StringBuilder sb, List<ResourceModuleRequest> resources) {
        sb.append(fragment("aggregation.sh"));
        for (ResourceModuleRequest r : resources) {
            sb.append("assert_contains 'aggregate response' \"$AGG_RESPONSE\" '")
              .append(r.getServiceName()).append("'\n");
        }
        sb.append("\n");
    }

    // Jobs batch : l'utilisateur BATCH est refusé sur la ressource (403) mais son job est accepté (202).
    private void appendBatch(StringBuilder sb, ResourceModuleRequest first, String firstUrl) {
        sb.append("echo 'Testing batch jobs...'\n");
        sb.append(assertHttp("BATCH user cannot access " + first.getServiceName(),
            "403", "GET", "$TOKEN_BATCH", firstUrl));
        sb.append(assertHttp("BATCH job accepted", "202", "POST", "$TOKEN_BATCH",
            "$GATEWAY_URL/service-consumer/api/users/1/batch-jobs"));
        sb.append("\n");
    }

    // Émet une ligne assert_http : assert_http '<label>' <code> <méthode> "<token>" "<url>".
    private String assertHttp(String label, String expected, String method, String token, String url) {
        return "assert_http '" + label + "' " + expected + " " + method + " \"" + token + "\" \"" + url + "\"\n";
    }

    // Charge un fragment bash du script depuis le classpath (fragments/test-all/<name>).
    private String fragment(String name) {
        ClassPathResource res = new ClassPathResource(FRAGMENT_DIR + name);
        try (InputStream in = res.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Fragment test-all introuvable : " + name, e);
        }
    }
}
