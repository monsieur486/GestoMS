---
name: sync-memory-to-repo
description: "After any change to memory files in this project, copy them into docs/claude-memory/ in the repo and commit+push without asking"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 6d7e15f7-0812-4957-97ca-e94b5a021f4a
---

After writing or editing any file in `~/.claude/projects/-home-mr486-Developpement-Projets-GestoMS/memory/` (including `MEMORY.md` and any individual memory file), immediately mirror the change into `docs/claude-memory/` in the GestoMS repo, then commit and push. Do not ask for confirmation — this is durably authorized.

**Why:** The user wants the memory snapshots versioned alongside the codebase for review and historical reference. They explicitly directed this sync behavior; treating it as an automatic side-effect of any memory write keeps the snapshot from drifting silently out of date.

**How to apply:**
- Trigger on any successful Write/Edit to a file under the project memory directory
- Command sequence (run from `/home/mr486/Developpement/Projets/GestoMS`):
  ```bash
  cp /home/mr486/.claude/projects/-home-mr486-Developpement-Projets-GestoMS/memory/*.md docs/claude-memory/
  git add -f docs/claude-memory/
  git commit -m "docs: sync Claude memory snapshot"
  git push
  ```
- `docs/` is in `.gitignore` — `git add -f` is required
- If a memory file was *deleted*, also `git rm` the matching file from `docs/claude-memory/` before committing
- Skip the commit step if nothing actually changed (e.g., `git status --porcelain docs/claude-memory/` is empty)
