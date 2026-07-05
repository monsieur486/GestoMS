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

- [ ] **Step 1 (test d'abord).** `ResourceNamingTest` : `ResourceNaming.from(res)` expose `serviceName`, `snake`, `scream`, `entityLower`, `entityPlural`, `serviceClass`, `servicePackage`, `roleName`, `tokenVar`, `testUser`, `gatewayUrl`, `routePath`. Cas : `order-service`/`Order` → valeurs attendues explicites (ré-encoder les helpers actuels lignes 98-119 de CrossCutting et 143-147 de Expand). Voir échouer.
- [ ] **Step 2.** Implémenter le `record` (fabrique statique `from`, `Locale.ROOT`). Vert.
- [ ] **Step 3.** Remplacer les helpers de nommage de `CrossCuttingConfigProcessor` (roleName/tokenVar/testUser/gatewayUrl/routePath) et les recalculs de `applyBaseReplacements` par `ResourceNaming`. `mvn verify` : golden + 143 tests verts. **Commit** : `refactor(processor): extrait ResourceNaming (noms dérivés par ressource)`.

---

## Task 2 : `removeYamlBlock` — utilitaire de retrait de bloc par lignes

**Files:** Create `pipeline/processor/YamlBlocks.java` + test ; Modify `CrossCuttingConfigProcessor`.

- [ ] **Step 1 (test d'abord).** `YamlBlocksTest` : `removeYamlBlock(text, headerPredicate)` retire un bloc (en-tête + lignes plus indentées) et rien d'autre. Cas repris des comportements actuels de `removeServiceBlock` (460), `removeVolumeEntry` (235), `removeGatewayRoute` (392). Voir échouer.
- [ ] **Step 2.** Implémenter le balayeur unique paramétré (indentation-aware). Vert.
- [ ] **Step 3.** Réécrire les 3 méthodes pour déléguer à `removeYamlBlock`. `mvn verify` vert (golden inclus). Constater dans `target/pmd.xml` la chute des `NPath`/`Cyclomatic` sur removeGatewayRoute/removeServiceBlock. **Commit** : `refactor(processor): factorise le retrait de bloc YAML par lignes`.

---

## Task 3 : `CrossCuttingConfigProcessor` → dispatcher + rewriters

**Files:** Create `crosscut/CrossCuttingRewriter.java` + les 8 rewriters + leurs tests ; Modify `CrossCuttingConfigProcessor`.

- [ ] **Step 1 (test d'abord).** Définir l'interface `CrossCuttingRewriter` et écrire un test de dispatch (mock de 2 rewriters : seul celui dont `handles` est vrai reçoit `rewrite`). Voir échouer.
- [ ] **Step 2.** Extraire les rewriters **un par un** (chacun = un sous-commit, golden vert à chaque fois), dans cet ordre de risque croissant :
  1. `RootPomRewriter` (rewriteRootPom + desiredModules).
  2. `WebUiCatalogRewriter` (rewriteWebUiCatalog).
  3. `AggregateRewriter` (rewriteAggregate + firstPackage).
  4. `ReadmeRewriter` (rewriteReadme).
  5. `GatewayRewriter` (rewriteGatewayYml + add/removeGatewayRoute, via `removeYamlBlock`).
  6. `ComposeRewriter` (rewriteCompose + blocks/volumes/dependsOn/addResourceBlocks…).
  7. `RealmRewriter` (rewriteRealm + buildRealmUser ; traite le `REC_CATCH_EXCEPTION` en resserrant le catch sur `JsonProcessingException`/`IOException`).
  8. `TestAllRewriter` (rewriteTestAll + assertHttp) — **le plus gros** ; le décomposer en sous-builders privés (`buildTokenBootstrap`, `buildPerServiceAsserts`, `buildAggregateAsserts`) pour passer sous les seuils.
  À chaque extraction : déplacer les tests correspondants de `CrossCuttingConfigProcessorTest` vers un `*RewriterTest` dédié (résout le `TooManyMethods` du test).
- [ ] **Step 3.** `CrossCuttingConfigProcessor` ne garde que : `@RequiredArgsConstructor` sur `List<CrossCuttingRewriter>`, `process()` qui dispatch, `hasResources`. `mvn verify` : golden + 143 verts, `GodClass`/`TooManyMethods` disparus sur le processor. **Commit(s)** : un `refactor(crosscut): extrait XxxRewriter` par rewriter + `refactor(crosscut): réduit CrossCuttingConfigProcessor à un dispatcher`.

> **Risque résiduel `TestAllRewriter`** : si après décomposition la complexité reste > seuil (émetteur de script intrinsèquement linéaire), poser un `@SuppressWarnings("PMD.CyclomaticComplexity")` **justifié par un commentaire**, plutôt que de fragmenter artificiellement. À décider sur les chiffres réels.

---

## Task 4 : `ResourceExpandProcessor` → stratégies db/id

**Files:** Create `expand/{DbVariant,PostgresVariant,H2Variant,MongoVariant,IdVariant,LongIdVariant,IntegerIdVariant,UuidIdVariant}.java` + tests ; Modify `ResourceExpandProcessor`.

- [ ] **Step 1 (test d'abord).** `MongoVariantTest`, `H2Variant Test`, `UuidIdVariantTest`… : chaque stratégie applique ses substitutions à un contenu d'exemple (repris de applyMongo/applyH2/applyUuidType actuels). Voir échouer.
- [ ] **Step 2.** Implémenter `DbVariant` (interface `byte[] apply(path, content, res, naming)`) avec Postgres (no-op ciblé), H2, Mongo ; `applyMongo` décomposé en étapes nommées dans `MongoVariant`. Idem `IdVariant` pour le `switch(idType)`. Mapper par enum (`Map<DatabaseType, DbVariant>`, `Map<IdType, IdVariant>`), injecté par constructeur.
- [ ] **Step 3.** `ResourceExpandProcessor` : orchestration seule (extract template, remove defaults, clone, path transform, délègue db+id aux stratégies). `mvn verify` : golden + 143 verts, `GodClass`/`TooManyMethods`/`NPath applyMongo` disparus. Scinder `ResourceExpandProcessorTest` par stratégie. **Commit(s)** : `refactor(expand): stratégies DbVariant/IdVariant (switch enum → polymorphisme)`.

---

## Task 5 : Vérification finale & clôture

- [ ] `mvn clean verify site` : BUILD SUCCESS, 143+ tests, golden vert, **Checkstyle 0**, **JaCoCo ≥ 80 %**.
- [ ] Lire `target/pmd.xml` : les familles `GodClass`/`TooManyMethods`/`Cyclomatic`/`NPath`/`Cognitive` sur les deux processors doivent être à **0** (résidu éventuel : `TestAllRewriter` sous `@SuppressWarnings` justifié, à documenter).
- [ ] Lire `target/spotbugsXml.xml` : pas de nouvelle anomalie ; `REC_CATCH_EXCEPTION` de rewriteRealm résolu si le catch a été resserré.
- [ ] Mémoire : compléter `docs/claude-memory/cross_cutting_config_pattern.md` (le dispatcher + les rewriters remplacent le God class ; la note dual-source `test-all.sh` pointe désormais `TestAllRewriter`) et créer une entrée `resource_expand_db_variants` mise à jour (stratégies). Miroir + commit selon `sync_memory_to_repo`.
- [ ] **Commit final** : `docs(memory): acte le passage des processors en dispatcher + stratégies`.

---

## Ordre de bataille & estimation

Task 0 (golden) est le prérequis absolu. Task 1 et 2 sont indépendantes et rapides (fondations partagées). Task 3 est le gros morceau (8 extractions séquentielles, chacune golden-verrouillée). Task 4 est moyen. Chaque sous-étape est un commit relisable dans l'IDE. Aucune étape ne doit rester rouge : un golden cassé = on annule l'étape, pas on ajuste le golden.
