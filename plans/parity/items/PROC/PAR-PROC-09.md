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
ui: false
in_game_steps: []
plan:
  - "GUI per catalogue `m3-proc-03-barrel` with tank overlay glyphs and seal button; fluid surface as an entity_renderer element tinted via tint_source (water/milk/brine colours); open barrels fill with rain lazily; horizontal barrels on a barrel rack with a tap (drain)."
  - "Split (PROTOCOL 3a + 6.5): opening the screen from the block (`paper/barrel/BarrelDevice.java`, `BarrelModule.java`), the device-record edit call (`paper/device/DeviceService.java`), the rain fill in the barrel catch-up and the fluid-IO slots (`core/barrel`, `BarrelDevice.advance`), the fluid surface element and the facing/rack states (`datagen/barrel/**`, `hitbox/HitboxDemands.java`) are all outside this item's files_owned. They, the barrel rack block and its tap, the in_game_steps and the screenshot criterion move to PAR-PROC-35 (deps PAR-PROC-09, not the other way). This session also had no cheap-model agent to run the headless client, so the GUI screenshot (possible now with `/tfp debug barrelgui`) is the first step there."
  - "Done: paper `barrel/gui/` (`BarrelScreens` service + `BarrelGuiListener` from `BarrelGuiModule`; `BarrelLink`/`BarrelSnapshot` contract with the device; `BarrelScreenLayout` 3-row chest layout; `BarrelTank` TFC fill height + tank item with tint and fluid tooltip; `BarrelFluidVisuals` TFC surface height and rain-fill numbers; `DemoBarrelLink` behind `/tfp debug barrelgui [mB] [fluid]`); datagen `barrelrack/BarrelScreenArt` (TFC barrel.png re-laid out, seal and disabled overlays, tank item model definition + 50 models + gauge texture). Tests: BarrelTankTest 4, BarrelScreensMockBukkitTest 9, BarrelScreenLayoutContentTest 2, BarrelFluidVisualsTest 3. Verification: deferred check tracked in PAR-PROC-35."
tests:
  - "paper: tank glyph frame per level"
done_criteria:
  - "Deferred check tracked in PAR-PROC-35"
---

# PAR-PROC-09: Barrel GUI, fluid level visual, rain fill, barrel rack

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
