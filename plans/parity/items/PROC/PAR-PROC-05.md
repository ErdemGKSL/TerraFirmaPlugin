---
id: PAR-PROC-05
title: "Scraping hides on a log"
area: PROC
status: open
wave: 7
deps: [PAR-INFRA-02]
size: S
aliases: ["M3-PROC-02 (scraping)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ScrapingBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/scraping"
files_owned:
  - "core/src/main/java/dev/tfp/core/scraping/**"
  - "core/src/test/java/dev/tfp/core/scraping/**"
  - "core/src/test/resources/dev/tfp/core/scraping/**"
  - "paper/src/main/java/dev/tfp/paper/scraping/**"
  - "paper/src/test/java/dev/tfp/paper/scraping/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/scraping/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/scraping/**"
  - "content/generated/content/craftengine/tfp/configuration/scraping/**"
  - "core/src/main/java/dev/tfp/core/craft/Scraping*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:soaked_hide/medium"
  - "place on a log; click 16 cells with a knife"
plan:
  - "Place a soaked hide on a log -> scraping block with 16-cell grid visual (in-progress texture as entity_renderer overlay); knife clicks per cell via the hit position (ScrapingRules)."
tests:
  - "paper: hit -> cell mapping"
done_criteria:
  - "Scraping a soaked hide yields a scraped hide"
---

# PAR-PROC-05: Scraping hides on a log

> see_not_parity: NP-DATA-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
