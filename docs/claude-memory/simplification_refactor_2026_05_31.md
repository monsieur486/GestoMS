---
name: simplification-refactor-2026-05-31
description: "Refactor commit 82a431d — what was deduplicated, extracted, and simplified across the processor pipeline"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3db72030-c893-4c1b-bc2c-b2765fe06257
---

Commit `82a431d` (2026-05-31) applied a broad simplification pass across all processors:

**ProcessorUtils** (`pipeline/processor/ProcessorUtils.java`) — new package-private utility class consolidating three methods that were copy-pasted across 5 processors: `containsNullByte`, `relative`, `toPascalCase`. Add future shared utilities here.

**ResourceModuleRequest.getEffectiveRoutePrefix()** — canonical method for the route-prefix default (`/api/{classNameLower}s`). Any processor needing the effective prefix must call this; never inline the default logic again.

**TemplateLoader caching** — template is loaded once at construction (`new TemplateLoader()` → `loadFromClasspath()`). `load()` returns the cached list. No scan on every request.

**BatchConfigProcessor fast-path** — skips decode for files that aren't `.env`, `dist.env`, or `docker-compose.yml` (the only files that carry `BATCH_*` keys).

**Switch expressions for enum dispatch** — `applyDatabaseType` and `applyIdType` in `ResourceExpandProcessor` now use `switch` expressions (exhaustiveness enforced at compile time). Any new `DatabaseType` or `IdType` value must be handled there or the build fails.

**Dead try/catch removed** — `PackagePlaceholderProcessor.transformContent()` and `BatchConfigProcessor.replace()` had `catch(Exception)` around `new String(…, UTF_8)` / `.getBytes(UTF_8)` — no checked exceptions thrown; removed.

**appendAppService helper** — `CrossCuttingConfigProcessor.buildResourceServiceBlock` now calls `appendAppService(sb, name, deps)` for the common `build/env_file/depends_on/environment` block shared by all three DB types.

**Why:** Follow-up from `/simplify` skill run. Four parallel review agents (reuse, simplification, efficiency, altitude) flagged these issues.

**How to apply:** When adding a new processor or extending existing ones, check `ProcessorUtils` first before adding local helpers. Use `getEffectiveRoutePrefix()` on the DTO for route defaults. Use exhaustive `switch` expressions on enums.

Related: [[claude-md]], [[cross-cutting-config-pattern]]
