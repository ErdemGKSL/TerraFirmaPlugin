---
id: PAR-DECO-04
title: "Chests, trapped chests and barrel racks per wood"
area: DECO
status: open
wave: 46
deps: [PAR-DECO-01, PAR-SURV-06, PAR-INFRA-13]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCTrappedChestBlock.java"
  - "src/main/java/net/dries007/tfc/common/container/RestrictedChestContainer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/chest/**"
  - "core/src/test/java/dev/tfp/core/chest/**"
  - "core/src/test/resources/dev/tfp/core/chest/**"
  - "paper/src/main/java/dev/tfp/paper/chest/**"
  - "paper/src/test/java/dev/tfp/paper/chest/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/chest/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/chest/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/chest/oak 2; place side by side; open"
plan:
  - "Per-wood chests on vanilla chest carriers (double chests work) with TFC textures; size restriction via PAR-SURV-06."
tests:
  - "paper: restriction"
done_criteria:
  - "A TFC oak double chest opens"
---

# PAR-DECO-04: Chests, trapped chests and barrel racks per wood

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
