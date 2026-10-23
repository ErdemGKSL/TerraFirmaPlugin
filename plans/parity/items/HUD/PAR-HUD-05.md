---
id: PAR-HUD-05
title: "HUD vs vanilla armour row: no overlap with armour worn, health text inside its bar"
area: HUD
status: done
wave: 4
deps: [PAR-HUD-04]
size: S
aliases: ["owner: armour-row overlap"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/chestplate/steel (and helmet, greaves, boots); equip"
  - "screenshot hotbar region at scale 2 and 3"
plan:
  - "With full armour worn, the vanilla armour row draws at H-49..H-41 on the left; check it against the health bar (H-40..H-31) and its text in-game."
  - "If they touch, move the armour row (hide vanilla armour sprites and draw an armour readout, or shift the health bar) and keep the HudLayoutsTest placement assertions in sync."
  - "Also check the right column with mounted health (horse hearts) and the XP number."
  - "Done: vanilla armour row (H-49..H-41) touches the health frame (H-40), so it stays hidden; the armour bar (H-48..H-42, one empty row) and the health text inside its interior verified in game with full steel armour at GUI scale 2 and 3, also riding a horse and with XP level 120; new HudLayoutsTest.armourWornNeitherTouchesHealthNorItsText; HudLayouts.java unchanged; evidence docs/verification/PAR-HUD-05/; mount health divergence recorded, follow-up PAR-HUD-09."
tests:
  - "tools/datagen: HudLayoutsTest placement rule for the armour row with armour worn"
done_criteria:
  - "Screenshot with full steel armour shows no overlap at scale 2 and 3"
---

# PAR-HUD-05: HUD vs vanilla armour row: no overlap with armour worn, health text inside its bar

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
