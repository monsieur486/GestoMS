# Phase 1 — Refonte du modèle de features — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplifier le modèle de features du générateur GestoMS — keycloak/redis/rabbitmq/websocket deviennent permanents, l'observabilité (loki+promtail+grafana) est pilotée par `batch.grafana`, et seuls `springbootAdmin` (module ms-admin) et `clientWebUI` (module ms-client, créé en Phase 2) restent optionnels.

**Architecture:** Le générateur transforme un template décompressé (`src/main/resources/templates/ms-platform/`, 112 fichiers) via des processors Spring ordonnés par `@Order`. On réduit `FeatureOptions` à 2 booléens, on ajoute `grafana` à `BatchOptions`, et on réaligne `FeatureFilterProcessor` (filtrage de chemins) + `CrossCuttingConfigProcessor` (références transverses). Aucune nouvelle application n'est générée — fondation des Phases 2/3.

**Tech Stack:** Java 17, Spring Boot, Lombok `@Data`, JUnit 5 + AssertJ, Maven, Jackson.

---

## Spec
`docs/superpowers/specs/2026-05-30-feature-model-redesign-design.md`

## Carte des fichiers touchés

Les anciens flags (`keycloak/redis/rabbitmq/websocket/admin/grafana/loki`) sont référencés UNIQUEMENT dans 4 fichiers (vérifié par grep).

**Production (main) :**
- `src/main/java/com/mr486/generator/dto/FeatureOptions.java` — réécrit (2 flags).
- `src/main/java/com/mr486/generator/dto/BatchOptions.java` — ajout `grafana`.
- `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` — méthode `include` + suppression de `contains`.
- `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` — SIX emplacements :
  - `desiredModules()` (~L120) — `ms-auth` permanent, `ms-admin` via springbootAdmin, `service-batch` via batch.enabled.
  - `volumesToRemove()` (~L188) — ne retire plus keycloak/redis.
  - `blocksToRemove()` (~L207) — ne retire plus keycloak/redis/rabbitmq.
  - `addResourceBlocks()` (~L243) — `boolean keycloak = ...isKeycloak()` → toujours `true`.
  - `rewriteGatewayYml()` (~L349) — retrait conditionnel route ms-auth supprimé.
  - `rewriteTestAll()` (~L522/531/536) + section infra (~L583) — `batchEnabled`, blocs `wait_for` et "Admin OK".

**Tests :**
- `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java` — réécrit.
- `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java` — 8 tests supprimés, 3 convertis.
- `src/test/java/com/mr486/generator/dto/FeatureOptionsDeserializationTest.java` — créé.

**Helpers réutilisables** (`ProcessorTestHelper`, NE PAS modifier) : `file(path, content[, executable])`, `defaultCtx()`, `ctxWithFeatures(FeatureOptions)`, `contentOf(GeneratedFile)`.

## Conventions
- Lombok `@Data` : `boolean springbootAdmin` → `isSpringbootAdmin()` / `setSpringbootAdmin(boolean)`.
- Clés JSON camelCase (`springbootAdmin`, `clientWebUI`), pas de `@JsonProperty`.
- Le **module** template reste `ms-client` ; seul le **flag** s'appelle `clientWebUI`.
- **Le build est l'oracle.** Le main ne compile qu'après les Tasks 1.1–1.8 ; les tests ne compilent qu'après les Tasks 2.1–2.2. Checkpoint : `mvn -q compile` (main) après la Phase 1.
- **Option 1 (validée)** : aucune référence à `ms-client`/`admin-application` ajoutée en Phase 1.

---

## Task 1.1 : Réécrire `FeatureOptions`

**Files:**
- Modify: `src/main/java/com/mr486/generator/dto/FeatureOptions.java`

- [ ] **Step 1: Remplacer entièrement le fichier**

```java
package com.mr486.generator.dto;

import lombok.Data;

/**
 * Bascules d'activation des composants optionnels de la plateforme.
 * <p>
 * keycloak (+ ms-auth), redis, rabbitmq, websocket et admin-application sont désormais TOUJOURS
 * installés et n'ont plus de bascule. L'observabilité (loki + promtail + grafana) est pilotée par
 * {@link BatchOptions#isGrafana()}. Seuls les deux modules ci-dessous restent optionnels.
 */
@Data
public class FeatureOptions {
    /** Si {@code false} (défaut), retire le module {@code ms-admin} (monitoring Spring Boot Admin). */
    private boolean springbootAdmin = false;
    /** Si {@code false} (défaut), retire le module {@code ms-client} (UI Thymeleaf). [module créé en Phase 2] */
    private boolean clientWebUI = false;
}
```

- [ ] **Step 2: Vérifier**

Run: `grep -c 'private boolean' src/main/java/com/mr486/generator/dto/FeatureOptions.java`
Expected: `2`

---

## Task 1.2 : Ajouter `grafana` à `BatchOptions`

**Files:**
- Modify: `src/main/java/com/mr486/generator/dto/BatchOptions.java`

- [ ] **Step 1: Ajouter le champ après `enabled`**

Remplacer :
```java
    /** Si {@code false}, retire le module et le service Docker service-batch. */
    private boolean enabled = true;
```
par :
```java
    /** Si {@code false}, retire le module et le service Docker service-batch. */
    private boolean enabled = true;
    /** Si {@code true}, installe l'observabilité complète : loki + promtail + grafana. Défaut: false. */
    private boolean grafana = false;
```

- [ ] **Step 2: Vérifier**

Run: `grep -c 'private boolean grafana = false;' src/main/java/com/mr486/generator/dto/BatchOptions.java`
Expected: `1`

---

## Task 1.3 : Réécrire `FeatureFilterProcessor.include()`

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`

- [ ] **Step 1: Remplacer la méthode `include`**

```java
    private boolean include(String path, String root, FeatureOptions f, BatchOptions b) {
        String rel = relative(path, root);
        // springboot-admin (monitoring) optionnel
        if (!f.isSpringbootAdmin() && rel.startsWith("ms-admin/"))      return false;
        // ms-client (UI) optionnel — module créé en Phase 2 ; règle inerte tant que le dossier est absent
        if (!f.isClientWebUI()     && rel.startsWith("ms-client/"))     return false;
        // observabilité complète (loki + promtail + grafana) pilotée par batch.grafana
        if (!b.isGrafana()         && rel.startsWith("observability/")) return false;
        // service-batch piloté par batch.enabled seul (rabbitmq toujours présent)
        if (!b.isEnabled()         && rel.startsWith("service-batch/")) return false;
        return true;
    }
```

- [ ] **Step 2: Supprimer la méthode `contains` (devenue inutile)**

Supprimer ce bloc :
```java
    private boolean contains(String rel, String fragment) {
        return rel.contains(fragment);
    }
```

- [ ] **Step 3: Vérifier**

Run: `grep -cE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin|isLoki|isGrafana|private boolean contains' src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
Expected: `0`

---

## Task 1.4 : `CrossCuttingConfigProcessor.desiredModules()`

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: Remplacer le corps de `desiredModules`**

```java
    private List<String> desiredModules(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        BatchOptions b = req.getBatch();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        List<String> modules = new ArrayList<>();
        modules.add("common-lib");
        modules.add("ms-eureka");
        modules.add("ms-gateway");
        modules.add("ms-auth");                 // keycloak permanent
        if (!hasResources) {
            modules.add("service-a");
            modules.add("service-b");
            modules.add("service-c");
        }
        modules.add("service-consumer");
        if (b.isEnabled())            modules.add("service-batch");
        if (f.isSpringbootAdmin())    modules.add("ms-admin");
        if (hasResources) {
            for (ResourceModuleRequest r : req.getResources()) modules.add(r.getServiceName());
        }
        return modules;
    }
```

- [ ] **Step 2: Vérifier**

Run: `grep -c 'modules.add("ms-auth")' src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
Expected: `1`

---

## Task 1.5 : `CrossCuttingConfigProcessor.blocksToRemove()`

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: Remplacer le corps de `blocksToRemove`**

```java
    private List<String> blocksToRemove(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        FeatureOptions f = req.getFeatures();
        BatchOptions b = req.getBatch();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        List<String> blocks = new ArrayList<>();
        if (!b.isEnabled())          blocks.add("service-batch");
        if (!b.isGrafana()) { blocks.add("loki"); blocks.add("promtail"); blocks.add("grafana"); }
        if (!f.isSpringbootAdmin())  blocks.add("ms-admin");
        if (hasResources) {
            blocks.add("service-a-db");
            blocks.add("service-b-db");
            blocks.add("service-a");
            blocks.add("service-b");
            blocks.add("service-c");
        }
        return blocks;
    }
```

- [ ] **Step 2: Vérifier**

Run: `grep -cE 'blocks.add\("keycloak"\)|blocks.add\("ms-auth"\)|blocks.add\("rabbitmq"\)|blocks.add\("redis"\)' src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
Expected: `0`

---

## Task 1.6 : `volumesToRemove()`, `addResourceBlocks()`, `rewriteGatewayYml()`

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: Remplacer le corps de `volumesToRemove`**

```java
    private List<String> volumesToRemove(GenerationContext ctx) {
        PlatformGenerationRequest req = ctx.getRequest();
        boolean hasResources = req.getResources() != null && !req.getResources().isEmpty();

        List<String> vols = new ArrayList<>();
        if (hasResources) {
            vols.add("service_a_db_data");
            vols.add("service_b_db_data");
        }
        return vols;
    }
```

- [ ] **Step 2: Dans `addResourceBlocks`, forcer keycloak à true**

Remplacer :
```java
        boolean keycloak = req.getFeatures().isKeycloak();
```
par :
```java
        boolean keycloak = true; // keycloak permanent — les blocs resource incluent toujours la dép + KEYCLOAK_ISSUER_URI
```
(La signature `buildResourceServiceBlock(r, keycloak)` est conservée ; le paramètre vaut désormais toujours `true`.)

- [ ] **Step 3: Dans `rewriteGatewayYml`, supprimer le retrait conditionnel de la route ms-auth**

Supprimer ce bloc :
```java
        if (!req.getFeatures().isKeycloak()) {
            text = removeGatewayRoute(text, "ms-auth");
        }
```
`req` reste utilisé pour `getResources()` — ne pas le supprimer.

- [ ] **Step 4: Vérifier**

Run: `grep -cE 'keycloak_db_data|redis_data|removeGatewayRoute\(text, "ms-auth"\)|getFeatures\(\).isKeycloak\(\)' src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
Expected: `0`

---

## Task 1.7 : `rewriteTestAll()` — wait_for + batchEnabled + infra

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`

- [ ] **Step 1: `batchEnabled` ne dépend plus de rabbitmq**

Remplacer :
```java
        boolean batchEnabled = feat.isRabbitmq() && batch != null && batch.isEnabled();
```
par :
```java
        boolean batchEnabled = batch != null && batch.isEnabled();
```

- [ ] **Step 2: wait_for ms-auth toujours émis**

Remplacer :
```java
        if (feat.isKeycloak()) sb.append("wait_for 'ms-auth + keycloak' auth_ready\n");
```
par :
```java
        sb.append("wait_for 'ms-auth + keycloak' auth_ready\n"); // keycloak permanent
```

- [ ] **Step 3: wait_for ms-admin piloté par springbootAdmin**

Remplacer :
```java
        if (feat.isAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
```
par :
```java
        if (feat.isSpringbootAdmin()) sb.append("wait_for 'ms-admin' curl -fs http://localhost:9100\n");
```

- [ ] **Step 4: section infrastructure "Admin OK" pilotée par springbootAdmin**

Remplacer :
```java
        if (feat.isAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
```
par :
```java
        if (feat.isSpringbootAdmin()) sb.append("curl -fs http://localhost:9100 >/dev/null && echo 'Admin OK'\n");
```

- [ ] **Step 5: Vérifier qu'aucun ancien flag ne subsiste dans tout le fichier**

Run: `grep -cE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin\(\)|isLoki|isGrafana' src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
Expected: `0`

---

## Task 1.8 : Vérifier la compilation du main + commit checkpoint

**Files:** aucun (vérification + commit).

- [ ] **Step 1: Aucun ancien flag dans tout le main**

Run: `grep -rnE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin\(\)|isLoki|isGrafana|setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin|setLoki|setGrafana' src/main/java/`
Expected: aucune ligne (rc=1).

- [ ] **Step 2: Compiler les sources principales**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS` (les tests ne compilent pas encore — attendu).

- [ ] **Step 3: Commit checkpoint**

```bash
git add src/main/java/com/mr486/generator/dto/FeatureOptions.java \
        src/main/java/com/mr486/generator/dto/BatchOptions.java \
        src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java \
        src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java
git commit -m "feat(generator): redesign feature model (main) — permanent core, batch.grafana drives observability"
```

---

## Task 2.1 : Réécrire `FeatureFilterProcessorTest`

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`

- [ ] **Step 1: Remplacer entièrement le fichier**

```java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class FeatureFilterProcessorTest {

    private final FeatureFilterProcessor processor = new FeatureFilterProcessor();

    private List<GeneratedFile> sampleFiles() {
        return List.of(
            file("ms-platform/keycloak/import/realm.json", "{}"),
            file("ms-platform/ms-auth/pom.xml", "<project/>"),
            file("ms-platform/ms-admin/pom.xml", "<project/>"),
            file("ms-platform/ms-client/pom.xml", "<project/>"),
            file("ms-platform/observability/grafana/dashboards/d.json", "{}"),
            file("ms-platform/observability/promtail/config.yml", ""),
            file("ms-platform/observability/loki/config.yml", ""),
            file("ms-platform/service-batch/pom.xml", "<project/>"),
            file("ms-platform/service-consumer/src/main/java/x/RabbitConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/RedisConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/WebSocketConfig.java", "class W{}"),
            file("ms-platform/service-consumer/src/main/resources/static/batch-notifications.html", "<html/>"),
            file("ms-platform/docker-compose.yml", "services:")
        );
    }

    @Test
    void keeps_permanent_components_regardless_of_options() {
        // defaults: springbootAdmin=false, clientWebUI=false, batch.grafana=false, batch.enabled=true
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).anyMatch(e -> e.path().contains("/keycloak/"));
        assertThat(result).anyMatch(e -> e.path().contains("/ms-auth/"));
        assertThat(result).anyMatch(e -> e.path().endsWith("RedisConfig.java"));
        assertThat(result).anyMatch(e -> e.path().endsWith("RabbitConfig.java"));
        assertThat(result).anyMatch(e -> e.path().endsWith("WebSocketConfig.java"));
        assertThat(result).anyMatch(e -> e.path().endsWith("batch-notifications.html"));
    }

    @Test
    void removes_ms_admin_when_springboot_admin_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).noneMatch(e -> e.path().contains("/ms-admin/"));
    }

    @Test
    void keeps_ms_admin_when_springboot_admin_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setSpringbootAdmin(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).anyMatch(e -> e.path().contains("/ms-admin/"));
    }

    @Test
    void removes_ms_client_when_client_web_ui_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).noneMatch(e -> e.path().contains("/ms-client/"));
    }

    @Test
    void keeps_ms_client_when_client_web_ui_enabled() {
        FeatureOptions f = new FeatureOptions();
        f.setClientWebUI(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).anyMatch(e -> e.path().contains("/ms-client/"));
    }

    @Test
    void removes_all_observability_when_grafana_disabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).noneMatch(e -> e.path().contains("/observability/"));
    }

    @Test
    void keeps_all_observability_when_grafana_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setGrafana(true);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).anyMatch(e -> e.path().contains("/observability/grafana/"));
        assertThat(result).anyMatch(e -> e.path().contains("/observability/loki/"));
        assertThat(result).anyMatch(e -> e.path().contains("/observability/promtail/"));
    }

    @Test
    void removes_service_batch_when_batch_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).noneMatch(e -> e.path().contains("/service-batch/"));
    }

    @Test
    void keeps_service_batch_when_batch_enabled() {
        List<GeneratedFile> result = processor.process(sampleFiles(), defaultCtx());
        assertThat(result).anyMatch(e -> e.path().contains("/service-batch/"));
    }

    @Test
    void filters_relative_to_target_root_not_source() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-app");
        // springbootAdmin=false -> ms-admin removed under the renamed root
        List<GeneratedFile> files = List.of(
            file("my-app/ms-admin/pom.xml", "<project/>"),
            file("my-app/service-consumer/pom.xml", "<project/>")
        );
        List<GeneratedFile> result = processor.process(files, GenerationContext.from(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("my-app/service-consumer/pom.xml");
    }

    @Test
    void always_keeps_docker_compose_and_root_files() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).anyMatch(e -> e.path().endsWith("docker-compose.yml"));
    }
}
```

- [ ] **Step 2: Vérifier l'absence d'anciens flags**

Run: `grep -cE 'setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin|setLoki|setGrafana\b' src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`
Expected: `0`

---

## Task 2.2 : Mettre à jour `CrossCuttingConfigProcessorTest`

**Files:**
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`

Dispositions précises (vérifiées par grep ligne à ligne). **Supprimer** les 8 tests dont le comportement n'existe plus, **convertir** les 3 tests utilisant des flags renommés/déplacés. Tous les autres tests (resource blocks, realm, test-all, aggregate, swaps par resource, `compose_resource_blocks_inserted_before_volumes_section`, `compose_removes_default_service_volumes_when_resources_provided`) restent inchangés.

- [ ] **Step 1: SUPPRIMER ces 8 tests entiers (méthode + annotation `@Test`)**

  1. `root_pom_excludes_ms_auth_when_keycloak_disabled` — ms-auth désormais permanent.
  2. `compose_removes_keycloak_and_ms_auth_blocks_when_keycloak_disabled` — blocs permanents.
  3. `gateway_yml_drops_ms_auth_route_when_keycloak_disabled` — route permanente.
  4. `compose_resource_blocks_omit_keycloak_dep_when_keycloak_disabled` — la dép keycloak est désormais toujours incluse.
  5. `compose_removes_keycloak_db_data_volume_when_keycloak_disabled` — volume permanent.
  6. `compose_removes_redis_data_volume_when_redis_disabled` — volume permanent.
  7. `compose_cleans_dangling_depends_on_when_rabbitmq_disabled` — rabbitmq jamais retiré.
  8. `compose_cleans_dangling_depends_on_when_keycloak_disabled` — keycloak jamais retiré.

- [ ] **Step 2: CONVERTIR `root_pom_includes_all_default_modules_when_all_features_enabled`**

Le test asserte la présence de `<module>ms-admin</module>`, qui exige maintenant `springbootAdmin=true`.
Remplacer :
```java
        FeatureOptions f = new FeatureOptions(); f.setGrafana(true); f.setLoki(true);
```
par :
```java
        FeatureOptions f = new FeatureOptions(); f.setSpringbootAdmin(true);
```

- [ ] **Step 3: CONVERTIR `compose_keeps_all_blocks_when_all_features_enabled`**

Ce test asserte keycloak/ms-auth/service-a (tous permanents).
Remplacer :
```java
        FeatureOptions f = new FeatureOptions(); f.setGrafana(true); f.setLoki(true);
```
par :
```java
        FeatureOptions f = new FeatureOptions();
```

- [ ] **Step 4: CONVERTIR `root_pom_excludes_ms_admin_when_admin_disabled`**

Remplacer :
```java
        FeatureOptions f = new FeatureOptions(); f.setAdmin(false);
```
par :
```java
        FeatureOptions f = new FeatureOptions(); f.setSpringbootAdmin(false);
```

- [ ] **Step 5: Confirmer l'assertion « ms-auth toujours présent » dans le test des routes par resource**

Run: `grep -c '.contains("- id: ms-auth")' src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
Expected: `>= 1` (déjà présent dans `gateway_yml_swaps_default_services_for_resource_routes`).

- [ ] **Step 6: Scanner qu'aucun ancien flag ne subsiste**

Run: `grep -cE 'setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin\b|setLoki|setGrafana\b' src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
Expected: `0`

- [ ] **Step 7: Compiler et lancer les tests des processors**

Run: `mvn -q test -Dtest='FeatureFilterProcessorTest,CrossCuttingConfigProcessorTest'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.
Si un test échoue : soit son attendu reflète l'ancien comportement (le supprimer/convertir selon les règles ci-dessus), soit il révèle un vrai bug dans les Tasks 1.4–1.7 (corriger le processor — ne jamais adapter le code de prod juste pour faire passer un test trompeur).

---

## Task 2.3 : Test de désérialisation Jackson tolérante

**Files:**
- Create: `src/test/java/com/mr486/generator/dto/FeatureOptionsDeserializationTest.java`

- [ ] **Step 1: Créer le fichier**

```java
package com.mr486.generator.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FeatureOptionsDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void maps_camel_case_flags() throws Exception {
        String json = "{\"springbootAdmin\":true,\"clientWebUI\":true}";
        FeatureOptions f = mapper.readValue(json, FeatureOptions.class);
        assertThat(f.isSpringbootAdmin()).isTrue();
        assertThat(f.isClientWebUI()).isTrue();
    }

    @Test
    void ignores_legacy_unknown_flags() throws Exception {
        // Une ancienne commande utilisant les flags supprimés ne doit pas casser la désérialisation.
        String json = "{\"keycloak\":true,\"redis\":true,\"loki\":false,\"springbootAdmin\":true}";
        FeatureOptions f = mapper.readValue(json, FeatureOptions.class);
        assertThat(f.isSpringbootAdmin()).isTrue();
        assertThat(f.isClientWebUI()).isFalse(); // défaut
    }
}
```

- [ ] **Step 2: Vérifier**

Run: `mvn -q test -Dtest=FeatureOptionsDeserializationTest`
Expected: `BUILD SUCCESS`, 2 tests passent.

---

## Task 3.1 : Build complet

**Files:** aucun (vérification).

- [ ] **Step 1: Build + tests**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS` ; `Tests run: N, Failures: 0, Errors: 0`.

- [ ] **Step 2: Confirmer la parité template inchangée**

`TemplateLoaderTest` (compteur 112) doit passer sans modification — aucun fichier template n'a été ajouté/retiré.

---

## Task 3.2 : Génération end-to-end (commande de référence)

**Files:** aucun (vérification). **Port dédié 8077** pour éviter le piège du serveur zombie sur :8080.

- [ ] **Step 1: Lancer le générateur en arrière-plan**

```bash
pkill -9 -f 'springboot-platform-generator' 2>/dev/null; sleep 2
setsid java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 \
  > /tmp/genapp.log 2>&1 < /dev/null &
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
```

- [ ] **Step 2: Générer avec la commande de référence (flags camelCase)**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform", "groupId": "com.acme", "basePackage": "com.acme.shop", "javaVersion": "17",
    "resources": [
      { "serviceName": "order-service",     "className": "Order",   "routePrefix": "/api/orders",   "databaseType": "POSTGRES", "idType": "LONG" },
      { "serviceName": "product-service",   "className": "Product", "routePrefix": "/api/products", "databaseType": "MONGO" },
      { "serviceName": "inventory-service", "className": "Item",    "routePrefix": "/api/items",    "databaseType": "H2", "idType": "UUID" }
    ],
    "batch": { "enabled": true, "replicas": 4, "fileConcurrency": 5, "minDelayMs": 500, "maxDelayMs": 1500, "memoryLimit": "768m", "grafana": true },
    "features": { "springbootAdmin": true, "clientWebUI": true }
  }' \
  -o /tmp/ref.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refx && mkdir -p /tmp/refx && unzip -q /tmp/ref.zip -d /tmp/refx
pkill -9 -f 'springboot-platform-generator' 2>/dev/null
```

- [ ] **Step 3: Inspecter**

```bash
echo "=== modules ==="; grep -E '<module>' /tmp/refx/ms-platform/pom.xml
echo "=== blocs compose ==="; grep -E '^  [a-z].*:' /tmp/refx/ms-platform/docker-compose.yml
echo "=== observability ==="; ls /tmp/refx/ms-platform/observability/ 2>&1
echo "=== ms-admin ==="; ls /tmp/refx/ms-platform/ms-admin/pom.xml 2>&1
echo "=== ms-client (doit être ABSENT en Phase 1) ==="; ls /tmp/refx/ms-platform/ms-client 2>&1
```

Expected:
- `HTTP=200`.
- pom contient `ms-auth`, `service-consumer`, `service-batch`, `ms-admin`, `order-service`, `product-service`, `inventory-service` ; PAS `service-a/b/c`.
- compose contient `keycloak`, `ms-auth`, `redis`, `rabbitmq`, `loki`, `promtail`, `grafana`, `service-batch`, `ms-admin`.
- `observability/` existe (grafana/loki/promtail).
- `ms-admin/pom.xml` existe.
- `ms-client` → « No such file or directory » (normal Phase 1).

---

## Task 3.3 : Valider le projet généré

**Files:** aucun (vérification).

- [ ] **Step 1: docker-compose**

Run: `cd /tmp/refx/ms-platform && (cp dist.env .env 2>/dev/null||true) && docker compose config >/dev/null && echo COMPOSE_OK`
Expected: `COMPOSE_OK` (aucun `depends_on` orphelin).

- [ ] **Step 2: maven du projet généré**

Run: `cd /tmp/refx/ms-platform && mvn -q -DskipTests package 2>&1 | tail -5`
Expected: `BUILD SUCCESS` (aucun module orphelin).
Si Docker/Maven indisponible : le noter explicitement comme NON vérifié, ne pas affirmer le succès.

---

## Task 3.4 : Commit final

**Files:** tous les fichiers de tests modifiés/créés.

- [ ] **Step 1:**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add -A && git commit -m "test(generator): realign feature-model tests; add Jackson tolerance test

FeatureFilterProcessorTest rewritten; 8 obsolete CrossCutting tests removed,
3 converted (admin->springbootAdmin, grafana->batch). Adds deserialization
test proving legacy flags are ignored. No template files changed."
```

- [ ] **Step 2: Vérifier**

Run: `git status` → working tree clean ; `git log --oneline -2`.

---

## Recovery
- `git log --oneline -5` — commits déjà passés.
- `grep -rnE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin\(\)|isLoki|isGrafana' src/main/java/` — si vide, Tasks 1.3–1.7 faites.
- `mvn -q compile` SUCCESS → Phase 1 complète. `mvn -q test` SUCCESS → Phase 2 complète.
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Modules `ms-client` (Phase 2) et `admin-application` (Phase 3).
- `BatchConfigProcessor` ne touche pas grafana — aucun changement requis.
