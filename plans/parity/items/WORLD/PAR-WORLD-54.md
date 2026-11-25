---
id: PAR-WORLD-54
title: "Bubble columns above the CE magma blocks: drag-down columns that survive vanilla's support check, and the hot spring tracker"
area: WORLD
status: open
wave: 30
deps: [PAR-WORLD-45]
size: M
aliases: ["PAR-WORLD-45 follow-up: TFCMagmaBlock/TFCBubbleColumnBlock behaviour on the CE magma block"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCMagmaBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBubbleColumnBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/magma/**"
  - "paper/src/test/java/dev/tfp/paper/magma/**"
  - "paper/src/main/java/dev/tfp/paper/worldwater/hot/HotSpringTracker.java"
ui: true
in_game_steps:
  - "Dive over a flooded caldera magma block: a drag-down bubble column forms above it within a second and pulls the player down; screenshot at GUI scale 2"
plan:
  - "Problem: the CE magma block's server state is not vanilla magma, so vanilla neither forms a bubble column over it nor keeps one (BubbleColumnBlock.canSurvive and its tick read MAGMA_BLOCK below). TFC schedules the column 20 ticks after a water neighbour change (TFCMagmaBlock.updateShape/tick, TFCBubbleColumnBlock.updateColumnForFluid)."
  - "Decide a design: (a) our own region-thread 'column keeper' for columns above CE magma (decide async from a snapshot, apply sync under the budget; BlockPhysicsEvent and fluid events cancel vanilla's removal of columns whose support is CE magma), or (b) a CE entity_renderer bubble visual with a damage-free drag effect. Then make HotSpringTracker.inTfcBubbleColumn accept the result."
tests:
  - "paper: MockBukkit test of the column decision over a fake CE magma id"
done_criteria:
  - "Drag-down bubble columns stand over flooded CE magma and drag the player down, in-game check done"
---

# PAR-WORLD-54: Bubble columns above the CE magma blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
