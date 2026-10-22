---
id: PAR-HUD-06
title: "HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation"
area: HUD
status: open
wave: 5
deps: [PAR-HUD-05]
size: S
aliases: ["gaps: bar centring frame asymmetry", "hud-layered-redesign §2"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hud/**"
  - "art/raw/hud-*/**"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudValues.java"
ui: true
in_game_steps:
  - "tfp time set noon"
  - "screenshot top-left plate and bars at scale 2 and 3"
plan:
  - "Correct the 4.5 px frame asymmetry of the health bar master (re-derive layers with tools/images/hud_layers.py)."
  - "Verify the ambient temperature bar ((t+20)/60) and the date/temperature plate in the top-left at both scales."
  - "Optional low-value pulse (opacity-equation) for critical thirst/health as TFC flashes; decide with the owner."
tests:
  - "HudArtTest masks after re-derivation"
done_criteria:
  - "Owner accepts the screenshots"
---

# PAR-HUD-06: HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
