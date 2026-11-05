---
id: PAR-PROC-01
title: "Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn"
area: PROC
status: done
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
ui: false
in_game_steps: []
plan:
  - "Right-click with 2 loose rocks / 5 clay / leather+knife / goat horn opens the 6-row chest GUI: 25 button slots in columns 2-6 (index x+5y), output slot; KnappingSession drives clicks, output appears when a recipe matches; consumption on close/take as TFC; textures from TFC gui/knapping/*.png."
  - "Layout note: the grid uses chest columns 1 to 5 (not 2 to 6) and the output chest slot 26 (column 8, row 2), so TFC's arrow fits between them; the art moves by (24, 8) with the output slot."
  - "Done: core/knap/KnappingScreen (6-row chest layout, cell overlays, frames), KnappingSession.stillValid, KnappingDefinitions.resultTemplate; paper/knap KnappingModule, KnappingMenu, KnappingListener (right-click air with the main hand stack, button cells, output take incl. shift-click hotbar-first, close returns and consumes, input slot locked, creative and spectator); datagen/knap/KnappingArt (knapping.png re-laid out + 47 textures x 5 row images, checked against TFC sources). Tests: core KnappingScreenTest (7), paper KnappingMockBukkitTest (10), datagen KnappingArtTest (4); in-game check deferred to PAR-PROC-20 (the axe head output needs PAR-TOOL-01)."
tests:
  - "paper: MockBukkit session click -> output -> take consumes"
  - "core: KnappingGoldenTest exists"
done_criteria:
  - "Deferred check tracked in PAR-PROC-20"
---

# PAR-PROC-01: Knapping GUI: 5x5 grid for stone, clay, fire clay, leather and goat horn

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
