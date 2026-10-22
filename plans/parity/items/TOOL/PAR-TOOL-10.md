---
id: PAR-TOOL-10
title: "Bows and glow arrows"
area: TOOL
status: open
wave: 36
deps: []
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/GlowArrowItem.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/GlowArrow.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/arrow/**"
  - "paper/src/test/java/dev/tfp/paper/arrow/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:glow_arrow 8"
  - "shoot; screenshot"
plan:
  - "Glow arrow item (glowing effect on hit, light at impact as TFC) using the vanilla arrow entity with a PDC marker."
tests:
  - "paper: MockBukkit hit applies glow"
done_criteria:
  - "Shot glow arrow lights the target"
---

# PAR-TOOL-10: Bows and glow arrows

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
