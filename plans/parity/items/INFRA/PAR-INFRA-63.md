---
id: PAR-INFRA-63
title: "Device automation follow-ups: droppers and hopper minecarts into devices, screen-aware moves, per-device settle after a move, config.yml automation keys"
area: INFRA
status: done
wave: 19
deps: [PAR-INFRA-60]
size: M
aliases: ["PAR-INFRA-60 (2026-11-20): Paper fires HopperInventorySearchEvent only for hopper blocks; droppers and hopper minecarts, the open-screen pause, the barrel's fluid IO after a hopper insert and the config keys were left out"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/device/automation/**"
  - "paper/src/test/java/dev/tfp/paper/device/automation/**"
  - "paper/src/main/resources/config.yml"
  - "docs/config.md"
ui: false
in_game_steps: []
plan:
  - "Done (2026-11-25): item 1 for droppers only: `DropperAutomationListener` (BlockDispenseEvent on a dropper facing a registered device: same spec, sides, enable switch, busy/screen guards and HopperMover.push as a hopper; cancel, then shrink the dropper's first equal stack on its region), wired in DeviceAutomationModule; tests DropperAutomationMockBukkitTest (4: feeds and shrinks, single item empties the slot, top/screen/off refused, plain block and dispenser untouched). The rest (hopper minecarts, screen-aware moves, barrel settle, config.yml keys with reload) moved to PAR-INFRA-64: they need files outside files_owned (core ConfigCodec/TfpConfig reject an `automation:` section as unknown keys; the device screens and the barrel module own their registrations). Droppers keep the 8-block screen guard."
  - "1. Droppers facing a device (TFC/NeoForge dropperInsertHook): BlockDispenseEvent gives no slot and the dropper re-grows the split stack on cancel; find a safe consume path (or an InventoryMoveItemEvent-free mirror) without dupes; hopper minecarts (no search event in Paper: an entity-scheduler scan of minecarts over or under a device, throttled)."
  - "2. Screens: replace the 'any TFP screen within 8 blocks pauses automation' guard with the device screens' own edit/reload (BarrelScreens, FirepitScreens, PotScreens, GrillScreens, PowderkegScreens expose viewer/edit/reload per key): the device modules register a Spec busy/after hook through DeviceAutomation (coordinate with the device items' owners)."
  - "3. Barrel: after a hopper puts a fluid container in SLOT_FLUID_CONTAINER_IN, run BarrelDevice.settle (fluid IO and instant recipes) at once instead of at the next access (needs the barrel module to register its own Spec)."
  - "4. config.yml keys automation.barrel / fire-pit / quern / composter / powder-keg (TFC defaults true) and docs/config.md rows; reload applies them (today they are read once at enable)."
tests:
  - "MockBukkit: a dropper into a quern, a hopper minecart under a quern; an open barrel screen keeps its copy in step with a hopper move; reload switches automation off"
done_criteria:
  - "Droppers feed the devices as hoppers do (hopper minecarts, screen-aware moves, barrel settle and config keys: PAR-INFRA-64)"
---

# PAR-INFRA-63: Device automation follow-ups

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
