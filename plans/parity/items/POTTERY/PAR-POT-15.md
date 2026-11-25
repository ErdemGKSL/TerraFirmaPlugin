---
id: PAR-POT-15
title: "Bowl automation: hoppers fill and empty powder bowls, the powder layer follows"
area: POTTERY
status: done
wave: 28
deps: [PAR-POT-09, PAR-INFRA-60]
size: S
aliases: ["split from PAR-POT-09: the powder layer is an item display refreshed by the device handler, a hopper move needs a refresh hook"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/BowlBlockEntity.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/bowl/**"
  - "paper/src/test/java/dev/tfp/paper/bowl/**"
  - "paper/src/main/java/dev/tfp/paper/device/automation/**"
  - "paper/src/test/java/dev/tfp/paper/device/automation/**"
  - "paper/src/main/resources/config.yml"
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "docs/config.md"
ui: false
in_game_steps: []
plan:
  - "TFC `powderBowlEnableAutomation` (default true): `PartialItemHandler(inventory).insertAll()` from Direction.UP, `extractAll()` from every other side. Register a `DeviceAutomation.Spec` for `tfp:ceramic/bowl` and `tfp:wooden_bowl` with a side table (core/device/automation `SideTable`), a config switch `automation.powder-bowl` (shipped config and docs) and refresh the powder layer (`BowlEffects.show`) after a hopper move: needs a post-move hook in the hopper mover (the spec has only the pure `SlotFix`), or a one-tick region task per moved device."
  - "Test: hopper above a bowl fills it with redstone up to 16 and the layer follows; a hopper below or beside a full bowl empties it; the switch off refuses both."
tests:
  - "paper: MockBukkit hopper events on a bowl"
done_criteria:
  - "A hopper above a powder bowl fills it, one beside it empties it, and the layer matches the slot after each move"
---

Done: PAR-POT-15 `paper/bowl/BowlAutomation` (side table: insert all from above, extract all from the other five sides; switch `automation.powder-bowl` added to `TfpConfig.Automation` with a five-argument constructor kept; settle redraws the powder layer after each hopper move), registered by `BowlModule` with the `DeviceAutomation` service. Tests: paper `BowlAutomationMockBukkitTest` 5, `ConfigCodecTest` updated. No UI.

# PAR-POT-15: Bowl automation: hoppers fill and empty powder bowls, the powder layer follows

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
