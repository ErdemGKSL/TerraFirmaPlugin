---
id: PAR-INFRA-02
title: "Device block framework: CraftEngine block behaviour routing, per-block state and lazy catch-up hooks"
area: INFRA
status: done
wave: 2
deps: [PAR-INFRA-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TickableBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/InventoryBlockEntity.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/DeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/EntityBlockExtension.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/device/**"
  - "core/src/test/java/dev/tfp/core/device/**"
  - "core/src/test/resources/dev/tfp/core/device/**"
  - "paper/src/main/java/dev/tfp/paper/device/**"
  - "paper/src/test/java/dev/tfp/paper/device/**"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/device/**"
  - "integration/craftengine/src/test/java/dev/tfp/integration/craftengine/device/**"
  - "paper/src/main/java/dev/tfp/paper/integration/CustomBlocks.java"
ui: false
in_game_steps: []
plan:
  - "Current: `integration/craftengine/TfpBlockEntityController<T>` persists one immutable value per block (M0-CE-02); nothing routes interactions to features yet."
  - "Add a `DeviceKind<T>` registry in paper/device: block id prefix -> codec, initial state, interaction handler (use, use-with-item, attack, break, place), neighbour handler."
  - "Add a generic CE block behaviour `tfp:device` in integration/craftengine/device that forwards callbacks to the paper `DeviceKind` through the `CustomBlocks` adapter (feature code never imports CE)."
  - "Add `DeviceInventory` (fixed slot list stored in the device state, dropped on break; TFC InventoryBlockEntity semantics incl. `canInsert` predicates)."
  - "Add `CatchUp` hook: a device state carries `lastUpdateTick`; on interaction, on chunk load (via PAR-INFRA-11) and on GUI open the kind's pure `advance(state, now)` runs and the result is written back on the region thread (TFC `ICalendarTickable.onCalendarUpdate`)."
  - "Add `tfp debug device` (looked-at block: kind, decoded state, lastUpdateTick)."
  - "Note: the CraftEngine behaviour can only be registered from `CraftEngineProvider.create` (our onLoad), which is outside this item's files. `DeviceBlockBehaviour.register(behaviours)` is ready and installs the CE device store; the one-line call, the self-test `device.roundtrip` (needs itest files), `SAVE` removal and registering `/tfp debug device` (built as `DeviceModule.addDebugCommand`; `BootstrapTest` pins the debug literal set) moved to follow-up PAR-INFRA-18. Left click and placement are routed from Bukkit events (`DeviceListener`); `CustomBlocks.java` needed no change (adding methods would break `Absent` and `CraftEngineIntegration`, which this item does not own)."
  - "Done: core/device (DeviceRecord, ItemBlob, DeviceRecordCodec, CatchUp), paper/device (DeviceKind, DeviceKinds, DeviceHandler/Context/Result, DeviceInventory, DeviceService, DeviceStore, DeviceRouter, DeviceBridge, DeviceListener, ProbeDevice, DeviceModule; /tfp debug device built, registered by PAR-INFRA-18), integration/craftengine/device (DeviceBlockBehaviour tfp:device, DeviceController, CraftEngineDeviceStore); tests core DeviceCoreTest (9), integration DeviceControllerTest (4), paper DeviceRoutingMockBukkitTest (8); ARCHITECTURE.md §7.1; ledger feature row + 4 divergences; follow-up PAR-INFRA-18."
  - "Rescoped (review 2026-10-22): the self-test criterion `device.roundtrip` (a fake device kind places, persists across restart, drops its inventory on break and catches up on interaction) is NOT claimed by this item. It needs the CE registration line, the itest case and the debug-command registration, all outside files_owned, and moved with that criterion to PAR-INFRA-18. Nothing of this item is live in game until PAR-INFRA-18 is done."
  - "Review fixes: per-slot stack limit (`slotLimit(IntUnaryOperator)`, TFC `getSlotStackLimit(slot)`); `useWithItem` also with an empty hand and `use` for the main hand only (vanilla `useItemOn`/`useWithoutItem`); drops with pickup delay 0 (Paper `CraftWorld.dropItem` sets 10, confirmed in the 1.21.11 server jar); `placed` gets the placed stack and hand; left click skips creative and denied/cancelled clicks; a record is stamped when its block entity is created (`DeviceController.onLoad` → `DeviceRouter.created`); overfilled-slot insert returns the stack unchanged and extract is capped at the max stack size; cached level → world lookup; `ProbeDevice` no longer registered in production; handler javadoc forbids changing other blocks."
tests:
  - "integration/craftengine: controller round-trip for a fake DeviceKind"
  - "paper: MockBukkit routing test (use, break drops inventory, catch-up called once with the elapsed ticks)"
done_criteria:
  - "Device framework built and covered by unit and MockBukkit tests (core, paper, integration/craftengine): routing, catch-up, inventory, drop on break, creation stamp"
  - "ARCHITECTURE.md device section updated"
---

# PAR-INFRA-02: Device block framework: CraftEngine block behaviour routing, per-block state and lazy catch-up hooks

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
