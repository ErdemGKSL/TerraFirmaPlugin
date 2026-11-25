---
id: PAR-INFRA-64
title: "Device automation, remaining parts: hopper minecarts, screen-aware moves, barrel fluid settle after a hopper insert, config.yml automation keys with reload"
area: INFRA
status: done
wave: 20
deps: [PAR-INFRA-63]
size: M
aliases: ["PAR-INFRA-63 (2026-11-25): split; droppers are done there"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/device/automation/**"
  - "paper/src/test/java/dev/tfp/paper/device/automation/**"
  - "paper/src/main/resources/config.yml"
  - "docs/config.md"
  - "core/src/main/java/dev/tfp/core/config/**"
  - "core/src/test/java/dev/tfp/core/config/**"
  - "paper/src/main/java/dev/tfp/paper/config/**"
ui: false
in_game_steps: []
plan:
  - "1. Hopper minecarts (no search event in Paper): an entity-scheduler scan of minecarts over or under a device, throttled, through HopperMover."
  - "2. Screens: replace the 'any TFP screen within 8 blocks pauses automation' guard with the device screens' own edit/reload (BarrelScreens, FirepitScreens, PotScreens, GrillScreens, PowderkegScreens): the device modules register a Spec busy/after hook through DeviceAutomation (needs those modules' files: add them to files_owned when picked)."
  - "3. Barrel: after a hopper puts a fluid container in SLOT_FLUID_CONTAINER_IN, run BarrelDevice.settle at once (the barrel module registers its own Spec; needs its files)."
  - "4. config.yml keys automation.barrel / fire-pit / quern / composter / powder-keg (TFC defaults true) as a typed `automation` section of TfpConfig/ConfigCodec (today they are unknown keys there, so adding them to config.yml warns), docs/config.md rows, reload applies them (the Switches are read once at enable)."
  - "Done: parts 1 and 4. (1) HopperMinecartAutomation: every hopper minecart entering a world gets an entity-scheduled task (4 ticks, vanilla MinecartHopper cooldown) that looks at the block above it and, for a registered device, pulls one item from the device's bottom face through HopperMover with the hopper's gates (switch, lock, busy, open screen, region owner); activator-rail disabled carts move nothing; a minecart never fills a device. (4) TfpConfig.Automation: the typed `automation` section of config.yml (barrel, fire-pit, quern, composter, powder-keg, TFC default true, ConfigCodec parse/write, unknown keys no longer warn), docs/config.md rows now mapped; DeviceAutomationModule reads the current config at every move, so `/tfp reload config` applies a change at once. Tests: HopperMinecartAutomationMockBukkitTest (4), HopperAutomationMockBukkitTest (typed switches, reload off/on), ConfigCodecTest#automationKeysAreReadWrittenAndDefaulted, ConfigServiceTest (added section), ConfigParityDocTest; all pass. Edited outside files_owned: ConfigServiceTest's expected added-sections list (one word). Parts 2 and 3 (device screens' own edit/reload instead of the 8-block pause, barrel fluid settle after a hopper insert) need the device modules' screen files: follow-up PAR-INFRA-66. Ledger: the hopper-minecart and EnableAutomation divergence rows updated, the screen and settle rows now point to PAR-INFRA-66."
tests:
  - "MockBukkit: a hopper minecart under a quern; reload switches automation off; ConfigCodecTest for the automation section (the screen test moved to PAR-INFRA-66)"
done_criteria:
  - "Hopper minecarts empty the devices as hoppers do; keys in config.yml and applied on reload (screens and the barrel settle: PAR-INFRA-66)"
---

# PAR-INFRA-64: Device automation, remaining parts

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
