---
id: PAR-PROC-10
title: "Log pile and charcoal pit"
area: PROC
status: open
wave: 13
deps: [PAR-INFRA-02, PAR-INFRA-10, PAR-HEAT-05, PAR-INFRA-13]
size: M
aliases: ["M3-PROC-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/charcoal/**"
  - "core/src/test/java/dev/tfp/core/charcoal/**"
  - "core/src/test/resources/dev/tfp/core/charcoal/**"
  - "paper/src/main/java/dev/tfp/paper/charcoal/**"
  - "paper/src/test/java/dev/tfp/paper/charcoal/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/charcoal/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/charcoal/**"
  - "content/generated/content/craftengine/tfp/configuration/charcoal/**"
ui: true
in_game_steps:
  - "sneak-place 16 logs"
  - "cover with dirt, light top"
  - "tfp time add hours 20"
  - "dig in; screenshot"
plan:
  - "Sneak-place logs to build a log pile (16 logs/block, inventory); light with fire; insulation check (all sides covered) via the neighbour runner; lazy conversion to charcoal piles (layers) via CharcoalPit; neighbour sweep with the 1-in-7 draw."
tests:
  - "paper: MockBukkit conversion after charcoalTicks"
  - "core: CharcoalPitGoldenTest exists"
done_criteria:
  - "A covered lit log pile becomes charcoal in-game"
---

# PAR-PROC-10: Log pile and charcoal pit

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
