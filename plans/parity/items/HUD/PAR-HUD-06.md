---
id: PAR-HUD-06
title: "HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation"
area: HUD
status: done
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
  - "Done: frame asymmetry already gone (the 4.5 px came from the first art; masters mirror-symmetric, hud_layers.py re-derivation bit-identical, in game the frames span -91..-11 and +10..+90 = the hotbar at GUI 2 and 3); thermometer (t+20)/60 verified in game at 8.7, 5.9, -11.5, -9.8 C within one column; pulse not built (TFC has no low-value flash, parity); date line overflows the 124 px plate in long months and a wider plate needs layout compensation outside files_owned: follow-up PAR-HUD-13; no source change; HudArtTest, HudLayoutsTest, core/paper/datagen tests pass; evidence docs/verification/PAR-HUD-06/ (owner acceptance of the crops pending)."
tests:
  - "HudArtTest masks after re-derivation"
done_criteria:
  - "Owner accepts the screenshots"
---

# PAR-HUD-06: HUD polish: health frame asymmetry, ambient temperature bar and info plate, pulse animation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
