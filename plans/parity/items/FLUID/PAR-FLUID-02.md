---
id: PAR-FLUID-02
title: "Buckets: wooden bucket, red and blue steel buckets, fluid placement policy"
area: FLUID
status: done
wave: 9
deps: [PAR-FLUID-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java"
  - "src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/bucket/**"
  - "core/src/test/java/dev/tfp/core/bucket/**"
  - "core/src/test/resources/dev/tfp/core/bucket/**"
  - "paper/src/main/java/dev/tfp/paper/bucket/**"
  - "paper/src/test/java/dev/tfp/paper/bucket/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bucket/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bucket/**"
ui: false
in_game_steps: []
plan:
  - "Wooden bucket (1000 mB, places water only if configured), red steel (lava), blue steel; TFC fluid buckets empty through the emptyFluidFrom rules with sources on; in-world placement of non-vanilla fluids refused (divergence: closest vanilla water/lava)."
  - "In-game check moved to PAR-FLUID-09 (steps and criterion there): this worker had no subagent tool to delegate the headless server and client run to (CLAUDE.md delegates test runs). The `buckets:` config keys and the bucket look after fills outside the bucket listener (debug fill, block tanks) are PAR-FLUID-08."
  - "Done: core `bucket/BucketKind`, `BucketConfig` (TFC defaults: no source blocks), `WorldFluid` (water/lava pickup; the water kind from PAR-FLUID-03's `WorldWaterService`, else ocean = salt water; water-family fluids place as vanilla water, lava as lava, others refused), `BucketRules` (pickup, TFC `emptyFluidFrom` branch for branch, `FluidHelpers.with` hand update); paper `bucket/BucketModule`, `BucketListener` (source-only ray in the interaction range, PlayerBucketFill/EmptyEvent for protection plugins, TFC sounds, creative and stack split, TFC fluid buckets place a source and return a vanilla bucket, vanilla bucket filled from salt water gives `tfp:bucket/salt_water`), `BucketVisuals` (custom_model_data flag + fluid colour); datagen `bucket/BucketItems` (3 tank buckets with a condition model + 66 `tfp:bucket/<fluid>` items, TFC names). Tests: core BucketRulesTest 13, paper BucketListenerMockBukkitTest 13, datagen BucketItemsTest 4. Ledger: feature row + 6 divergences. Verification: PAR-FLUID-09."
  - "Unblock round: both reviews accepted; review notes applied (ledger wording on TFC's vanilla BucketItem and the differences from BucketItem.use, water kinds worded as PAR-FLUID-03 says, listener javadoc). Spring-water vanilla bucket and the waterlogged ocean rule split off to PAR-FLUID-10. PAR-FLUID-09 needs a free server slot (the earlier capture was blocked by another agent's wedged server and a nearly full disk)."
tests:
  - "paper: MockBukkit fill/place"
done_criteria:
  - "Deferred check tracked in PAR-FLUID-09"
---

# PAR-FLUID-02: Buckets: wooden bucket, red and blue steel buckets, fluid placement policy

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
