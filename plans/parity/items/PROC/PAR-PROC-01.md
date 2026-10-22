---
id: PAR-PROC-01
title: "Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn"
area: PROC
status: open
wave: 5
deps: [PAR-INFRA-03]
size: M
aliases: ["M3-KNAP-01", "gaps: knapping has no player screen"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/container/KnappingContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/KnappingScreen.java"
  - "src/main/java/net/dries007/tfc/util/data/KnappingType.java"
  - "src/generated/resources/data/tfc/recipe/knapping"
  - "src/generated/resources/data/tfc/tfc/knapping_type"
files_owned:
  - "core/src/main/java/dev/tfp/core/knap/**"
  - "core/src/test/java/dev/tfp/core/knap/**"
  - "core/src/test/resources/dev/tfp/core/knap/**"
  - "paper/src/main/java/dev/tfp/paper/knap/**"
  - "paper/src/test/java/dev/tfp/paper/knap/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/knap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/knap/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:rock/loose/granite 2"
  - "right-click air"
  - "click the axe pattern; screenshot; take output"
plan:
  - "Right-click with 2 loose rocks / 5 clay / leather+knife / goat horn opens the 6-row chest GUI: 25 button slots in columns 2-6 (index x+5y), output slot; KnappingSession drives clicks, output appears when a recipe matches; consumption on close/take as TFC; textures from TFC gui/knapping/*.png."
tests:
  - "paper: MockBukkit session click -> output -> take consumes"
  - "core: KnappingGoldenTest exists"
done_criteria:
  - "Knapping an axe head from two granite loose rocks in-game"
---

# PAR-PROC-01: Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
