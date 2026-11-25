---
id: PAR-PHYS-02
title: "Collapse runner: mining triggers collapses decided async, applied as falling blocks"
area: PHYS
status: done
wave: 17
deps: [PAR-PHYS-01, PAR-INFRA-10, PAR-INFRA-16]
size: M
aliases: ["M3-PHYS-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/Collapse.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java"
  - "src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java"
  - "src/main/java/net/dries007/tfc/util/data/Support.java"
  - "src/generated/resources/data/tfc/recipe/collapse"
files_owned:
  - "core/src/main/java/dev/tfp/core/collapse/**"
  - "core/src/test/java/dev/tfp/core/collapse/**"
  - "core/src/test/resources/dev/tfp/core/collapse/**"
  - "paper/src/main/java/dev/tfp/paper/collapse/**"
  - "paper/src/test/java/dev/tfp/paper/collapse/**"
ui: true
in_game_steps:
  - "dig a 5x5 room in raw rock without beams; mine a ceiling block"
  - "screenshot falling cobble"
plan:
  - "On BlockBreakEvent of #can_trigger_collapse with a pickaxe: snapshot the support area, CollapseRules decides (real/fake chance, radius) off-thread, the commit queue turns raw rock into falling cobble per collapse recipe in budgeted batches; WorldTracker propagation ticks become delayed decisions."
  - "Folia: the collapse area is clipped to the owning region; neighbours in other regions get a scheduled follow-up."
  - "Ore overlay (2026-10-22): collapses turn rich -> normal -> poor ore into falling blocks (TFC collapse recipes); overlays follow through the block-change packets and the falling-block attachment of PAR-INFRA-16; check no overlay is left in the air after a collapse."
  - "Pending in-game check (container cannot run client). Moved with the itest `collapse.stress` case (itest/cases is not owned here), TFC's rock-slide sounds (datagen) and the collapse config keys to PAR-PHYS-07."
  - "Done: PAR-PHYS-02 commit. core/collapse: CollapseRules.drawRadius, start (TFC startCollapse column pass), propagate (WorldTracker collapse step, sorted next positions), propagationDelay (nextInt(10) == 0 per tick), InProgress/Phase, the propagate/radius defaults. paper/collapse: CollapseModule, CollapseListener (BlockBreakEvent, any game mode), CollapseService (trigger snapshot in the event incl. support reach, decide on the CPU pool, fake sound + particles on the owner, real start snapshot read in 2048-block slices on each chunk owner, per-world RegionCommitQueue under 1 ms, delayed propagation after the commits; cross-region reads and writes on their owners), CollapseBlocks, BukkitCollapseWorld (tags, tfc:collapse recipes, full collision shape, FallingBlocks with 2/20 damage), CollapseSupports (TFC support definitions, overridable for PAR-PHYS-04). Tests: core CollapseRunGoldenTest (4, 60 oracle worlds), paper CollapseMockBukkitTest (10, fake clock), CollapseContentDataMockBukkitTest (2). Ledger row + 7 divergences. ./gradlew check: the collapse tests pass; failures elsewhere are pre-existing (license headers of animal/familiarity/spawn files, tr_tr lang keys, recipe counts, BootstrapTest debug literals, SelfTestSuiteTest count, seasonal biome datapack)."
tests:
  - "core: CollapseGoldenTest exists"
  - "paper: fake-clock collapse commits"
  - "itest: Folia stress `collapse.stress`"
done_criteria:
  - "Mining under unsupported raw rock causes a collapse in-game; support beams prevent it"
---

# PAR-PHYS-02: Collapse runner: mining triggers collapses decided async, applied as falling blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
