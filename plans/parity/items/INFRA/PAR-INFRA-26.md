---
id: PAR-INFRA-26
title: "Neighbour runner self-test: neighbour.converge on Paper and Folia"
area: INFRA
status: open
wave: 5
deps: [PAR-INFRA-10]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/NeighbourConvergeCase.java"
  - "itest/src/test/java/dev/tfp/itest/SelfTestSuiteTest.java"
  - "paper/src/main/java/dev/tfp/paper/neighbour/NeighbourSelfCheck.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-10 built the neighbour-update runner (`core/neighbour`, `paper/neighbour`, module `neighbour`, service `NeighbourService`) and the self-test body `NeighbourSelfCheck.run(service, scheduler, world)` with its test rule `tfp:selftest/bone_axis` (vanilla bone blocks: axis=x next to another bone block east/west, else y; provisional y). The case itself needs `itest/.../cases/` and `SelfTestSuiteTest` (its case count), outside that item's files_owned."
  - "Add `NeighbourConvergeCase` (`neighbour.converge`): look up `NeighbourService` through `tfp.moduleServices()`, return `NeighbourSelfCheck.run(...)` for the first world; add it to `SelfTestSuite` (hub) and update `SelfTestSuiteTest` (count, name prefix). If `SelfTestSuiteTest` is held by another open item (PAR-INFRA-25 also owns it), take it after that item or coordinate the count."
  - "Run `:itest:runSelfTest` on Paper 1.21.11 and Folia 1.21.11 (delegated per PROTOCOL 5.3). If Paper does not fire `BlockPhysicsEvent` for the notified neighbours of a `setType(AIR, true)`, fix the second phase of `NeighbourSelfCheck` (e.g. break through a fake player) rather than the runner."
tests:
  - "itest: neighbour.converge passes on Paper 1.21.11 and Folia 1.21.11"
done_criteria:
  - "One rule (the bone-axis test rule) converges on Paper and Folia with one commit per changed block (self-test `neighbour.converge`)"
---

# PAR-INFRA-26: Neighbour runner self-test: neighbour.converge on Paper and Folia

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
