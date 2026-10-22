---
id: PAR-INFRA-02
title: "Device block framework: CraftEngine block behaviour routing, per-block state and lazy catch-up hooks"
area: INFRA
status: open
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
tests:
  - "integration/craftengine: controller round-trip for a fake DeviceKind"
  - "paper: MockBukkit routing test (use, break drops inventory, catch-up called once with the elapsed ticks)"
done_criteria:
  - "A fake device kind places, persists across restart, drops its inventory on break and catches up on interaction (self-test `device.roundtrip`)"
  - "ARCHITECTURE.md device section updated"
---

# PAR-INFRA-02: Device block framework: CraftEngine block behaviour routing, per-block state and lazy catch-up hooks

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
