---
id: PAR-PROC-09
title: "Barrel GUI, fluid level visual, rain fill, barrel rack"
area: PROC
status: done
wave: 12
deps: [PAR-PROC-08, PAR-INFRA-03, PAR-CLIM-02]
size: M
aliases: ["M3-PROC-03 (GUI/visual)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/BarrelContainer.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/barrel/gui/**"
  - "paper/src/test/java/dev/tfp/paper/barrel/gui/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrelrack/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrelrack/BarrelScreenArtTest.java"
ui: true
in_game_steps:
  - "tfp debug barrelgui 5000: screenshot at GUI scale 2 and 3; 8x crop of the tank"
  - "tfp debug barrelgui 0, 200 and 10000: 8x crops of the tank at GUI scale 2 and 3 (empty, 1 pixel, full)"
plan:
  - "GUI per catalogue `m3-proc-03-barrel` with tank overlay glyphs and seal button; fluid surface as an entity_renderer element tinted via tint_source (water/milk/brine colours); open barrels fill with rain lazily; horizontal barrels on a barrel rack with a tap (drain)."
  - "Split (PROTOCOL 3a + 6.5): opening the screen from the block (`paper/barrel/BarrelDevice.java`, `BarrelModule.java`), the device-record edit call (`paper/device/DeviceService.java`), the rain fill in the barrel catch-up and the fluid-IO slots (`core/barrel`, `BarrelDevice.advance`), the fluid surface element and the facing/rack states (`datagen/barrel/**`, `hitbox/HitboxDemands.java`) are all outside this item's files_owned. They, the barrel rack block and its tap, the in_game_steps and the screenshot criterion move to PAR-PROC-35 (deps PAR-PROC-09, not the other way). The GUI screenshot does not depend on later items (`/tfp debug barrelgui`), so it stays here (review fix)."
  - "Done: paper `barrel/gui/` (`BarrelScreens` service + `BarrelGuiListener` from `BarrelGuiModule`; `BarrelLink`/`BarrelSnapshot` contract with the device; `BarrelScreenLayout` 3-row chest layout; `BarrelTank` TFC fill height + tank item with tint and fluid tooltip; `BarrelFluidVisuals` TFC surface height and rain-fill numbers; `DemoBarrelLink` behind `/tfp debug barrelgui [mB] [fluid]`); datagen `barrelrack/BarrelScreenArt` (TFC barrel.png re-laid out, seal and disabled overlays, tank item model definition + 50 models + gauge texture). Tests: BarrelTankTest 4, BarrelScreensMockBukkitTest 9, BarrelScreenLayoutContentTest 2, BarrelFluidVisualsTest 3. Verification: deferred check tracked in PAR-PROC-35."
  - "Review fix: the tank was clipped to its 16 x 16 slot on 1.21.6+ clients (no `oversized_in_gui` in `items/gui/barrel_tank.json`), so half full drew a thin floating band; the definition sets it now, the fluid tiles from the top of the fill like TFC `fillAreaWithSprite`, and datagen `BarrelScreenArtTest` 3 pins what frame N draws (solid column from the tank bottom up N pixels, x 4..12, the gauge over the whole tank, threshold N to model N, tint index 0). Verification: docs/verification/PAR-PROC-09/."
tests:
  - "paper: tank glyph frame per level"
  - "datagen: BarrelScreenArtTest: the definition (oversized_in_gui, thresholds, tint) and frame N's geometry"
done_criteria:
  - "Screenshot of the barrel GUI with a half-full tank filling from the bottom (GUI scale 2 and 3); 1 and 50 pixel frames at the edges"
  - "Block, liquid surface, rain and rack: deferred check tracked in PAR-PROC-35"
---

# PAR-PROC-09: Barrel GUI, fluid level visual, rain fill, barrel rack

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
