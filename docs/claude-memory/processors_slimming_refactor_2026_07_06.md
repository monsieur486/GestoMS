---
name: processors-slimming-refactor-2026-07-06
description: "Le chantier qui a dissous les deux God class (CrossCuttingConfigProcessor + ResourceExpandProcessor) en dispatcher+rewriters et stratégies DbVariant/IdVariant, à sortie générée constante (golden-master)"
metadata: 
  node_type: memory
  type: project
  originSessionId: a9ab7f2d-5d05-435f-b9a2-98cb101ac2da
---

Refactor à comportement constant (2026-07-05 → 07-06) qui a résorbé les violations PMD structurelles (`GodClass`/`TooManyMethods`/`Cyclomatic`/`NPath`/`Cognitive`) sur les deux plus gros processors, **sans changer un octet de la plateforme générée**. Plan : `docs/superpowers/plans/2026-07-05-processors-slimming-refactor.md`.

**Oracle de non-régression = golden-master, pas TDD RED.** `GeneratorGoldenMasterTest` (`@SpringBootTest`) fige la sortie complète de `PlatformGeneratorService.generate` en empreinte `path  sha256  executable` triée, comparée à `src/test/resources/golden/<cas>.txt` ; `-Dgolden.write=true` (ré)écrit. 7 cas : `default` (body `{}`, test-all.sh statique), `one-postgres`/`one-mongo`/`one-h2`/`one-uuid`, `full` (3 ressources + springbootAdmin + webUI + grafana), `batch-off`. Un golden qui bouge = l'extraction a altéré le comportement → corriger l'extraction, jamais le golden.

**Fondations partagées.** `ResourceNaming` (record des noms dérivés par ressource : snake/scream/entityLower/roleName/tokenVar/testUser/gatewayUrl/routePath…, `Locale.ROOT`, fabrique `from(ResourceModuleRequest)`) consommé par les rewriters ET `ResourceExpandProcessor`. `YamlBlocks.removeBlock(text, isStart, isBoundary)` (deux `Predicate<String>`) factorise le retrait de bloc YAML par lignes.

**Résultats.** `CrossCuttingConfigProcessor` → dispatcher ~58 lignes sur `List<CrossCuttingRewriter>` (8 rewriters, voir [[cross-cutting-config-pattern]]). `ResourceExpandProcessor` → orchestration sur `List<DbVariant>`/`List<IdVariant>` (voir [[resource-expand-db-variants]]). PMD 71 → 41, SpotBugs `REC_CATCH_EXCEPTION` résolu, JaCoCo 96.6 % instr / 82.2 % branches, 143 → 158 tests, Checkstyle 0. Les deux résidus de rewriters ont ensuite été soldés (voir [[cross-cutting-config-pattern]]) : `RealmRewriter.rewrite` éclaté en `rewriteRealmRoles`/`rewriteUsers`/`repointAdminRoles` (0 PMD), et `TestAllRewriter` a externalisé ses gabarits bash en `fragments/test-all/*.sh` (chargés par `fragment()`, tokens `{{...}}`) avec `rewrite()` réduit à un orchestrateur d'appenders — les 4 `@SuppressWarnings` de complexité retirés, restent 2 légitimes (`TestClassWithoutTestCases`, `CompareObjectsWithEquals`). Enfin `TemplateLoader` déclaré `final` clôt le dernier finding SpotBugs (`CT_CONSTRUCTOR_THROW`, attaque par finalizer sur constructeur qui peut lever) → **suite SpotBugs à 0**.

**Commits :** `1cbb9ed` (golden-master) → `f45345e` (plan) → `7237e04` style → `836a365` ResourceNaming → `96aa4dd` YamlBlocks → `5177ab4` dispatcher+8 rewriters → `d947761` DbVariant/IdVariant → `22416e4` catch IOException → `752568e` RealmRewriter décomposé → `f9f11ff` gabarits TestAllRewriter externalisés → `f65cbaf` TemplateLoader `final`.

Prolonge [[simplification-refactor-2026-05-31]].
