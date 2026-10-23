---
id: PAR-HUD-09
title: "Mount health bar: the ridden animal's health as a second health bar, as TFC's MOUNT_HEALTH"
area: HUD
status: open
wave: 5
deps: [PAR-HUD-05]
size: S
aliases: ["PAR-HUD-05 follow-up: vehicle hearts hidden, no mount health shown"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
  - "docs/hud/catalogue.md"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudMountValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudMountValues*"
  - "paper/src/main/java/dev/tfp/paper/player/MountHudService.java"
  - "paper/src/test/java/dev/tfp/paper/player/MountHudService*"
ui: true
in_game_steps:
  - "summon minecraft:horse ~ ~ ~ {Tame:1b,SaddleItem:{id:\"minecraft:saddle\",count:1}}; ride Bot mount @e[type=horse,limit=1,sort=nearest]"
  - "damage @e[type=horse,limit=1,sort=nearest] 6"
  - "screenshot hotbar region at scale 2 and 3, with and without full steel armour"
plan:
  - "PAR-HUD-05 hides the vanilla vehicle hearts (heart/vehicle_*.png transparent, part of the hide-hearts set), so a rider sees no mount health at all. TFC's renderMountHealth draws the vehicle's health with renderHealthBar on the left column (leftHeight), above the player's health and armour."
  - "Publish the ridden living vehicle's health and max health into the HUD snapshot (tfp_mount_health, tfp_mount_max_health; 0 when not riding a living entity), add a catalogue row and a bar spec in HudLayouts above the armour bar with one empty row, shown only while tfp_mount_max_health > 0."
tests:
  - "integration/betterhud: placeholder values riding and not riding"
  - "tools/datagen: HudLayoutsTest placement (no overlap with armour, health, thirst, air)"
done_criteria:
  - "Riding a damaged horse shows its health bar and text, no overlap with armour or health at scale 2 and 3"
---

# PAR-HUD-09: Mount health bar: the ridden animal's health as a second health bar, as TFC's MOUNT_HEALTH

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
