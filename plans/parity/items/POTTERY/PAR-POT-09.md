---
id: PAR-POT-09
title: "Bowls and powder bowls (wooden and ceramic bowl blocks)"
area: POTTERY
status: open
wave: 27
deps: [PAR-INFRA-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BowlBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/component/Bowl.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/bowl/**"
  - "core/src/test/java/dev/tfp/core/bowl/**"
  - "core/src/test/resources/dev/tfp/core/bowl/**"
  - "paper/src/main/java/dev/tfp/paper/bowl/**"
  - "paper/src/test/java/dev/tfp/paper/bowl/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bowl/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bowl/**"
  - "content/generated/content/craftengine/tfp/configuration/bowl/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/bowl"
  - "place; add salt powder; screenshot"
plan:
  - "Placed bowls hold powders (for salting, crafting) as TFC; bowl item component for soups/salads."
tests:
  - "paper: place/take"
done_criteria:
  - "A ceramic bowl placed holds salt"
---

# PAR-POT-09: Bowls and powder bowls (wooden and ceramic bowl blocks)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
