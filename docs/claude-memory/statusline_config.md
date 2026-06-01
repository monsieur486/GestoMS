---
name: statusline-config
description: "Configuration de la Claude Code status line — script bash, champs JSON disponibles, format actuel affiché"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 3db72030-c893-4c1b-bc2c-b2765fe06257
---

Script : `/home/mr486/.claude/statusline-command.sh`
Config : `/home/mr486/.claude/settings.json` — `statusLine.command`, `refreshInterval: 10`

**Format affiché :**
`🤖 Sonnet 4.6 │ 🧠 20% │ 📊 161k │ ⏳ 5h 4h11m │ 🌿 master │ 📁 GestoMS`

**Champs JSON réels exposés par Claude Code (v2.1.158) :**
- `model.display_name` — nom du modèle
- `workspace.current_dir` — répertoire courant
- `context_window.total_input_tokens`, `total_output_tokens`
- `context_window.remaining_percentage` — % contexte restant (0-100)
- `context_window.used_percentage`
- `rate_limits.five_hour.used_percentage`
- `rate_limits.five_hour.resets_at` — **Unix timestamp** (pas ISO 8601, pas `reset_at`)
- `rate_limits.seven_day.used_percentage` + `resets_at`
- `cost.total_cost_usd`, `total_duration_ms`, `total_lines_added`, `total_lines_removed`

**Pièges découverts :**
- Le champ est `resets_at` (Unix timestamp), PAS `reset_at` ni une date ISO 8601.
- Pour capturer le vrai JSON : pointer temporairement `statusLine.command` vers un script debug qui fait `cat > /tmp/debug.json`, envoyer un message, puis lire le fichier.

**Tokens format :** `awk` — `<1000` → entier, `≥1000` → `Xk`, `≥1M` → `X.XM`

**Couleurs contexte :** vert >50%, jaune 20-50%, rouge ≤20%
