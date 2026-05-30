---
name: resource-expand-db-variants
description: "ResourceExpandProcessor clones service-a/ per resource and applies DB-type/id-type variants — the entity's package/id changes must be propagated to EVERY file that references it, or the generated service won't compile/start"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3dcf1186-bdaa-431b-81d7-378cfbb226f1
---

`ResourceExpandProcessor` (`@Order(50)`) derives one business service per `resources[]` entry by cloning the `service-a/` template, then applies `databaseType` and `idType` variants. The trap: each generated file is transformed independently, so a change to the **entity's package or id type must be propagated to every other file that references it**. Three separate breaks this session all came from forgetting that propagation — each surfaced only at compile/runtime, never in unit tests, because the test fixture for the service layer was a trivial stub that didn't import the entity.

**MONGO variant** (`applyMongo`): the entity moves from the `entity` package to `document` (and the file path `/entity/` → `/document/`) and becomes `String`-keyed (`@Id String id`). Two propagations are required and were initially missing:
- the **service** class still `import …entity.X` → must be rewritten to `…document.X` (done for every remaining `.java` via `.replace(".entity.", ".document.")`).
- the **DTO** still `private Long id` → must become `private String id`. `applyIdType` deliberately `return`s early for MONGO ("Mongo is always String id"), so the DTO's String id has to be handled inside `applyMongo`, not `applyIdType`.

**UUID variant** (`applyUuidType`): every file that ends up referencing `UUID` needs `import java.util.UUID` — entity, **DTO, and repository**. The original code only added the import to the entity (anchored on `import jakarta.persistence.*;`, which only the entity has), so the DTO/repo failed to compile. Fix: insert the import generically after the `package …;` line of any `.java` that references `UUID` and lacks it.

**MONGO `application.yml` is a separate Java-string template** (`MONGO_APP_YML_TEMPLATE`), NOT the `service-a` yaml that POSTGRES/H2 reuse. It had doubled placeholder braces `${{{SERVICE_UPPER}_PORT:8080}}` → after token substitution `${{PRODUCT_SERVICE_PORT:8080}}`. Spring resolves the inner placeholder and leaves a dangling brace (`port: 8080}`), so every Mongo service failed to **start** (compiled fine). Spring placeholders are single-brace: `${PRODUCT_SERVICE_PORT:8080}`.

**When adding a new `DatabaseType` or `IdType`:** audit ALL cloned files that name the entity (service, dto, repository, controller, `application.yml`), not just the entity class. And make the `ResourceExpandProcessorTest` service fixture *realistic* (it must import the entity and use the id type) — a stub fixture hides exactly this class of propagation bug.

Related: [[cross-cutting-config-pattern]] handles the per-resource rewrites of root-level files (compose, gateway, realm, test-all.sh, aggregator).
