---
id: PAR-INFRA-10
title: "Neighbour-update runner: block shape updates decided async, applied sync"
area: INFRA
status: done
wave: 4
deps: [PAR-INFRA-02]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/neighbour/**"
  - "core/src/test/java/dev/tfp/core/neighbour/**"
  - "core/src/test/resources/dev/tfp/core/neighbour/**"
  - "paper/src/main/java/dev/tfp/paper/neighbour/**"
  - "paper/src/test/java/dev/tfp/paper/neighbour/**"
ui: false
in_game_steps: []
plan:
  - "Current: AsyncDecisionPipeline + RegionCommitQueue exist; nothing listens to neighbour changes for CE blocks."
  - "Listen to BlockPhysicsEvent / CE neighbour callbacks for registered `ShapeRule`s, snapshot the 3x3x3 neighbourhood, decide in a pure `ShapeRule.decide`, commit only real changes under the 1 ms budget, coalescing duplicate requests per position per tick."
  - "Provide a provisional-state helper so placement shows something immediately (CLAUDE.md 'decide async, apply sync')."
  - "Rescoped: the self-test criterion (`neighbour.converge` on Paper and Folia) needs a case in `itest/.../cases/` and the case count in `SelfTestSuiteTest`, outside files_owned; it moved with that criterion to follow-up PAR-INFRA-26. The case body (`NeighbourSelfCheck`, bone-axis test rule) is ready in `paper/neighbour`."
  - "Done: core/neighbour (`ShapeRule` with `blockIds`/`appliesTo`/pure `decide`/`provisional`, immutable `Neighbourhood` 3x3x3, `ShapeRules` registry + pure `decide`), paper/neighbour (`NeighbourRunner`: per-tick per-position coalescing, 3x3x3 `NeighbourSnapshot`, decide on the worker pool, `RegionCommitQueue` commit under the 1 ms budget, each commit requests its 6 neighbours; `placeProvisional`; `WorldBlocks` over CustomBlocks + vanilla block data; `NeighbourService` per-world runners and rule registration; `NeighbourListener` BlockPhysicsEvent/BlockPlaceEvent; `NeighbourModule` (module `neighbour`); `NeighbourSelfCheck`); tests core ShapeRulesTest (5), paper NeighbourRunnerTest (5, fake-clock chain of 10 = one commit per changed block, thread ownership across a region border), NeighbourListenerMockBukkitTest (4); ARCHITECTURE.md 7.3; ledger feature row + 2 divergences; follow-up PAR-INFRA-26."
tests:
  - "paper: fake-clock test: a chain of 10 placements converges with one commit per changed block"
  - "paper: thread assertion test (decision off-thread, commit on owner region)"
done_criteria:
  - "Runner built and covered by core and paper tests: a chain of 10 placements converges with one commit per changed block under the fake-clock budget; decisions off-thread, commits on the owner region"
  - "Self-test criterion (one rule converges on Paper and Folia, `neighbour.converge`): deferred check tracked in PAR-INFRA-26"
---

# PAR-INFRA-10: Neighbour-update runner: block shape updates decided async, applied sync

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
