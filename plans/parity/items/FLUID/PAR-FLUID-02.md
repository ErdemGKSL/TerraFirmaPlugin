---
id: PAR-FLUID-02
title: "Buckets: wooden bucket, red and blue steel buckets, fluid placement policy"
area: FLUID
status: open
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
ui: true
in_game_steps:
  - "ce item give Bot tfp:wooden_bucket; right-click water; place"
plan:
  - "Wooden bucket (1000 mB, places water only if configured), red steel (lava), blue steel; vanilla bucket behaviour on TFC fluids; in-world placement of non-vanilla fluids refused (divergence: closest vanilla water/lava)."
tests:
  - "paper: MockBukkit fill/place"
done_criteria:
  - "Filling a wooden bucket from a river"
---

# PAR-FLUID-02: Buckets: wooden bucket, red and blue steel buckets, fluid placement policy

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
