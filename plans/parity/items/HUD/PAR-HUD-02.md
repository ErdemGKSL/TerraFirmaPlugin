---
id: PAR-HUD-02
title: "Two-layer HUD art: one master per bar, bg and fill derived by hud_layers.py"
area: HUD
status: done
wave: 0
deps: []
size: S
aliases: ["hud-layered-redesign §3"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures/gui/icons"
files_owned:
  - "tools/images/hud_layers.py"
  - "art/raw/hud-*/**"
ui: false
in_game_steps: []
plan:
  - "Done in commits 33c33b8 and 5f19e5d (owner feedback: plain style, compact air bar)."
tests:
  - "HudArtTest"
done_criteria:
  - "Committed"
---

# PAR-HUD-02: Two-layer HUD art: one master per bar, bg and fill derived by hud_layers.py

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
