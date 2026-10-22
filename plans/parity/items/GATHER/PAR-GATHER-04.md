---
id: PAR-GATHER-04
title: "Leaves: slow decay, movement slowdown, fallen leaves; plant movement slowdown"
area: GATHER
status: open
wave: 22
deps: [PAR-WORLD-10, PAR-INFRA-11]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ISlowEntities.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/leaves/**"
  - "core/src/test/java/dev/tfp/core/leaves/**"
  - "core/src/test/resources/dev/tfp/core/leaves/**"
  - "paper/src/main/java/dev/tfp/paper/leaves/**"
  - "paper/src/test/java/dev/tfp/paper/leaves/**"
ui: true
in_game_steps:
  - "fell a tree (PAR-GATHER-03)"
  - "tfp time add days 2"
  - "screenshot"
plan:
  - "Leaves without a log nearby decay lazily (catch-up kind) as TFC; entities moving through leaves/plants are slowed (PlayerMoveEvent velocity scaling, throttled; divergence: server-side only)."
tests:
  - "core: decay distance golden"
done_criteria:
  - "Leaves of a felled tree decay over time; walking in leaves is slower"
---

# PAR-GATHER-04: Leaves: slow decay, movement slowdown, fallen leaves; plant movement slowdown

> see_not_parity: NP-PART-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
