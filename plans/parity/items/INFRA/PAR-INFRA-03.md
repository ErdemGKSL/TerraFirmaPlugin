---
id: PAR-INFRA-03
title: "GUI framework: textured chest GUIs, title glyphs, buttons, progress bars, alignment"
area: INFRA
status: done
wave: 2
deps: [PAR-INFRA-01]
size: M
aliases: ["X-GUI-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/TFCContainerScreen.java"
  - "src/main/java/net/dries007/tfc/client/screen/BlockEntityScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/BlockEntityContainer.java"
  - "src/main/java/net/dries007/tfc/common/container/ButtonHandlerContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/button"
  - "src/main/resources/assets/tfc/textures/gui"
files_owned:
  - "core/src/main/java/dev/tfp/core/gui/**"
  - "core/src/test/java/dev/tfp/core/gui/**"
  - "core/src/test/resources/dev/tfp/core/gui/**"
  - "paper/src/main/java/dev/tfp/paper/gui/**"
  - "paper/src/test/java/dev/tfp/paper/gui/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/gui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/gui/**"
  - "docs/gui/catalogue.md"
ui: true
in_game_steps:
  - "tfp debug gui sample"
  - "Screenshot at GUI scale 2 and 3; slots and background line up to the pixel"
plan:
  - "Done 2026-10-22 (8d3513a cherry-picked; BootstrapTest and CeYamlCheck made shared hubs); still pending: the PROTOCOL §6 screenshot check at GUI scales 1 to 4."
  - "Build the framework pieces of docs/gui/catalogue.md section 2: `fw-title-glyphs` (CE `<image>`/`<shift>` title builder), `fw-title-refresh` (rate-limited `InventoryView#setTitle`), `fw-overlay-layers`, `fw-progress-bars` (15x5 temperature bar, tank, progress arrows as glyph frames), `fw-slot-layouts`, `fw-buttons` (button slot items with empty item model + tooltip), `fw-alignment` (pixel math at GUI scale 1-4), `fw-background-art` (datagen copies TFC `textures/gui/*.png` into CE images + font)."
  - "Java API: `TfpGui` (builder: background image, slots map, buttons, overlays), `GuiSession` (per viewer, refresh throttled to 4/s), click routing on the player's entity scheduler, slot predicates from DeviceInventory."
  - "One sample screen (a 9-slot storage) behind `tfp debug gui sample` to prove alignment."
  - "Keep the catalogue's status column current."
tests:
  - "paper: alignment math unit tests (x offsets per slot vs the TFC screen coordinates)"
  - "tools/datagen: every GUI image referenced exists and has the expected size"
  - "paper: MockBukkit click routing and shift-click predicate tests"
done_criteria:
  - "`tfp debug gui sample` shows a textured GUI aligned at scales 1-4 on 1.21.11"
  - "docs/gui/catalogue.md framework rows marked built"
---

# PAR-INFRA-03: GUI framework: textured chest GUIs, title glyphs, buttons, progress bars, alignment

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
