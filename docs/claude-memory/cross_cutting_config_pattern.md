---
name: cross-cutting-config-pattern
description: "Non-obvious design and gotchas around CrossCuttingConfigProcessor — the processor that rewrites root pom.xml and docker-compose.yml based on features + resources[]"
metadata: 
  node_type: memory
  type: project
  originSessionId: 6d7e15f7-0812-4957-97ca-e94b5a021f4a
---

The GestoMS generator pipeline splits work into path-based processors (filter paths, rename roots) and content-based processors (replace strings inside files). A structural gap exists: files like the root `pom.xml` and `docker-compose.yml` reference *other* subsystems by name. When you remove a service's path with `FeatureFilterProcessor`, or swap default services for `resources[]` in `ResourceExpandProcessor`, those cross-cutting files keep dangling references and break `mvn package` / `docker compose up`. `CrossCuttingConfigProcessor` (`@Order(60)`) closes that gap.

**Why @Order(60) and not earlier.**
Must run AFTER `ResourceExpandProcessor` (@Order(50)) because it needs the final resource list to know which modules to add. Running earlier would miss dynamic services.

**Architecture (2026-07-06 refactor): the God class was dissolved into a dispatcher + one rewriter bean per cross-cutting file.**
`CrossCuttingConfigProcessor` is now a ~58-line dispatcher (`@RequiredArgsConstructor` over `List<CrossCuttingRewriter>`): for each file it finds the rewriter whose `handles(f, ctx)` returns true and applies `rewrite(f, ctx)`. Adding a cross-cutting file = one new `@Component` implementing `CrossCuttingRewriter` (`handles` + `rewrite` + `static hasResources`), dispatcher untouched (SOLID open/closed). The eight beans live in package `pipeline.processor` (NOT a `crosscut/` subpackage) so they can consume the package-private `ProcessorUtils`/`YamlBlocks`/`ResourceNaming`. The responsibilities below map to: `RootPomRewriter` (§1), `ComposeRewriter` (§2+§3), `GatewayRewriter` (§4), `RealmRewriter` / `TestAllRewriter` / `AggregateRewriter` (§5). This killed the `GodClass`/`TooManyMethods` PMD violations on the processor. The gotchas below are unchanged — they just moved into the corresponding rewriter. `RealmRewriter` catches `IOException` (not `Exception`) — Jackson only throws that; narrowing cleared SpotBugs `REC_CATCH_EXCEPTION` + PMD `AvoidCatchingGenericException`. Residual PMD on `RealmRewriter` (nested-loop Cognitive/Cyclomatic/NPath) and `TestAllRewriter` (linear script emitter, `@SuppressWarnings`-justified) are accepted, not target of the chantier. Shared helpers extracted the same day: `ResourceNaming` (record of per-resource derived names, consumed by rewriters AND `ResourceExpandProcessor`) and `YamlBlocks.removeBlock(text, isStart, isBoundary)` (line-based block remover backing `removeGatewayRoute`/`removeServiceBlock`).

**The rewriters own several responsibilities.**
1. Rewrite root `<modules>` block — regenerated from scratch based on features + resources[], not patched.
2. Remove obsolete docker-compose service blocks (e.g., `keycloak:` and `ms-auth:` when `keycloak=false`, `service-a/b/c:` and their `-db:` companions when resources[] is provided). Also clean dangling `depends_on: [..., removed-name, ...]` entries in surviving blocks — leaving them in causes `docker compose up` to fail with "service X depends on undefined service Y".
3. Append new docker-compose blocks for each `resources[]` entry, templated by `databaseType` (POSTGRES → app + postgres db + healthcheck, MONGO → app + mongo db, H2 → app only).
4. Rewrite `ms-gateway/src/main/resources/application.yml` route list. Without this, `keycloak=false` leaves a dangling `- id: ms-auth` route pointing at a removed service, and `resources[]` leaves `/service-a/**` routes that don't resolve while the new `/{resource}/**` services aren't reachable at all.
5. (added later) When `resources[]` is present, regenerate three more cross-cutting files that reference services by name, matched by **path suffix** (not exact path, because their location embeds the basePackage) and gated on `hasResources`:
   - **Keycloak realm** `keycloak/import/ms-realm-realm.json` — drop the demo `USER_SERVICE_A/B/C` roles and `test-service-a/b/c` users, emit one `USER_<SERVICE>` role + one `test-<service>` user (password `user123`) per resource, and re-point `test-admin` to all resource roles. Edited as a Jackson tree (the only JSON the pipeline manipulates structurally rather than by string ops — variable-arity arrays make string editing too fragile). The realm being correct is necessary but not sufficient at runtime — see [[keycloak-realm-reimport]].
   - **`test-all.sh`** — regenerated end-to-end from `resources[]`: per-service logins, `tokens.env`, the full role matrix (own→200, cross→403, ADMIN→200), gateway URLs `$GATEWAY_URL/<service><routePrefix>`, aggregation asserts. Batch/admin sections gated on their features.
   - **`service-consumer/.../AggregateController.java`** — `Mono.zip(List.of(...), combinator)` over N resource services (`lb://<service><routePrefix>`, map keyed by service name), instead of a hardcoded zip of the 3 demo services.

**`test-all.sh` has TWO sources — editing the static template alone is a silent no-op for the common case.**
The file ships in the static template (`templates/ms-platform/test-all.sh`, used verbatim when `resources[]` is empty) AND is regenerated from scratch by `TestAllRewriter` (formerly `CrossCuttingConfigProcessor.rewriteTestAll()`, from the `TEST_ALL_PROLOGUE` constant + per-resource appends) whenever `resources[]` is present — which is the normal usage. So a change made only to the template text never reaches a custom-resource platform. Any edit to test-all.sh behaviour must be applied in BOTH places (template file + Java prologue/builder), and verified by generating with a real `resources[]` payload, not just `{}`. This bit the "wait for the stack to be UP before testing" feature: the template edit alone left custom platforms failing with `Unable to get ADMIN token` / `503` because tests ran before services registered. Fix shipped a `wait_for`/`auth_ready`/`routed_up` readiness gate in both sources — `auth_ready` does a real admin login (proves ms-auth + Keycloak realm import), `routed_up` accepts 200/401/403 (proves Eureka registration + gateway routing), default `WAIT_TIMEOUT=180`. Same dual-source caveat applies to any other file that is both templated and regenerated.

**Verifying generation locally: the server is stateless per call, but zombie `java -jar` on :8080 will serve a stale jar.**
`POST /api/generate/platform` returns the zip in-memory (no output dir). To check generated output, run the jar and curl the endpoint — but old background servers squatting port 8080 silently serve the previous build, so generated files look "unchanged" after a rebuild. Kill prior servers (`pkill -f springboot-platform-generator`) or launch on an alt port (`--server.port=8077`) before verifying. The authoritative check that doesn't need a server at all: inspect `BOOT-INF/classes/templates/ms-platform/<file>` inside the freshly built jar with `unzip -p`.

**Service block removal is line-based, not regex.**
docker-compose blocks span multiple lines with indent-based scoping. The algorithm: find `  <name>:` (2-space indent), then consume lines until the next 2-space-indented sibling or column-0 top-level key. Blank lines between blocks are absorbed into the removed range. Regex doesn't work cleanly here because YAML indent-scoping isn't a regular language.

**docker-compose `volumes:` is overloaded — top-level section AND inline service list.**
A service block can contain `    volumes: [name:/path]` as an inline list, and the file also has a top-level `volumes:` section. Searching for `volumes:` with `indexOf` finds the inline one first (it appears earlier in the file). Always anchor to column 0: `text.indexOf("\nvolumes:")`. This bit a test originally written with the unanchored form.

**Substring matching bites YAML route IDs too.**
`- id: service-c` is a prefix of `- id: service-consumer`, so `assertThat(yml).doesNotContain("- id: service-c")` falsely fails when service-consumer is present. Always include the line terminator in the negative assertion: `doesNotContain("- id: service-c\n")`. Same family of bug as the `volumes:` overload — naive substring matching ignores YAML's hierarchical structure.

**H2 resources get no db block and no volume entry.**
H2 runs in-memory inside the JVM. POSTGRES and MONGO need their own container, env vars, and a named volume entry; H2 needs none. The `volumes:` cleanup logic and the resource block builder both branch on this.

**Stale volume entries cleaned with regex line removal.**
Volume entries are simpler than service blocks (single line each), so a multi-line regex `(?m)^  {name}:[^\n]*\n?` suffices. Volumes cleaned: `keycloak_db_data` (when `keycloak=false`), `redis_data` (when `redis=false`), `service_a_db_data` + `service_b_db_data` (when resources[] replaces defaults).

**Pragmatic defaults preserve API ergonomics.**
`ResourceModuleRequest.routePrefix` defaults to `/api/{className.toLowerCase()}s` when null/blank. Raw `String#replace` throws NPE on null replacement, so the upstream guard is necessary, but the choice to *default* rather than *reject* keeps the API friendly for minimal requests.

**Extending the processor when adding a new feature flag.**
If a new `FeatureOption` controls a service that has both filesystem files AND a docker-compose entry AND a root pom module AND a gateway route, you must update FIVE places (now spread across the rewriter beans): `FeatureFilterProcessor` (paths), `RootPomRewriter.desiredModules` (root pom), `ComposeRewriter.blocksToRemove` + `.volumesToRemove` (compose), and `GatewayRewriter.rewriteGatewayYml` (call `removeGatewayRoute` for the affected route id). The split is intentional — keep path filtering separate from cross-cutting content edits — but the multi-place update is the cost.

Related: [[ms-auth-design]] for the auth-specific design rationale.
