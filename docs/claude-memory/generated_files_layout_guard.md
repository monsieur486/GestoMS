---
name: generated-files-layout-guard
description: "generated Java + generator's own source must stay ≤120/4-space/one-import-per-line + French javadoc, enforced by GeneratedOutputLayoutTest (real templates) and GeneratorSourceLayoutTest (code-only width); why unit fixtures can't catch reformat breakage; poms stay compact"
metadata: 
  node_type: memory
  type: project
  originSessionId: 62225419-5f5d-4d01-b4d2-1630802477f5
---

The template `.java` under `src/main/resources/templates/ms-platform/` (and the Java emitted as strings by processors) follow a convention: 4-space indent, ≤120 chars/line, one import per line, French javadoc on class + public methods (no Lombok-accessor / per-`@Test` javadoc). This is enforced by `GeneratedOutputLayoutTest` (a `@SpringBootTest` that generates a real `resources[]` platform and asserts ≤120 + no `;import ` on every generated `.java`, plus UUID/Mongo variant transforms still apply).

The generator's OWN source (`src/main/java` + `src/test/java`) follows the same convention, enforced by a SECOND guard `GeneratorSourceLayoutTest`. It measures **code-only width** — string/char literals, comments and text blocks are neutralized before counting — so Unicode `// ── … ──` banner comments (>120 bytes, ≤80 display) and unbreakable string literals (Mongo templates, shell/yaml/json builder fragments, test fixtures) are exempt, while a genuinely too-wide code line or a real glued import fails. (`;import ` is checked on the code-only text too, so occurrences inside string literals/assertions don't false-positive.)

**Why:** generated output came from two sources (static templates copied+substituted, and Java emitted as strings by `ResourceExpandProcessor` / `CrossCuttingConfigProcessor`); both were minified and undocumented. Established 2026-06-01; generator-source guard added the same day.

**How to apply:**
- When editing a template `.java`, keep lines ≤120 and imports one-per-line or `GeneratedOutputLayoutTest` fails. Run `find src/main/resources/templates/ms-platform -name '*.java' -exec awk '{if(length>120)print FILENAME":"NR}' {} +` and `grep -rn ';import '` to self-check.
- **Coupling trap:** `ResourceExpandProcessor.applyUuidType`/`applyIntegerType` do `text.replace(...)` on EXACT whitespace of the service-a entity/repo (e.g. `"@Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id"`, `"JpaRepository<X, Long>"`). Reformatting service-a templates **silently breaks** UUID/Integer/Mongo generation unless these constants are updated in lockstep. `ResourceExpandProcessorTest` uses **inline-minified fixtures, not the real templates**, so it stays green while real generation breaks — the real-template guard is the only safety net. See [[resource_expand_db_variants]].
- **poms stay compact** (one `<dependency>` per line, children inline): `ResourceExpandProcessor` (data-jpa→mongo, postgres→h2 swaps), `CrossCuttingConfigProcessor` (`<modules>` block) and `VersionInjectionProcessor` (parent/admin versions) all do exact-string replacement on those lines — never expand poms to multi-line XML. See [[cross_cutting_config_pattern]].
- `test-all.sh` / `realm.json` are regenerated per-resource by CrossCutting; the guard is Java-only so it doesn't police them.
