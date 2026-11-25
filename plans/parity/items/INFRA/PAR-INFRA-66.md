---
id: PAR-INFRA-66
title: "Device automation, screens and barrel: the device screens' own edit/reload instead of the 8-block pause, barrel fluid settle after a hopper insert"
area: INFRA
status: done
wave: 21
deps: [PAR-INFRA-64]
size: M
aliases: ["PAR-INFRA-64 (2026-11-25): split; hopper minecarts and the config.yml automation keys are done there, these two parts need the device modules' own files"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/device/automation/**"
  - "paper/src/test/java/dev/tfp/paper/device/automation/**"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelModule.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelDevice.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/gui/BarrelScreens.java"
  - "paper/src/main/java/dev/tfp/paper/firepit/gui/FirepitScreens.java"
  - "paper/src/main/java/dev/tfp/paper/pot/gui/PotScreens.java"
  - "paper/src/main/java/dev/tfp/paper/grill/gui/GrillScreens.java"
  - "paper/src/main/java/dev/tfp/paper/powderkeg/PowderkegScreens.java"
ui: false
in_game_steps: []
plan:
  - "1. Screens: replace the 'any TFP screen within 8 blocks pauses automation' guard (HopperAutomationListener.screenNearby, also used by the dropper and minecart paths) with the device screens' own edit/reload: each screen module registers a Spec busy/after hook through DeviceAutomation (BarrelScreens, FirepitScreens, PotScreens, GrillScreens, PowderkegScreens): a hopper move while a screen shows the device edits the record and tells the open screen to reload its copy, so nothing dupes or is lost."
  - "2. Barrel: after a hopper (or minecart) puts a fluid container in SLOT_FLUID_CONTAINER_IN, run BarrelDevice.settle at once (the barrel module registers its own Spec with an afterMove that settles; TFC does it within 5 ticks in serverTick)."
tests:
  - "MockBukkit: an open barrel screen keeps its copy in step with a hopper move (no dupe, no loss); a water bucket a hopper puts into the barrel is emptied into the tank at once"
done_criteria:
  - "No automation pause near open screens and no dupes with a screen open; a fluid container inserted by a hopper settles at once"
---

# PAR-INFRA-66: Device automation, screens and barrel

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.

Done: screens keep their own edit/reload around hopper, dropper and minecart moves (`DeviceAutomation.ScreenSync`, `ViewerSync`; the 8-block pause is gone, only a viewer on another Folia region waits); the barrel's fluid IO settles in the same edit (`Spec#settle`, `BarrelDevice.hopperSettle`). Tests: `BarrelHopperAutomationMockBukkitTest` 4 plus the updated automation tests. No in-game check (ui false).
