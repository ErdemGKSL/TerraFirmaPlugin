---
id: PAR-HUD-01
title: "Two-layer HUD emitted by datagen (HudArt, offset-left layouts, trim compensation)"
area: HUD
status: done
wave: 0
deps: []
size: M
aliases: ["hud-layered-redesign §4", "M3-SURV-06"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/resources/assets/tfc/textures/gui/icons"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudArt.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
ui: false
in_game_steps: []
plan:
  - "Done in commit 99070a7 (HudArt, offset-left layouts, trim-compensated layers) and 6f8e8f2 (plain air bar, black health shadow)."
tests:
  - "HudArtTest, HudLayoutsTest"
done_criteria:
  - "Committed and green"
---

# PAR-HUD-01: Two-layer HUD emitted by datagen (HudArt, offset-left layouts, trim compensation)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
