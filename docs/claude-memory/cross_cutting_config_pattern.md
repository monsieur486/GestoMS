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

**The processor owns three responsibilities.**
1. Rewrite root `<modules>` block — regenerated from scratch based on features + resources[], not patched.
2. Remove obsolete docker-compose service blocks (e.g., `keycloak:` and `ms-auth:` when `keycloak=false`, `service-a/b/c:` and their `-db:` companions when resources[] is provided).
3. Append new docker-compose blocks for each `resources[]` entry, templated by `databaseType` (POSTGRES → app + postgres db + healthcheck, MONGO → app + mongo db, H2 → app only).

**Service block removal is line-based, not regex.**
docker-compose blocks span multiple lines with indent-based scoping. The algorithm: find `  <name>:` (2-space indent), then consume lines until the next 2-space-indented sibling or column-0 top-level key. Blank lines between blocks are absorbed into the removed range. Regex doesn't work cleanly here because YAML indent-scoping isn't a regular language.

**docker-compose `volumes:` is overloaded — top-level section AND inline service list.**
A service block can contain `    volumes: [name:/path]` as an inline list, and the file also has a top-level `volumes:` section. Searching for `volumes:` with `indexOf` finds the inline one first (it appears earlier in the file). Always anchor to column 0: `text.indexOf("\nvolumes:")`. This bit a test originally written with the unanchored form.

**H2 resources get no db block and no volume entry.**
H2 runs in-memory inside the JVM. POSTGRES and MONGO need their own container, env vars, and a named volume entry; H2 needs none. The `volumes:` cleanup logic and the resource block builder both branch on this.

**Stale volume entries cleaned with regex line removal.**
Volume entries are simpler than service blocks (single line each), so a multi-line regex `(?m)^  {name}:[^\n]*\n?` suffices. Volumes cleaned: `keycloak_db_data` (when `keycloak=false`), `redis_data` (when `redis=false`), `service_a_db_data` + `service_b_db_data` (when resources[] replaces defaults).

**Pragmatic defaults preserve API ergonomics.**
`ResourceModuleRequest.routePrefix` defaults to `/api/{className.toLowerCase()}s` when null/blank. Raw `String#replace` throws NPE on null replacement, so the upstream guard is necessary, but the choice to *default* rather than *reject* keeps the API friendly for minimal requests.

**Extending the processor when adding a new feature flag.**
If a new `FeatureOption` controls a service that has both filesystem files AND a docker-compose entry AND a root pom module, you must update three places: `FeatureFilterProcessor` (paths), `CrossCuttingConfigProcessor.desiredModules` (root pom), `CrossCuttingConfigProcessor.blocksToRemove` (compose), and possibly `volumesToRemove`. The split is intentional — keep path filtering separate from cross-cutting content edits — but the multi-place update is the cost.

Related: [[ms-auth-design]] for the auth-specific design rationale.
