---
id: PAR-PROC-13
title: "Sluice"
area: PROC
status: open
wave: 22
deps: [PAR-PROC-12, PAR-INFRA-02]
size: M
aliases: ["M3-PAN-01 (sluice)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/sluice/**"
  - "core/src/test/java/dev/tfp/core/sluice/**"
  - "core/src/test/resources/dev/tfp/core/sluice/**"
  - "paper/src/main/java/dev/tfp/paper/sluice/**"
  - "paper/src/test/java/dev/tfp/paper/sluice/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/sluice/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/sluice/**"
  - "content/generated/content/craftengine/tfp/configuration/sluice/**"
  - "core/src/main/java/dev/tfp/core/pan/Sluice*"
  - "core/src/test/java/dev/tfp/core/pan/Sluice*"
ui: true
in_game_steps:
  - "place a sluice with water flowing"
  - "throw 4 deposit items in; wait; screenshot output"
plan:
  - "Two-block sluice with flowing water; soil items thrown in are consumed and roll deposit loot over time (SluiceRules), lazy catch-up from the item intake timestamps."
tests:
  - "core: SluiceRulesTest exists"
  - "paper: catch-up"
done_criteria:
  - "A sluice processes gravel into loot in-game"
---

# PAR-PROC-13: Sluice

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
