---
id: PAR-CLIM-28
title: "Climate instruments: moving parts, device sources and support rules"
area: CLIMATE
status: done
wave: 36
deps: [PAR-CLIM-27]
size: M
aliases: ["split from PAR-CLIM-27"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/AnemometerBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/VaneBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CalendarClockBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/instrument/**"
  - "paper/src/test/java/dev/tfp/paper/instrument/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/instrument/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/instrument/**"
ui: true
in_game_steps:
  - "place a thermometer on a firebox, an anemometer under a vane, a clock; compare with TFC"
plan:
  - "Moving parts: the cups, the vane arrow and the clock hands are drawn at their rest angle. Split them into their own display parts (EntityBoxes.model per part) and set their rotation from the reading on a click and on neighbour updates (no ticking)."
  - "Support rules: thermometer and clock survive on a sturdy face or a thermometer_readable / clock_readable block, the anemometer on a centre support, the vane on an anemometer or a centre support; attached_wind_devices and attached written when neighbours change (decide async, apply sync)."
  - "Register InstrumentSources (firebox, crucible, blast furnace, barrel, bloomery) from those device modules."
  - "Done: moving parts as own displays (EntityBoxes.moving, quaternion rotation + translation about the part pivot): vane arrow `wind` 0..15 and clock `dial` 0..47 / 0..11 (month), written on click, placement and neighbour change (Instruments.vaneStep/clockDial/clockMonthDial); anemometer cups stay at rest (ledger divergence). InstrumentRules + InstrumentDevices.placed/neighbourChanged: placement facing from the look direction, support rules, attached/attached_wind_devices, a lost support breaks the block (CustomBlocks.remove, loot self). DeviceSources: firebox heat + kiln timer and crucible heat registered through the instrument module (load-order free); blast furnace, bloomery and the barrel timer are follow-up PAR-CLIM-29. Tests: InstrumentBlocksTest (8), InstrumentDevicesMockBukkitTest (14), InstrumentReadoutTest, InstrumentsGoldenTest.restingSteps. Pending in-game check (container cannot run client)"
tests:
  - "paper: placement, support and attached rules"
done_criteria:
  - "The four survive and attach like TFC"
---

# PAR-CLIM-28: Climate instruments: moving parts, device sources and support rules

Follow-up of PAR-CLIM-27. Follow `plans/parity/PROTOCOL.md`.
