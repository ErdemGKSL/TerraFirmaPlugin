---
id: PAR-INFRA-10
title: "Neighbour-update runner: block shape updates decided async, applied sync"
area: INFRA
status: open
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
tests:
  - "paper: fake-clock test: a chain of 10 placements converges with one commit per changed block"
  - "paper: thread assertion test (decision off-thread, commit on owner region)"
done_criteria:
  - "One rule (a test rule in itest) converges on Paper and Folia (self-test `neighbour.converge`)"
---

# PAR-INFRA-10: Neighbour-update runner: block shape updates decided async, applied sync

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
