---
date: 2026-05-30
plan_id: feature-model-redesign
status: draft
---

# Phase 1 — Refonte du modèle de features — Implementation Plan

## Goal
Simplifier le modèle de features du générateur GestoMS : keycloak/redis/rabbitmq/websocket
deviennent permanents (plus de toggle), l'observabilité (loki+promtail+grafana) est pilotée par
`batch.grafana`, et seuls `springbootAdmin` (module ms-admin) et `clientWebUI` (module ms-client, créé
en Phase 2) restent optionnels. Aucune nouvelle application n'est générée dans cette phase — c'est la
fondation qui débloque les Phases 2 et 3.

## Context
- Le générateur transforme un template décompressé (`src/main/resources/templates/ms-platform/`,
  112 fichiers) via une chaîne de processors Spring ordonnés par `@Order`.
- Spec de référence : `docs/superpowers/specs/2026-05-30-feature-model-redesign-design.md`.
- Les anciens flags (`keycloak/redis/rabbitmq/websocket/admin/grafana/loki`) sont référencés UNIQUEMENT
  dans 4 fichiers :
  - `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
  - `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
  - `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`
  - `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`
- `BatchConfigProcessor` ne gère PAS grafana (il n'injecte que les placeholders `BATCH_*`) et n'a rien à
  changer. `ResourceExpandProcessor` / `PackagePlaceholderProcessor` n'utilisent pas les features.
- **Contrainte de compilation** : retirer les getters de `FeatureOptions` casse simultanément le main
  (`CrossCuttingConfigProcessor`) ET les deux tests. Le build n'est donc vert qu'après la Phase 2.
  Checkpoint intermédiaire : `mvn compile` (main seul) passe après la Phase 1.
- **Option 1 (validée)** : aucune référence à `ms-client`/`admin-application` n'est ajoutée dans
  `CrossCuttingConfigProcessor` en Phase 1 (ces modules n'existent pas encore dans le template).

## Tech Stack
Java 17, Spring Boot, Lombok (`@Data`), JUnit 5 + AssertJ, Maven. Jackson via Spring Boot
(`spring.jackson` ; `FAIL_ON_UNKNOWN_PROPERTIES=false` par défaut).

## Conventions
- Lombok `@Data` génère getters/setters : un `boolean springbootAdmin` produit `isSpringbootAdmin()` /
  `setSpringbootAdmin(boolean)`.
- Pas de `@JsonProperty` : les clés JSON sont en camelCase (`springbootAdmin`, `clientWebUI`), mappées
  nativement par Jackson.
- Le **module** template reste `ms-client` ; seul le **flag** s'appelle `clientWebUI`.
- Conserver le style des fichiers existants (alignement des `if` dans les processors, Javadoc en
  français).

---

## Phase 1 : DTOs et processors (main compile)

### Task 1.1 : Réécrire `FeatureOptions`
**Files:**
- `src/main/java/com/mr486/generator/dto/FeatureOptions.java` — remplacer entièrement le contenu.

**Step 1: Remplacer le fichier**
Écrire ce contenu exact :

```java
package com.mr486.generator.dto;

import lombok.Data;

/**
 * Bascules d'activation des composants optionnels de la plateforme.
 * <p>
 * keycloak (+ ms-auth), redis, rabbitmq, websocket et admin-application sont désormais TOUJOURS
 * installés et n'ont plus de bascule. L'observabilité (loki + promtail + grafana) est pilotée par
 * {@link BatchOptions#isGrafana()}. Seuls les deux modules ci-dessous restent optionnels.
 * <p>
 * Quand un drapeau passe à {@code false}, le {@link com.mr486.generator.pipeline.processor.FeatureFilterProcessor}
 * retire les fichiers du module concerné et le
 * {@link com.mr486.generator.pipeline.processor.CrossCuttingConfigProcessor} nettoie les références
 * correspondantes (pom racine, docker-compose, routes du gateway).
 */
@Data
public class FeatureOptions {
    /** Si {@code false} (défaut), retire le module {@code ms-admin} (monitoring Spring Boot Admin). */
    private boolean springbootAdmin = false;
    /** Si {@code false} (défaut), retire le module {@code ms-client} (UI Thymeleaf). [module créé en Phase 2] */
    private boolean clientWebUI = false;
}
```

**Verification:**
- [ ] `grep -c 'springbootAdmin\|clientWebUI' src/main/java/com/mr486/generator/dto/FeatureOptions.java` retourne `2` ou plus.
- [ ] `grep -cE 'keycloak|redis|rabbitmq|websocket|loki|grafana|admin ' src/main/java/com/mr486/generator/dto/FeatureOptions.java` retourne `0` (hors Javadoc — utiliser `grep -c 'private boolean'` doit retourner `2`).

### Task 1.2 : Ajouter `grafana` à `BatchOptions`
**Files:**
- `src/main/java/com/mr486/generator/dto/BatchOptions.java` — ajouter un champ.

**Step 1: Ajouter le champ `grafana` après `enabled`**
Remplacer cette ligne :
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

**Verification:**
- [ ] `grep -c 'private boolean grafana = false;' src/main/java/com/mr486/generator/dto/BatchOptions.java` retourne `1`.

### Task 1.3 : Réécrire `FeatureFilterProcessor.include()`
**Files:**
- `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` — remplacer la méthode `include`.

**Step 1: Remplacer la méthode `include`**
Remplacer le bloc actuel (de `private boolean include(` jusqu'à son `}` fermant, lignes 34–51) par :

```java
    private boolean include(String path, String root, FeatureOptions f, BatchOptions b) {
        String rel = relative(path, root);
        // springboot-admin (monitoring) optionnel
        if (!f.isSpringbootAdmin() && rel.startsWith("ms-admin/"))      return false;
        // ms-client (UI) optionnel — le module arrive en Phase 2 ; règle inerte tant que le dossier est absent
        if (!f.isClientWebUI()     && rel.startsWith("ms-client/"))     return false;
        // observabilité complète (loki + promtail + grafana) pilotée par batch.grafana
        if (!b.isGrafana()         && rel.startsWith("observability/")) return false;
        // service-batch piloté par batch.enabled seul (rabbitmq toujours présent)
        if (!b.isEnabled()         && rel.startsWith("service-batch/")) return false;
        return true;
    }
```

**Step 2: Supprimer la méthode `contains` devenue inutile**
Supprimer ce bloc (il n'est plus appelé) :
```java
    private boolean contains(String rel, String fragment) {
        return rel.contains(fragment);
    }
```

**Verification:**
- [ ] `grep -cE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin|isLoki|isGrafana' src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` retourne `0`.
- [ ] `grep -c 'private boolean contains' src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` retourne `0`.
- [ ] `grep -c 'isSpringbootAdmin\|isClientWebUI\|isGrafana()\|isEnabled()' src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` retourne `4` ou plus.

### Task 1.4 : Réaligner `CrossCuttingConfigProcessor.desiredModules()`
**Files:**
- `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` — méthode `desiredModules`.

**Step 1: Remplacer le corps de `desiredModules`**
Remplacer la méthode actuelle (lignes ~120–143) par :

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

**Verification:**
- [ ] `grep -c 'modules.add("ms-auth")' …/CrossCuttingConfigProcessor.java` retourne `1`.
- [ ] `grep -c 'f.isKeycloak()' …/CrossCuttingConfigProcessor.java` (méthode entière) ne contient plus `if (f.isKeycloak()) modules.add("ms-auth")` — voir verif globale Task 1.7.

### Task 1.5 : Réaligner `CrossCuttingConfigProcessor.blocksToRemove()`
**Files:**
- `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` — méthode `blocksToRemove`.

**Step 1: Remplacer le corps de `blocksToRemove`**
Remplacer la méthode actuelle (lignes ~207–233) par :

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

**Verification:**
- [ ] `grep -c 'blocks.add("keycloak")\|blocks.add("ms-auth")\|blocks.add("rabbitmq")\|blocks.add("redis")' …/CrossCuttingConfigProcessor.java` retourne `0`.
- [ ] `grep -c 'blocks.add("loki")' …/CrossCuttingConfigProcessor.java` retourne `1`.

### Task 1.6 : Réaligner `volumesToRemove()` et `rewriteGatewayYml()`
**Files:**
- `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` — méthodes `volumesToRemove` et `rewriteGatewayYml`.

**Step 1: Remplacer le corps de `volumesToRemove`**
Remplacer la méthode actuelle (lignes ~188–201) par :

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

**Step 2: Retirer le retrait conditionnel de la route ms-auth dans `rewriteGatewayYml`**
Dans `rewriteGatewayYml`, supprimer ce bloc (la route ms-auth ne doit plus jamais être retirée) :
```java
        if (!req.getFeatures().isKeycloak()) {
            text = removeGatewayRoute(text, "ms-auth");
        }
```
Le reste de la méthode (retrait service-a/b/c + `addGatewayRoutes` par resource) est inchangé. Si après
suppression la variable `FeatureOptions`/`req.getFeatures()` n'est plus utilisée dans la méthode,
supprimer aussi sa déclaration locale pour éviter un warning « unused ».

**Verification:**
- [ ] `grep -c 'keycloak_db_data\|redis_data' …/CrossCuttingConfigProcessor.java` retourne `0`.
- [ ] `grep -c 'removeGatewayRoute(text, "ms-auth")' …/CrossCuttingConfigProcessor.java` retourne `0`.

### Task 1.7 : Vérifier que le main compile sans référence aux anciens flags
**Files:** aucun (vérification).

**Step 1: Confirmer l'absence d'anciens flags dans le main**
```bash
grep -rnE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin|isLoki|isGrafana|setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin|setLoki|setGrafana' src/main/java/
```

**Step 2: Compiler les sources principales**
```bash
mvn -q compile
```

**Verification:**
- [ ] Le `grep` du Step 1 ne retourne **aucune** ligne (rc=1).
- [ ] `mvn -q compile` se termine par `BUILD SUCCESS` (les sources main compilent ; les tests ne sont pas
  encore compilés à ce stade — c'est attendu).

---

## Phase 2 : Tests (build vert complet)

### Task 2.1 : Réécrire `FeatureFilterProcessorTest`
**Files:**
- `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java` — remplacer entièrement.

**Step 1: Remplacer le fichier**
Écrire ce contenu exact :

```java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FeatureFilterProcessorTest {

    private final FeatureFilterProcessor processor = new FeatureFilterProcessor();

    private GeneratedFile file(String path) {
        return new GeneratedFile(path, "x".getBytes(StandardCharsets.UTF_8), false);
    }

    private GenerationContext ctx(PlatformGenerationRequest req) {
        return GenerationContext.from(req);
    }

    @Test
    void keeps_permanent_modules_regardless_of_features() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        // defaults: springbootAdmin=false, clientWebUI=false, batch.grafana=false
        List<GeneratedFile> files = List.of(
            file("ms-platform/keycloak/import/realm.json"),
            file("ms-platform/ms-auth/pom.xml"),
            file("ms-platform/service-consumer/src/main/java/x/RedisConfig.java"),
            file("ms-platform/service-consumer/src/main/java/x/RabbitConfig.java"),
            file("ms-platform/service-consumer/src/main/java/x/WebSocketConfig.java"),
            file("ms-platform/service-consumer/src/main/resources/static/batch-notifications.html")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).hasSize(6);
    }

    @Test
    void removes_ms_admin_when_springboot_admin_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        // springbootAdmin defaults to false
        List<GeneratedFile> files = List.of(
            file("ms-platform/ms-admin/pom.xml"),
            file("ms-platform/ms-eureka/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("ms-platform/ms-eureka/pom.xml");
    }

    @Test
    void keeps_ms_admin_when_springboot_admin_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getFeatures().setSpringbootAdmin(true);
        List<GeneratedFile> files = List.of(
            file("ms-platform/ms-admin/pom.xml"),
            file("ms-platform/ms-eureka/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).hasSize(2);
    }

    @Test
    void removes_ms_client_when_client_web_ui_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        // clientWebUI defaults to false
        List<GeneratedFile> files = List.of(
            file("ms-platform/ms-client/pom.xml"),
            file("ms-platform/ms-eureka/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("ms-platform/ms-eureka/pom.xml");
    }

    @Test
    void keeps_ms_client_when_client_web_ui_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getFeatures().setClientWebUI(true);
        List<GeneratedFile> files = List.of(
            file("ms-platform/ms-client/pom.xml"),
            file("ms-platform/ms-eureka/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).hasSize(2);
    }

    @Test
    void removes_all_observability_when_grafana_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        // batch.grafana defaults to false
        List<GeneratedFile> files = List.of(
            file("ms-platform/observability/grafana/cfg.yml"),
            file("ms-platform/observability/loki/cfg.yml"),
            file("ms-platform/observability/promtail/cfg.yml"),
            file("ms-platform/ms-eureka/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("ms-platform/ms-eureka/pom.xml");
    }

    @Test
    void keeps_all_observability_when_grafana_enabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setGrafana(true);
        List<GeneratedFile> files = List.of(
            file("ms-platform/observability/grafana/cfg.yml"),
            file("ms-platform/observability/loki/cfg.yml"),
            file("ms-platform/observability/promtail/cfg.yml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).hasSize(3);
    }

    @Test
    void removes_service_batch_when_batch_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        List<GeneratedFile> files = List.of(
            file("ms-platform/service-batch/pom.xml"),
            file("ms-platform/service-a/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("ms-platform/service-a/pom.xml");
    }

    @Test
    void filters_relative_to_target_root_not_source() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-app");
        // springbootAdmin=false -> ms-admin removed under the renamed root
        List<GeneratedFile> files = List.of(
            file("my-app/ms-admin/pom.xml"),
            file("my-app/service-a/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("my-app/service-a/pom.xml");
    }

    @Test
    void keeps_files_outside_feature_dirs() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        List<GeneratedFile> files = List.of(
            file("ms-platform/pom.xml"),
            file("ms-platform/service-consumer/pom.xml")
        );
        List<GeneratedFile> result = processor.process(files, ctx(req));
        assertThat(result).hasSize(2);
    }
}
```

**Verification:**
- [ ] `grep -cE 'setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin|setLoki|setGrafana\b' src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java` retourne `0`.
- [ ] (après Task 2.2) `mvn -q -Dtest=FeatureFilterProcessorTest test` → `BUILD SUCCESS`.

### Task 2.2 : Mettre à jour `CrossCuttingConfigProcessorTest`
**Files:**
- `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java` — modifier les tests référençant les anciens flags.

**Step 1: Remplacer le test `removes_keycloak_compose_blocks_when_disabled`**
Ce comportement n'existe plus (keycloak permanent). Remplacer le test entier par un test de
non-régression « keycloak/ms-auth toujours présents » :

```java
    @Test
    void keeps_keycloak_and_ms_auth_compose_blocks_always() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        String compose = "services:\n"
            + "  keycloak:\n    image: x\n  keycloak-db:\n    image: y\n  ms-auth:\n    image: z\n  ms-eureka:\n    image: w\n";
        List<GeneratedFile> files = List.of(file("ms-platform/docker-compose.yml", compose));
        List<GeneratedFile> result = processor.process(files, ctx(req));
        String out = new String(result.get(0).content(), StandardCharsets.UTF_8);
        assertThat(out).contains("keycloak:");
        assertThat(out).contains("ms-auth:");
        assertThat(out).contains("ms-eureka:");
    }
```

**Step 2: Mettre à jour le test des routes gateway**
Dans `rewrites_gateway_routes_for_resources`, le YAML d'entrée contient une route `ms-auth`. Ajouter une
assertion qu'elle est conservée (elle ne doit plus jamais être retirée). Après les assertions existantes,
ajouter :
```java
        assertThat(out).contains("- id: ms-auth");
```

**Step 3: Vérifier le test `rewrites_root_pom_modules_from_features_and_resources`**
Aucun ancien flag n'y est utilisé (il vérifie seulement la présence de `common-lib` et
`service-consumer`). Ajouter une assertion que `ms-auth` est désormais toujours présent :
```java
        assertThat(out).contains("<module>ms-auth</module>");
```

**Step 4: Scanner et corriger tout autre usage d'ancien flag**
```bash
grep -nE 'setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin|setLoki|setGrafana\b' \
  src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java
```
Appliquer ces règles de conversion, déterministes, à CHAQUE occurrence :
- `setKeycloak(false)` / `setRedis(false)` / `setRabbitmq(false)` / `setWebsocket(false)` : le
  comportement de retrait associé n'existe plus. Convertir le test en assertion « toujours présent » sur
  le même artefact (cf. Step 1), ou supprimer le test s'il ne testait QUE ce retrait disparu.
- `setAdmin(false)` → `setSpringbootAdmin(false)` (comportement de retrait ms-admin conservé).
- `setGrafana(...)` / `setLoki(...)` → `req.getBatch().setGrafana(...)` (une seule bascule pilote
  loki+promtail+grafana).

**Step 5: Itérer jusqu'au build vert (le build est l'oracle)**
Lancer `mvn -q test`. Pour chaque erreur de compilation ou échec de test résiduel dans
`CrossCuttingConfigProcessorTest`, appliquer les règles de conversion du Step 4. Répéter jusqu'à
`BUILD SUCCESS`. Ne PAS modifier le code de production pour faire passer un test : si un test échoue, soit
sa valeur attendue reflète l'ancien comportement (le convertir/supprimer), soit il révèle un vrai bug
dans les Tasks 1.4–1.6 (auquel cas corriger le processor).

**Verification:**
- [ ] `grep -cE 'setKeycloak|setRedis|setRabbitmq|setWebsocket|setAdmin|setLoki|setGrafana\b' src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java` retourne `0`.
- [ ] `mvn -q test` → `BUILD SUCCESS`, `Tests run: <N>, Failures: 0, Errors: 0`.

### Task 2.3 : Ajouter un test de désérialisation Jackson tolérante
**Files:**
- `src/test/java/com/mr486/generator/dto/FeatureOptionsDeserializationTest.java` — créer.

**Step 1: Créer le test**
Écrire ce contenu exact :

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
        // An old command using removed flags must not break deserialization.
        String json = "{\"keycloak\":true,\"redis\":true,\"loki\":false,\"springbootAdmin\":true}";
        FeatureOptions f = mapper.readValue(json, FeatureOptions.class);
        assertThat(f.isSpringbootAdmin()).isTrue();
        assertThat(f.isClientWebUI()).isFalse(); // default
    }
}
```

**Verification:**
- [ ] `mvn -q -Dtest=FeatureOptionsDeserializationTest test` → `BUILD SUCCESS`, 2 tests passent.

---

## Phase 3 : Vérification end-to-end

### Task 3.1 : Build complet
**Files:** aucun (vérification).

**Step 1:**
```bash
mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'
```

**Verification:**
- [ ] `BUILD SUCCESS`.
- [ ] `Tests run: N, Failures: 0, Errors: 0` (N = ancien total − ~1 test supprimé + ~5 nouveaux + 2 Jackson).
- [ ] `TemplateLoaderTest` passe sans modification (parité 112 inchangée — aucun fichier template touché).

### Task 3.2 : Génération end-to-end avec la commande de référence
**Files:** aucun (vérification). Utiliser un port dédié pour éviter le piège du serveur zombie sur :8080.

**Step 1: Lancer le générateur en arrière-plan sur un port dédié**
```bash
pkill -9 -f 'springboot-platform-generator' 2>/dev/null; sleep 2
setsid java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 \
  > /tmp/genapp.log 2>&1 < /dev/null &
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
```

**Step 2: Générer avec la commande de référence (JSON valide, flags camelCase)**
```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform",
    "groupId": "com.acme",
    "basePackage": "com.acme.shop",
    "javaVersion": "17",
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

**Step 3: Inspecter le contenu généré**
```bash
echo "=== modules du pom racine ==="; grep -E '<module>' /tmp/refx/ms-platform/pom.xml
echo "=== blocs compose présents ==="; grep -E '^  [a-z].*:' /tmp/refx/ms-platform/docker-compose.yml
echo "=== observability présente (grafana=true) ==="; ls /tmp/refx/ms-platform/observability/ 2>&1
echo "=== ms-admin présent (springbootAdmin=true) ==="; ls /tmp/refx/ms-platform/ms-admin/pom.xml 2>&1
echo "=== ms-client : absent en Phase 1 même si clientWebUI=true (module pas encore créé) ==="; ls /tmp/refx/ms-platform/ms-client 2>&1
```

**Verification:**
- [ ] `HTTP=200`.
- [ ] Le pom racine contient `ms-auth`, `service-consumer`, `service-batch`, `ms-admin`,
  `order-service`, `product-service`, `inventory-service` ; il ne contient PAS `service-a/b/c`.
- [ ] `docker-compose.yml` contient les blocs `keycloak`, `ms-auth`, `redis`, `rabbitmq`, `loki`,
  `promtail`, `grafana`, `service-batch`, `ms-admin`.
- [ ] `observability/` existe et contient grafana/loki/promtail.
- [ ] `ms-admin/pom.xml` existe.
- [ ] `ms-client` n'existe pas (normal en Phase 1) — `ls` retourne « No such file or directory ».

### Task 3.3 : Valider le projet généré (compose + maven)
**Files:** aucun (vérification).

**Step 1: Valider docker-compose**
```bash
cd /tmp/refx/ms-platform && (cp dist.env .env 2>/dev/null || true) && docker compose config >/dev/null && echo "COMPOSE_OK"
```

**Step 2 (si Docker indisponible, sauter avec mention) : compiler le projet généré**
```bash
cd /tmp/refx/ms-platform && mvn -q -DskipTests package 2>&1 | tail -5
```

**Verification:**
- [ ] `docker compose config` → `COMPOSE_OK` (aucun service `depends_on` orphelin).
- [ ] `mvn -DskipTests package` du projet généré → `BUILD SUCCESS` (aucun module orphelin).
- [ ] Si Docker ou Maven indisponible dans l'environnement : le noter explicitement comme NON vérifié
  plutôt que d'affirmer le succès.

### Task 3.4 : Commit
**Files:** tous les fichiers modifiés des Phases 1–2.

**Step 1:**
```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add -A && git commit -m "feat(generator): redesign feature model — permanent core, batch.grafana drives observability

FeatureOptions reduced to springbootAdmin + clientWebUI; keycloak/redis/
rabbitmq/websocket are now always installed; BatchOptions.grafana drives the
full observability stack (loki+promtail+grafana). Processors and tests
realigned. No template files changed; ms-client/admin-application wiring
deferred to their own phases (no orphan references).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

**Verification:**
- [ ] `git status` → working tree clean.
- [ ] `git log --oneline -1` montre le commit.

---

## Recovery

Si l'exécution est interrompue :
- [ ] `git log --oneline -5` — voir les commits déjà passés.
- [ ] `grep -rnE 'isKeycloak|isRedis|isRabbitmq|isWebsocket|isAdmin|isLoki|isGrafana' src/main/java/`
  — si vide, les Tasks 1.3–1.6 sont faites.
- [ ] `mvn -q compile` — si SUCCESS, la Phase 1 est complète.
- [ ] `mvn -q test` — si SUCCESS, la Phase 2 est complète.
- [ ] Reprendre à la première Task dont la Verification échoue.

## Notes hors périmètre (ne pas traiter ici)
- Création des modules `ms-client` (Phase 2) et `admin-application` (Phase 3).
- `BatchConfigProcessor` ne touche pas grafana — aucun changement requis.
