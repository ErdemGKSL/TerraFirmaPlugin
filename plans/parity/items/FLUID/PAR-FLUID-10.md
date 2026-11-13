---
id: PAR-FLUID-10
title: "Vanilla bucket from a spring pool gives tfp:bucket/spring_water; waterlogged blocks in oceans give fresh water"
area: FLUID
status: done
wave: 11
deps: [PAR-FLUID-02, PAR-FLUID-04]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
  - "src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/bucket/BucketListener.java"
  - "paper/src/test/java/dev/tfp/paper/bucket/BucketSpringWaterMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "Follow-up of PAR-FLUID-02 (review notes). Once PAR-FLUID-04 (or PAR-FLUID-03) tells spring pools apart in `WorldWaterService`, a vanilla empty bucket filled from spring water gives `tfp:bucket/spring_water` (TFC: the spring water source gives `bucket/spring_water`), as `onVanillaFill` already does for salt water. Also: water taken by a wooden/metal bucket from a vanilla waterlogged block reads as fresh `minecraft:water` (TFC: a vanilla waterlogged block holds vanilla water), not by the ocean salt rule; revisit together with PAR-FLUID-07's ocean drinking rule. Remove or update the two PAR-FLUID-02 gap notes in the ledger's pickup divergence row."
  - "Done: BucketListener reads PAR-FLUID-04's SpringWater (module service, looked up lazily when a bucket is filled because the hotspring module enables after the bucket module; BucketModule is not this item's file) for water sources: tank buckets take tfp:spring_water, a vanilla bucket becomes tfp:bucket/spring_water; any vanilla waterlogged block gives minecraft:water (tank and vanilla bucket). Tests: paper BucketSpringWaterMockBukkitTest 7, BucketListenerMockBukkitTest 13 unchanged. Ledger: bucket feature row and the pickup divergence row updated (gap notes removed; TFC's salt-holding fluid-logged sea plants read as fresh here)."
tests:
  - "paper: MockBukkit vanilla bucket on a spring pool; waterlogged block in an ocean biome"
done_criteria:
  - "A vanilla bucket filled from a spring pool becomes tfp:bucket/spring_water (MockBukkit test)"
  - "A tank bucket filled from a waterlogged block in an ocean holds minecraft:water (MockBukkit test)"
---

# PAR-FLUID-10: Vanilla bucket from a spring pool gives tfp:bucket/spring_water; waterlogged blocks in oceans give fresh water

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
