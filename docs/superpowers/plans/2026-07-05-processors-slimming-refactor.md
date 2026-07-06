# Dégraissage des deux gros processors (God class) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. C'est un **refactor à comportement constant** : la règle d'or est « sortie générée identique au bit près, à chaque étape ».

**Goal:** Résorber les violations PMD structurelles (`GodClass`, `TooManyMethods`, `CyclomaticComplexity`/`NPathComplexity`/`CognitiveComplexity`) sur `CrossCuttingConfigProcessor` (~1059 lignes, ~25 méthodes, 8 responsabilités « réécrire un fichier transverse ») et `ResourceExpandProcessor` (~443 lignes, `switch` sur enums de base/id), **sans changer un seul octet de la plateforme générée**. Objectif chiffré : 0 violation PMD structurelle sur ces deux fichiers, couverture ≥ 80 % maintenue, 143 tests toujours verts.

**Architecture cible:**
- **`CrossCuttingConfigProcessor` → dispatcher mince.** On extrait une interface `CrossCuttingRewriter` (`boolean handles(path, ctx)` + `GeneratedFile rewrite(f, ctx)`) et un bean par fichier transverse (`RootPomRewriter`, `ComposeRewriter`, `GatewayRewriter`, `RealmRewriter`, `ReadmeRewriter`, `TestAllRewriter`, `AggregateRewriter`, `WebUiCatalogRewriter`). `process()` devient : pour chaque fichier, trouver le rewriter qui le prend en charge, l'appliquer. **O de SOLID** : ajouter un fichier transverse = un nouveau bean, dispatcher inchangé. Le God class se dissout, chaque rewriter est petit et testable isolément.
- **`ResourceNaming` (value object partagé).** Les noms dérivés par ressource (`snake`, `scream`/`upper`, `entityLower`, `roleName`, `tokenVar`, `testUser`, `gatewayUrl`, `routePath`, `servicePackage`…) sont calculés **une fois** dans un `record` immuable, consommé par les rewriters ET par `ResourceExpandProcessor`. Supprime les recalculs `replace/toUpperCase/toLowerCase` éparpillés et les helpers de nommage du God class.
- **`removeYamlBlock(...)` (utilitaire de lignes partagé).** `removeGatewayRoute`, `removeServiceBlock`, `removeVolumeEntry` partagent la même forme « scanner les lignes, repérer un en-tête, retirer le bloc selon l'indentation ». On factorise en **un** balayeur paramétré (`ProcessorUtils` ou `YamlBlocks`) → dissout les `NPath`/`Cyclomatic` de ces méthodes.
- **`ResourceExpandProcessor` → stratégies par enum.** `applyH2`/`applyMongo`/(postgres implicite) et `applyIntegerType`/`applyUuidType`/`switch(idType)` deviennent des stratégies (`DbVariant` par `DatabaseType`, `IdVariant` par `IdType`) injectées/mappées. **O de SOLID** : le `switch` sur enum → polymorphisme (conventions §3, O). `applyMongo` (NPath 432 !) est décomposé en étapes nommées (rename package, id→String, imports, application.yml) dans sa propre stratégie.

**Tech Stack:** Java 17, Spring Boot (beans `@Component` + `@RequiredArgsConstructor`, **aucun `@Autowired`**), Lombok (`record`/`@RequiredArgsConstructor`), JUnit 5 + AssertJ + Mockito. PMD/Checkstyle/JaCoCo/SpotBugs déjà câblés (`mvn verify`).

---

## Rappels d'environnement (pièges)

- **Refactor pur, pas de fonctionnalité.** Le RED classique du TDD n'existe pas ; le **golden-master (Task 0)** joue le rôle d'oracle de non-régression. Toute étape qui fait bouger un octet de sortie = régression, pas « amélioration ».
- **Aucun template touché.** Ce chantier ne modifie que `src/main/java/com/mr486/generator/pipeline/processor/**` (+ éventuellement `model/`). Donc **pas** de bump `TemplateLoaderTest.hasSize`, **pas** de compilation d'un module généré : `mvn test` valide intégralement. Ne PAS toucher `src/main/resources/templates/**`.
- **Dual-source `test-all.sh` (piège mémoire `cross_cutting_config_pattern`).** Le fichier existe en deux versions : le template statique (utilisé quand `resources[]` est vide) ET `rewriteTestAll()` (utilisé dès que `resources[]` est non vide). `TestAllRewriter` ne concerne QUE la seconde. Le golden-master doit couvrir **les deux** cas (payload vide ET non vide).
- **Substitutions db par fichier (piège mémoire `resource_expand_db_variants`).** Mongo (package `document`, id `String`), UUID (imports), application.yml Mongo (placeholders simple-accolade) doivent rester propagés à **chaque** fichier concerné. Le golden Mongo/UUID/H2 le verrouille.
- **Ordre du pipeline inchangé.** `@Order(50)` ResourceExpand puis `@Order(60)` CrossCutting : CrossCutting voit la liste finale des ressources. Les nouveaux beans rewriter sont des collaborateurs **internes** au processor `@Order(60)`, pas de nouveaux `FileProcessor` du pipeline — ne pas déranger l'ordre global.
- **Style maison :** ≤ 120 colonnes, 4 espaces, un import/ligne, Javadoc FR (exemple avant les `@`), commentaire `//` sur les privées, logs SLF4J si ajout de log. Limite dure 10 méthodes/classe (hors getters).
- **Vérif à chaque étape :** `mvn clean verify` → 143 tests verts + golden vert + Checkstyle 0 ; lire `target/pmd.xml` pour constater la baisse.

---

## File Structure

Sous `src/main/java/com/mr486/generator/` :
- Create `pipeline/processor/crosscut/CrossCuttingRewriter.java` — interface (`handles` + `rewrite`).
- Create `pipeline/processor/crosscut/{RootPom,Compose,Gateway,Realm,Readme,TestAll,Aggregate,WebUiCatalog}Rewriter.java` — un bean par fichier transverse.
- Create `model/ResourceNaming.java` — `record` des noms dérivés + fabrique `from(ResourceModuleRequest)`.
- Create `pipeline/processor/YamlBlocks.java` (ou étendre `ProcessorUtils`) — `removeYamlBlock(...)` paramétré.
- Create `pipeline/processor/expand/{DbVariant,PostgresVariant,H2Variant,MongoVariant,IdVariant,LongIdVariant,IntegerIdVariant,UuidIdVariant}.java` — stratégies.
- Modify `pipeline/processor/CrossCuttingConfigProcessor.java` — réduit à l'orchestration (dispatch sur les rewriters).
- Modify `pipeline/processor/ResourceExpandProcessor.java` — réduit à l'orchestration (délègue aux stratégies).
- Test (nouveaux) : `GeneratorGoldenMasterTest.java` + un `*RewriterTest` par rewriter + `*VariantTest` par stratégie + `ResourceNamingTest`.
- Les gros tests existants (`CrossCuttingConfigProcessorTest` 34 méthodes, `ResourceExpandProcessorTest` 28) sont **scindés** au fil des extractions (résout aussi le `TooManyMethods` côté tests).

---

## Task 0 : Filet de sécurité — golden-master (À FAIRE EN PREMIER)

**Files:** Create `src/test/java/com/mr486/generator/GeneratorGoldenMasterTest.java`

Oracle de non-régression : on fige la sortie complète du service pour une matrice de requêtes représentatives, AVANT toute modification de production. Chaque tâche suivante doit garder ce test **vert au bit près**.

- [x] **Step 1 — Écrire le test golden.** `GeneratorGoldenMasterTest` (`@SpringBootTest`, `PlatformGeneratorService.generate`). Empreinte = `path  sha256(content)  executable` triée par path, comparée à `src/test/resources/golden/<cas>.txt` ; `-Dgolden.write=true` (ré)écrit au lieu de comparer.
- [x] **Step 2 — Matrice de couverture** (chaque cas = un golden) :
  - `default` — body `{}` (service-a/b/c, test-all.sh **statique**).
  - `one-postgres` — 1 ressource POSTGRES/LONG (test-all.sh **régénéré**).
  - `one-mongo` — 1 ressource MONGO (package document, id String, application.yml).
  - `one-h2` — 1 ressource H2 (pas de bloc db ni volume).
  - `one-uuid` — 1 ressource POSTGRES/UUID (imports UUID).
  - `full` — 3 ressources + `springbootAdmin:true` + `webUI:true` + `batch.grafana:true`.
  - `batch-off` — `batch.enabled:false` (service-batch exclu).
- [x] **Step 3 — Générer les goldens** (mode write), comparaison verte **deux fois** (déterministe), sensibilité vérifiée (mongo ≠ postgres : 21 lignes ; `test-all.sh` statique ≠ régénéré). Suite complète verte (143 → **150** tests), Checkstyle 0. **Commit à faire** : `test(generator): fige la sortie via golden-master (7 cas) avant refactor`.

> À partir d'ici, aucune tâche ne modifie un golden. Si un golden change, l'extraction a altéré le comportement → corriger l'extraction, pas le golden.

---

## Task 1 : `ResourceNaming` — value object des noms dérivés

**Files:** Create `model/ResourceNaming.java` + `ResourceNamingTest.java` ; Modify les deux processors.

- [x] **Step 1 (test d'abord).** `ResourceNamingTest` (3 tests) ré-encode les formules historiques (order-service/Order → order_service/ORDER_SERVICE/orderservice/USER_ORDER_SERVICE…, gatewayUrl/routePath défaut + préfixe explicite). Vu au RED (3 NPE, `from` renvoyait null).
- [x] **Step 2.** `record ResourceNaming(serviceName, className, routePrefix)` en package `pipeline.processor` (accès à `ProcessorUtils.toPascalCase` package-private) ; dérivés en méthodes, `Locale.ROOT`. Vert (3/3).
- [x] **Step 3.** CrossCutting : les 5 helpers délèguent à `ResourceNaming` (call sites intacts, suppression en Task 3). Expand : `transformPath`/`applyBaseReplacements`/`applyMongo` consomment `ResourceNaming` ; `toConcatLower` (mort) supprimé. `mvn verify` : **golden 7/7 vert** (sortie identique), 153 tests, Checkstyle 0, `ResourceNaming` 0 violation PMD. **Commit à faire** : `refactor(processor): extrait ResourceNaming (noms dérivés par ressource)`.

---

## Task 2 : `removeYamlBlock` — utilitaire de retrait de bloc par lignes

**Files:** Create `pipeline/processor/YamlBlocks.java` + test ; Modify `CrossCuttingConfigProcessor`.

- [x] **Step 1 (test d'abord).** `YamlBlocksTest` (5 cas : service compose, route passerelle, absence, fin de texte, ligne vide interne). `removeVolumeEntry` **écarté** — c'est un regex une-ligne, pas un scanner de bloc. Vu au RED (4/5). Piège relevé : le comportement historique laisse un `\n` final quand le bloc est en fin de texte → attente de test corrigée pour rester fidèle (pas l'implémentation).
- [x] **Step 2.** `YamlBlocks.removeBlock(text, isStart, isBoundary)` (deux `Predicate<String>`), décomposé en `indexOfStart`/`indexOfBoundary`/`rejoinWithout` pour ne PAS déplacer la complexité dans une méthode unique flaggée. Vert (5/5), `YamlBlocks` 0 violation PMD.
- [x] **Step 3.** `removeGatewayRoute`/`removeServiceBlock` délèguent (prédicats à l'identique). `mvn verify` : **golden 7/7 vert**, 158 tests, Checkstyle 0. **PMD 71 → 65** (les 6 `Cognitive/Cyclomatic/NPath` des 2 méthodes éliminées, rien ajouté). **Commit à faire** : `refactor(processor): factorise le retrait de bloc YAML par lignes (YamlBlocks)`.

---

## Task 3 : `CrossCuttingConfigProcessor` → dispatcher + rewriters

**Files:** Create `crosscut/CrossCuttingRewriter.java` + les 8 rewriters + leurs tests ; Modify `CrossCuttingConfigProcessor`.

> **Écart au plan (assumé) :** les rewriters vivent dans le package `pipeline.processor` (et non un sous-package `crosscut/`) car ils consomment `ProcessorUtils`/`YamlBlocks`/`ResourceNaming`, package-private — les mettre ailleurs aurait forcé à élargir leur visibilité. Encapsulation préservée. Pattern **strangler** : dispatcher + `legacyRewrite` de repli, résorbé au fil des extractions ; le test unitaire construit le processor avec ses rewriters réels (aucune migration des 34 tests).

- [x] **Step 1.** Interface `CrossCuttingRewriter` (`handles`+`rewrite`+`static hasResources`). Dispatcher + repli en place (Step 3a), golden + 34 tests verts avec liste vide.
- [x] **Step 2.** Rewriters extraits **un par un** (golden 7/7 vert à chaque étape) :
  1. `RootPomRewriter` (rewriteRootPom + desiredModules).
  2. `WebUiCatalogRewriter` (rewriteWebUiCatalog).
  3. `AggregateRewriter` (rewriteAggregate + firstPackage).
  4. `ReadmeRewriter` (rewriteReadme).
  5. `GatewayRewriter` (rewriteGatewayYml + add/removeGatewayRoute, via `removeYamlBlock`).
  6. `ComposeRewriter` (rewriteCompose + blocks/volumes/dependsOn/addResourceBlocks…).
  7. `RealmRewriter` (rewriteRealm + buildRealmUser ; traite le `REC_CATCH_EXCEPTION` en resserrant le catch sur `JsonProcessingException`/`IOException`).
  8. `TestAllRewriter` (rewriteTestAll + assertHttp) — **le plus gros** ; assemblé mécaniquement (corps verbatim + 4 délégateurs de nommage) pour zéro risque de transcription sur ~230 lignes.
  Les 34 tests restent dans `CrossCuttingConfigProcessorTest` (construit avec les 8 rewriters réels) — pas de migration ; le `TooManyMethods` du test reste (bruit toléré).
- [x] **Step 3.** `CrossCuttingConfigProcessor` réduit à **58 lignes** (dispatcher pur : `@RequiredArgsConstructor` sur `List<CrossCuttingRewriter>` + `dispatch`). `mvn verify` : **golden 7/7 vert**, 158 tests, Checkstyle 0. **`GodClass`/`TooManyMethods` disparus** (CrossCuttingConfigProcessor : 0 violation PMD). PMD 65 → 56, SpotBugs 2 (injection XML re-suppressée sur RootPomRewriter). Suppression Checkstyle Indentation étendue aux `*Rewriter.java` (gabarits), retirée de CrossCuttingConfigProcessor (dispatcher sans gabarit).

> **`TestAllRewriter`** : émetteur de script linéaire → complexité neutralisée par `@SuppressWarnings` **justifié** (Ncss/Cognitive/Cyclomatic/NPath + TestClassWithoutTestCases faux positif + CompareObjectsWithEquals identité volontaire), plutôt que fragmentation artificielle. Cible du chantier #6 (externalisation des gabarits). **`RealmRewriter`** garde 5 violations de complexité (boucles imbriquées) — à traiter séparément si souhaité.

---

## Task 4 : `ResourceExpandProcessor` → stratégies db/id

**Files:** Create `expand/{DbVariant,PostgresVariant,H2Variant,MongoVariant,IdVariant,LongIdVariant,IntegerIdVariant,UuidIdVariant}.java` + tests ; Modify `ResourceExpandProcessor`.

> **Écart au plan (assumé) :** golden-master + les 28 tests existants de `ResourceExpandProcessorTest` (H2/Mongo/UUID/Integer via `process()`) servent d'oracle — pas de `*VariantTest` unitaires séparés (refactor à comportement constant, couverture préservée). Dispatch par recherche linéaire sur `type()` (N≤3) au lieu d'`EnumMap` — évite un constructeur manuel, `@RequiredArgsConstructor` suffit.

- [x] **Step 1-2.** `DbVariant` (Postgres no-op, H2, Mongo) + `IdVariant` (Integer, UUID), beans `@Component` avec discriminant `type()`. `applyMongo` **décomposé** (`apply` → gardes + `applyJava`/`applicationYml`/`pom`/`entity`/`repository`/`otherJava`) : NPath effondré. `null` sentinelle (changelog) conservée avec `@SuppressWarnings` justifié (byte[], pas une collection).
- [x] **Step 3.** `ResourceExpandProcessor` : orchestration seule, injecte `List<DbVariant>`/`List<IdVariant>` et dispatche par `type()` ; `transformContent`+`applyBaseReplacements` fusionnés (≤10 méthodes) ; 5 `AvoidReassigningParameters` corrigés par chaînage sur locale. `mvn verify` : **golden 7/7 vert**, 158 tests, Checkstyle 0. **`GodClass`/`TooManyMethods`/`NPath applyMongo` disparus** (ResourceExpandProcessor + variants : 0 violation PMD). PMD 56 → 46. Suppression Checkstyle Indentation étendue aux `*Variant.java` (gabarits DB). **Commit à faire** : `refactor(expand): stratégies DbVariant/IdVariant (switch enum → polymorphisme)`.

---

## Task 5 : Vérification finale & clôture

- [x] `mvn clean verify` : **BUILD SUCCESS**, 158 tests, golden 7/7 vert, **Checkstyle 0**, **JaCoCo 96.6 % instr / 82.2 % branches** (≥ 80 %).
- [x] Lu `target/pmd.xml` : familles `GodClass`/`TooManyMethods`/`Cyclomatic`/`NPath`/`Cognitive` sur les deux processors = **0**. PMD total 71 → 45. Résidus assumés : `RealmRewriter` (boucles imbriquées) et `TestAllRewriter` (`@SuppressWarnings` justifié).
- [x] Lu `target/spotbugsXml.xml` : `REC_CATCH_EXCEPTION` de `RealmRewriter` **résolu** (catch resserré sur `IOException`, commit `22416e4`) ; reste `CT_CONSTRUCTOR_THROW` sur `TemplateLoader` (préexistant, hors périmètre).
- [x] Mémoire : `cross_cutting_config_pattern` (dispatcher + 8 rewriters, dual-source `test-all.sh` → `TestAllRewriter`, extension → beans) et `resource_expand_db_variants` (stratégies `DbVariant`/`IdVariant`) complétées ; nouvelle entrée `processors_slimming_refactor_2026_07_06` + index `MEMORY.md`. Miroir `docs/claude-memory/` fait.
- [x] **Commit final** : `docs(memory): acte le passage des processors en dispatcher + stratégies`.

---

## Ordre de bataille & estimation

Task 0 (golden) est le prérequis absolu. Task 1 et 2 sont indépendantes et rapides (fondations partagées). Task 3 est le gros morceau (8 extractions séquentielles, chacune golden-verrouillée). Task 4 est moyen. Chaque sous-étape est un commit relisable dans l'IDE. Aucune étape ne doit rester rouge : un golden cassé = on annule l'étape, pas on ajuste le golden.
