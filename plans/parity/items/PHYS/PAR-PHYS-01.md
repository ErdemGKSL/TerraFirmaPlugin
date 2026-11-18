---
id: PAR-PHYS-01
title: "TFC falling blocks: canFallThrough, landing, damage"
area: PHYS
status: done
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
  - "Bot stands on open flat ground in a fresh scratch world; note its block position X Y Z (Y = the block it stands in). Do not run setblock or fill anywhere near the landing spot during the check."
  - "Set GUI scale 2; face the landing spot <X+2> <Y> <Z> (look about 30 degrees down toward +X)"
  - "tfp debug fall <X+2> <Y+10> <Z> tfp:rock/gravel/granite (spawns the falling CE gravel 10 blocks up; `debug place` replaces the targeted block and cannot spawn in the air)"
  - "screenshot while it falls (about 0.5 s after the command) and after it lands (3 s)"
  - "over RCON: tfp debug fall stats (expect spawned 1, tracking 0, placed 1) and tfp debug fall last (expect `<world> <X+2> <Y> <Z>: PLACE_AT carried tfp:rock/gravel/granite, now tfp:rock/gravel/granite`; it reads the landed block without a crosshair and without changing it)"
  - "Set GUI scale 3 and repeat the spawn one block further (<X+3> <Y+10> <Z>), the screenshots and `tfp debug fall last`"
plan:
  - "Spawn vanilla FallingBlock entities carrying the CE block state (CE falling block support or our PDC + land listener); landing places the CE state, breaks blocks it can fall through (canFallThrough), damages entities as TFC."
  - "Ore overlay (2026-10-22): a falling block carrying a tfp ore state calls `OreOverlays.attachToFalling` (PAR-INFRA-16) so the ore keeps its overlay while it falls."
  - "Note (2026-11-18): no `attachToFalling` call is needed: PAR-INFRA-16's listener attaches the riders on the `EntitySpawnEvent` of any FallingBlock whose block data is an ore state, and ours carries the CE server state."
  - "Repair (2026-11-18): the first in-game run could not confirm the landed block (`tfp debug block` needs a crosshair, RCON has none; the landing was then removed by a manual setblock). Added `/tfp debug fall last`: the last 8 landings with the state at each landing block now, read on its region without changing it; in_game_steps use it and add GUI scale 3."
  - "Done: PAR-PHYS-01 commit (core `dev.tfp.core.falling`: FallCell, Landing, FallRules; paper `dev.tfp.paper.falling`: FallingBlocks service, BukkitFallWorld, FallingListener, FallingModule with `/tfp debug fall`). Tests: core FallRulesTest (16), paper FallingBlocksMockBukkitTest (13, incl. `debug fall last` reads the landed state); ./gradlew check passes. 4 divergences in the ledger. In-game screenshot check left to the verify stage (no Haiku agent available in the implementing session); evidence goes to docs/verification/PAR-PHYS-01/."
tests:
  - "paper: MockBukkit land places the CE state"
done_criteria:
  - "A CE gravel block falls and lands as itself"
---

# PAR-PHYS-01: TFC falling blocks: canFallThrough, landing, damage

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
