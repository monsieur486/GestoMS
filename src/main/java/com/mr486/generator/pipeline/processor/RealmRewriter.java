package com.mr486.generator.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Réécrit le realm Keycloak importé : retire les rôles et utilisateurs de démonstration des services
 * par défaut et ajoute un rôle + un utilisateur de test par entrée de {@code resources[]}.
 *
 * <p>Utilise l'arbre Jackson (et non une substitution textuelle) car les tableaux du realm sont
 * d'arité variable. Ne s'applique qu'en présence de {@code resources[]}.
 */
@Component
@Order(40)
public class RealmRewriter implements CrossCuttingRewriter {

    private static final Set<String> DEFAULT_SERVICE_ROLES = Set.of(
        "USER_SERVICE_A", "USER_SERVICE_B", "USER_SERVICE_C"
    );
    private static final Set<String> DEFAULT_SERVICE_USERS = Set.of(
        "test-service-a", "test-service-b", "test-service-c"
    );
    private static final String ADMIN_USERNAME = "test-admin";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean handles(GeneratedFile f, GenerationContext ctx) {
        return CrossCuttingRewriter.hasResources(ctx)
            && f.path().endsWith("/keycloak/import/ms-realm-realm.json");
    }

    @Override
    public GeneratedFile rewrite(GeneratedFile f, GenerationContext ctx) {
        if (ProcessorUtils.containsNullByte(f.content())) {
            return f;
        }
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(f.content());
            rewriteRealmRoles((ArrayNode) root.path("roles").path("realm"), resources);
            rewriteUsers((ArrayNode) root.get("users"), resources);
            byte[] out = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
            return new GeneratedFile(f.path(), out, f.executable());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite Keycloak realm " + f.path(), e);
        }
    }

    // Retire les rôles de démonstration des services par défaut et ajoute un rôle par ressource.
    private void rewriteRealmRoles(ArrayNode roles, List<ResourceModuleRequest> resources) {
        for (int i = roles.size() - 1; i >= 0; i--) {
            if (DEFAULT_SERVICE_ROLES.contains(roles.get(i).path("name").asText())) {
                roles.remove(i);
            }
        }
        for (ResourceModuleRequest r : resources) {
            roles.add(mapper.createObjectNode().put("name", ResourceNaming.from(r).roleName()));
        }
    }

    // Retire les utilisateurs de démonstration, repointe test-admin, ajoute un utilisateur de test par ressource.
    private void rewriteUsers(ArrayNode users, List<ResourceModuleRequest> resources) {
        for (int i = users.size() - 1; i >= 0; i--) {
            ObjectNode u = (ObjectNode) users.get(i);
            String username = u.path("username").asText();
            if (DEFAULT_SERVICE_USERS.contains(username)) {
                users.remove(i);
            } else if (ADMIN_USERNAME.equals(username)) {
                repointAdminRoles(u, resources);
            }
        }
        for (ResourceModuleRequest r : resources) {
            users.add(buildRealmUser(r));
        }
    }

    // Remplace les rôles de service par défaut de test-admin par un rôle par ressource.
    private void repointAdminRoles(ObjectNode adminUser, List<ResourceModuleRequest> resources) {
        ArrayNode rr = (ArrayNode) adminUser.get("realmRoles");
        for (int j = rr.size() - 1; j >= 0; j--) {
            if (DEFAULT_SERVICE_ROLES.contains(rr.get(j).asText())) {
                rr.remove(j);
            }
        }
        for (ResourceModuleRequest r : resources) {
            rr.add(ResourceNaming.from(r).roleName());
        }
    }

    // Construit le nœud JSON d'un utilisateur de test Keycloak pour une ressource.
    private ObjectNode buildRealmUser(ResourceModuleRequest r) {
        ObjectNode u = mapper.createObjectNode();
        u.put("username", ResourceNaming.from(r).testUser());
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
        rr.add(ResourceNaming.from(r).roleName());
        u.set("realmRoles", rr);
        return u;
    }
}
