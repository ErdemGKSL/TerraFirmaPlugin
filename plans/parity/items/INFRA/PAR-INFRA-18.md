---
id: PAR-INFRA-18
title: "Device framework wiring: register tfp:device with CraftEngine, self-test device.roundtrip, SAVE removal"
area: INFRA
status: open
wave: 3
deps: [PAR-INFRA-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/DeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/InventoryBlockEntity.java"
files_owned:
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/CraftEngineProvider.java"
  - "itest/src/main/java/dev/tfp/itest/cases/DeviceRoundTripCase.java"
  - "itest/src/main/resources/craftengine/tfpitest/configuration/devices.yml"
  - "paper/src/main/java/dev/tfp/paper/device/**"
  - "paper/src/test/java/dev/tfp/paper/device/**"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/device/**"
  - "paper/src/test/java/dev/tfp/paper/boot/BootstrapTest.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-02 built the device framework (core/device, paper/device, integration/craftengine/device) but could not register the CraftEngine behaviour: the only onLoad hook is `CraftEngineProvider.create`, outside its files. `DeviceBlockBehaviour.register(behaviours)` exists and installs the CE device store; nothing calls it yet, so no block can use `type: tfp:device`."
  - "Add the one line `DeviceBlockBehaviour.register(behaviours);` to `CraftEngineProvider.create` (before `new CraftEngineIntegration`)."
  - "Register `/tfp debug device`: override `DeviceModule.debugCommands` to call the existing `addDebugCommand(tree)`, and add `device` to the pinned debug literal list in `BootstrapTest.commandTreeStructure` (PAR-INFRA-02 could not: that test is outside its files)."
  - "Add the probe block to the self-test pack (`devices.yml`): a `tfpitest:device/probe` block with `behavior: { type: tfp:device }`, and have the case register a `ProbeDevice`-style kind for that id through `DeviceService.register` (the built-in `ProbeDevice` kind is `tfp:device/probe`; `DeviceModule` no longer registers it, so the case must)."
  - "Check in game when CraftEngine stores the device block entity relative to `BlockPlaceEvent` (its `BlockItemBehavior` places before firing the event) and when the `placed` region task runs on Folia; correct the `DeviceListener` javadoc, ARCHITECTURE.md §7.1 and the ledger divergence row on placement accordingly (review note on PAR-INFRA-02)."
  - "Add `DeviceRoundTripCase` (`device.roundtrip`, SelfTestSuite hub): place the probe, right click with an item (slot filled, record stamped), advance the calendar, right click again (catch-up called once with the gap), unload/reload the chunk and restart (record kept), break it (the item drops, block entity gone)."
  - "Add `DeviceKind.RemoveBehaviour.SAVE` (TFC `InventoryRemoveBehavior.SAVE`): the dropped block item keeps the record (item PDC `tfp:device`), placing it restores the record (`DeviceController.loadCustomDataFromItem`)."
tests:
  - "itest: DeviceRoundTripCase passes on Paper 1.21.11 and Folia (run delegated per PROTOCOL 5.3)"
  - "paper: MockBukkit test for SAVE (record on the item, restored on placement)"
done_criteria:
  - "A fake device kind places, persists across restart, drops its inventory on break and catches up on interaction (self-test `device.roundtrip`)"
  - "Ledger divergence 'InventoryRemoveBehavior.SAVE' closed"
---

# PAR-INFRA-18: Device framework wiring: register tfp:device with CraftEngine, self-test device.roundtrip, SAVE removal

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
