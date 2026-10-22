---
id: PAR-PHYS-01
title: "TFC falling blocks: canFallThrough, landing, damage"
area: PHYS
status: open
wave: 16
deps: [PAR-INFRA-16]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/IFallableBlock.java"
  - "src/main/java/net/dries007/tfc/mixin/FallingBlockMixin.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/falling/**"
  - "core/src/test/java/dev/tfp/core/falling/**"
  - "core/src/test/resources/dev/tfp/core/falling/**"
  - "paper/src/main/java/dev/tfp/paper/falling/**"
  - "paper/src/test/java/dev/tfp/paper/falling/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:rock/gravel/granite (in the air)"
  - "screenshot after it lands"
plan:
  - "Spawn vanilla FallingBlock entities carrying the CE block state (CE falling block support or our PDC + land listener); landing places the CE state, breaks blocks it can fall through (canFallThrough), damages entities as TFC."
  - "Ore overlay (2026-10-22): a falling block carrying a tfp ore state calls `OreOverlays.attachToFalling` (PAR-INFRA-16) so the ore keeps its overlay while it falls."
tests:
  - "paper: MockBukkit land places the CE state"
done_criteria:
  - "A CE gravel block falls and lands as itself"
---

# PAR-PHYS-01: TFC falling blocks: canFallThrough, landing, damage

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
