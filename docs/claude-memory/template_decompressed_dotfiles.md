---
name: template-decompressed-dotfiles
description: "The platform template is a decompressed directory (not a zip) loaded by TemplateLoader; real dotfiles can't be shipped — they're stored dot-prefixed and decoded on load to survive plexus default excludes at TWO build layers"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3dcf1186-bdaa-431b-81d7-378cfbb226f1
---

The platform template lives **decompressed** under `src/main/resources/templates/ms-platform/` (it used to be an opaque binary zip — now Git-reviewable). `TemplateLoader` (renamed from `ZipTemplateLoader`) enumerates it via `PathMatchingResourcePatternResolver` on `classpath*:templates/ms-platform/**`, which works both from `target/classes` and packaged inside the boot jar. Each entry's path is derived from its URL (substring after `templates/`), directories skipped, and the `.sh`/`mvnw` executable bit re-derived heuristically (Unix perms don't survive the classpath). The class is declared **`final`**: its constructor loads the template eagerly and can throw `IllegalStateException`, which SpotBugs flagged as `CT_CONSTRUCTOR_THROW` (finalizer-attack risk on a partially-constructed object); `final` forbids subclassing and neutralizes the vector at the root (commit `f65cbaf`).

**Dotfile convention — the non-obvious part.** The template ships NO real dotfiles. `.gitignore`/`.env` are stored as **`dot-gitignore`/`dot-env`** and decoded back to `.` by `TemplateLoader` (decode the last path segment: `dot-X` → `.X`). Reason: plexus `DEFAULTEXCLUDES` (which includes `**/.gitignore`, `.gitattributes`, etc.) silently drop a real `.gitignore` at **two** independent build layers — `maven-resources-plugin` (src/main/resources → target/classes) **and** `maven-jar-plugin` (target/classes → jar). So a `.gitignore` could pass a unit test running from `target/classes` yet be missing from the boot jar (the symptom that exposed this: jar had 121 files, not 122). `.env` happens to survive (not an SCM-pattern), but both are encoded for uniformity.

Why encode instead of configure: `addDefaultExcludes` is **not** a valid `<resource>` model tag (only a `maven-resources-plugin` `<configuration>` knob), and `maven-jar-plugin` doesn't expose it at all — so no pom config fully fixes it. The `dot-` convention sidesteps every tooling layer.

**When adding a template dotfile, name it `dot-X`** — never ship a literal `.X`. Parity is guarded by `TemplateLoaderTest` (file count + explicit `.gitignore`/`.env` presence after decode).

Related: [[cross-cutting-config-pattern]].
