# Claude memory snapshot

Versioned snapshot of the persistent notes maintained by Claude Code while
working on this project. The live copy lives at
`~/.claude/projects/-home-mr486-Developpement-Projets-GestoMS/memory/` and is
the source of truth at conversation time; the files here are mirrors kept for
code review, onboarding context, and historical reference.

## How it stays in sync

After any write to a memory file in `~/.claude/...`, Claude auto-syncs this
directory and commits the change. The behavior is recorded in
[sync_memory_to_repo.md](sync_memory_to_repo.md) (a `feedback`-type memory).

Manual sync, if ever needed:

```bash
cp ~/.claude/projects/-home-mr486-Developpement-Projets-GestoMS/memory/*.md docs/claude-memory/
git add -f docs/claude-memory/
git commit -m "docs: sync Claude memory snapshot"
```

`docs/` is in `.gitignore`, so `git add -f` is required.

## What's in here

Start with [MEMORY.md](MEMORY.md) — it's the index of every memory file with a
one-line description. Each individual `.md` file has YAML frontmatter
indicating its type (`user`, `feedback`, `project`, `reference`) and a
self-contained body that documents *why* a decision was made, not *what* the
code does.

## What's NOT in here

These files are deliberately not exhaustive documentation. They omit anything
derivable from the code itself: file layouts, conventions, architecture
diagrams, git history. Instead they capture the small set of facts and
rationales that are non-obvious to a future reader (including future Claude
sessions) and that would otherwise be lost when context is compacted.

If you find yourself reaching for a memory entry to learn *how the code
works*, prefer reading the code; the memory only adds value when the code
alone can't answer *why*.
