package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Régénère le script {@code test-all.sh} : matrice de rôles et URLs dérivées de {@code resources[]}
 * (attente du démarrage, obtention des jetons, tests d'accès par service, agrégation, batch, refresh,
 * logout/blacklist, création d'utilisateur admin et changement de mot de passe).
 *
 * <p>Ne s'applique qu'en présence de {@code resources[]} ; sans ressource dynamique, le script
 * statique du modèle est utilisé tel quel (piège « dual-source » documenté dans la mémoire projet).
 */
@Component
@Order(50)
@SuppressWarnings({
    // Faux positif : nom en « Test… » mais classe de production, pas une classe de test JUnit.
    "PMD.TestClassWithoutTestCases",
    // rewrite() est un émetteur de script bash intrinsèquement linéaire (une section = un append) :
    // le fragmenter en sous-builders n'améliorerait pas la lisibilité. Cible du chantier #6
    // (externalisation des gabarits en ressources template).
    "PMD.NcssCount", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.NPathComplexity",
    // Comparaison d'identité volontaire (même élément de la liste resources dans la boucle imbriquée).
    "PMD.CompareObjectsWithEquals"
})
public class TestAllRewriter implements CrossCuttingRewriter {

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return CrossCuttingRewriter.hasResources(ctx) && f.path().endsWith("/test-all.sh");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        final List<ResourceModuleRequest> resources = req.getResources();
        final FeatureOptions feat = req.getFeatures();
        BatchOptions batch = req.getBatch();
        final boolean batchEnabled = batch != null && batch.isEnabled();

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
        if (feat.isSpringbootAdmin()) {
            sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
        }
        if (feat.isWebUI()) {
            sb.append("wait_for 'ms-webui' curl -fs http://localhost:8090/login\n");
        }
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
                if (other == target) {
                    continue;
                }
                sb.append(assertHttp(
                    other.getServiceName() + " user cannot access " + target.getServiceName(),
                    "403", "$" + tokenVar(other), url
                ));
            }
            sb.append("\n");
        }

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

        sb.append("echo 'Testing service-consumer aggregation...'\n");
        sb.append("AGG_RESPONSE=$(curl -s \\\n"
                + "  -H \"Authorization: Bearer $TOKEN_ADMIN\" \\\n"
                + "  \"$GATEWAY_URL/service-consumer/api/aggregate\")\n\n");
        sb.append("AGG_STATUS=$(curl -s -o /tmp/aggregate-response.txt -w \"%{http_code}\" \\\n"
                + "  -H \"Authorization: Bearer $TOKEN_ADMIN\" \\\n"
                + "  \"$GATEWAY_URL/service-consumer/api/aggregate\")\n\n");
        sb.append("if [ \"$AGG_STATUS\" != \"200\" ]; then\n"
                + "  echo \"FAIL ADMIN aggregate expected 200 got $AGG_STATUS\"\n"
                + "  cat /tmp/aggregate-response.txt\n"
                + "  exit 1\nfi\n");
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
        sb.append("if [ -z \"$TOKEN_ADMIN_REFRESHED\" ]; then\n"
                + "  echo \"FAIL refresh token — no access_token in response\"\n"
                + "  echo \"$REFRESH_RESPONSE\"\n"
                + "  exit 1\nfi\n");
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
        sb.append("LOGOUT_STATUS=$(curl -s -o /dev/null -w \"%{http_code}\" \\\n"
                + "  -X POST \"$GATEWAY_URL/auth/logout\" \\\n"
                + "  -H \"Authorization: Bearer $LOGOUT_ACCESS\" \\\n"
                + "  -H \"Content-Type: application/json\" \\\n"
                + "  -d \"{\\\"opaque_refresh_token\\\":\\\"$LOGOUT_OPAQUE\\\"}\")\n");
        sb.append("if [ \"$LOGOUT_STATUS\" != \"204\" ]; then\n"
                + "  echo \"FAIL logout expected 204 got $LOGOUT_STATUS\"\n"
                + "  exit 1\nfi\n");
        sb.append("echo \"OK logout -> 204\"\n");
        sb.append(assertHttp("Blacklisted token rejected by gateway", "401", "$LOGOUT_ACCESS", firstUrl));
        sb.append("STALE_REFRESH_STATUS=$(curl -s -o /dev/null -w \"%{http_code}\" \\\n"
                + "  -X POST \"$GATEWAY_URL/auth/refresh\" \\\n"
                + "  -H \"Content-Type: application/json\" \\\n"
                + "  -d \"{\\\"opaque_refresh_token\\\":\\\"$LOGOUT_OPAQUE\\\"}\")\n");
        sb.append("if [ \"$STALE_REFRESH_STATUS\" != \"401\" ]; then\n"
                + "  echo \"FAIL stale refresh expected 401 got $STALE_REFRESH_STATUS\"\n"
                + "  exit 1\nfi\n");
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
                + "-H \"Content-Type: application/json\" "
                + "-d '{\"username\":\"admin2\",\"password\":\"admin2pass\"}')\n");
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

    // Nom de variable de jeton du service dans le script ($TOKEN_ORDER_SERVICE).
    private String tokenVar(ResourceModuleRequest r) {
        return ResourceNaming.from(r).tokenVar();
    }

    // Utilisateur de test Keycloak du service (test-order-service).
    private String testUser(ResourceModuleRequest r) {
        return ResourceNaming.from(r).testUser();
    }

    // URL complète frappée via la passerelle ($GATEWAY_URL/<service><routePrefix>).
    private String gatewayUrl(ResourceModuleRequest r) {
        return ResourceNaming.from(r).gatewayUrl();
    }

    // Chemin routé sous la passerelle (sans hôte), pour la sonde routed_up.
    private String routePath(ResourceModuleRequest r) {
        return ResourceNaming.from(r).routePath();
    }
}
