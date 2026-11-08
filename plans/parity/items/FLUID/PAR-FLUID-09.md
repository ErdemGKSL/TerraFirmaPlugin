---
id: PAR-FLUID-09
title: "Bucket in-game verification: fill a wooden bucket from a river and place it"
area: FLUID
status: done
wave: 10
deps: [PAR-FLUID-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java"
  - "src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java"
files_owned:
  - "docs/verification/PAR-FLUID-09/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wooden_bucket; screenshot the hotbar slot (empty bucket look)"
  - "stand Bot at a river or the testroom water pool (spikes/client/testroom.sh), survival, looking at a water source within 4 blocks; right-click (xdotool click 3); screenshot the held bucket and hover its tooltip (`1000 / 1000 mB of Water`)"
  - "look at the ground next to the pool; right-click; screenshot the placed water (a short flowing block that dries up) and the emptied bucket"
  - "repeat in an ocean: the tooltip reads Salt Water"
  - "ce item give Bot tfp:metal/bucket/red_steel; right-click a lava source; screenshot (red steel bucket with the lava layer)"
plan:
  - "In-game check of PAR-FLUID-02 (PROTOCOL §6), split off because the PAR-FLUID-02 worker had no subagent tool to delegate the headless server and client run to (CLAUDE.md: test runs are delegated). Run the steps on Paper 1.21.11 with the headless client (docs/testing/headless.md). A failure is fixed in PAR-FLUID-02's files (`core/.../bucket/**`, `paper/.../bucket/**`, `tools/datagen/.../bucket/**`) through a follow-up."
  - "Done: in-game check passed on Paper 1.21.11 with the 1.21.11 client (river fill removes the source and shows the water layer and `1000 / 1000 mB of Water`, placement gives a transient level-7 flowing block and an empty-looking bucket, ocean gives Salt Water, red steel takes lava with the lava layer); evidence and verdict in `docs/verification/PAR-FLUID-09/`. Defect found: the bucket double-use guard uses World.getFullTime (day time), so buckets stop working with the daylight cycle off; follow-up PAR-FLUID-13. No code changed; `:core:test :paper:test :tools:datagen:test` green."
tests:
  - "none (in-game check only)"
done_criteria:
  - "Filling a wooden bucket from a river: the source disappears, the bucket shows the blue fluid layer and the tooltip `1000 / 1000 mB of Water`"
  - "Emptying it on the ground places a transient flowing water block and the bucket looks empty again"
---

# PAR-FLUID-09: Bucket in-game verification: fill a wooden bucket from a river and place it

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
