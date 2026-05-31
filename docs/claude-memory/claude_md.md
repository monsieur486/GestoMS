---
name: claude-md
description: "CLAUDE.md exists at repo root — covers build commands, pipeline architecture, template dotfile convention, and memory index"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3db72030-c893-4c1b-bc2c-b2765fe06257
---

A `CLAUDE.md` was created at the repo root (commit `aae577f`, 2026-05-31). It documents:

- Build and test commands (`mvn clean package`, `mvn test -Dtest=<Class>`)
- The full generation pipeline (`TemplateLoader` → 7 ordered `FileProcessor` beans → `ZipService`)
- Key design points: dotfile `dot-` prefix, `CrossCuttingConfigProcessor` responsibilities, `test-all.sh` dual-source trap, `PlatformVersions` dual-use, the 5-place extension checklist
- Template module inventory (permanent / conditional / default services)
- Pointer to `docs/claude-memory/` with per-entry summaries

**Why:** Future Claude sessions start with full architectural context without re-exploring the codebase.

**How to apply:** When onboarding to the project, read CLAUDE.md first. Keep it updated whenever pipeline order, feature flags, or the template module list changes.

Related: [[cross-cutting-config-pattern]], [[sync-memory-to-repo]]
