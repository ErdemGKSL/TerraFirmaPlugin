---
id: PAR-PROC-05
title: "Scraping hides on a log"
area: PROC
status: done
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
ui: false
in_game_steps: []
plan:
  - "Place a soaked hide on a log -> scraping block with 16-cell grid visual (in-progress texture as entity_renderer overlay); knife clicks per cell via the hit position (ScrapingRules)."
  - "Follow-up PAR-PROC-29 (PROTOCOL 3a + 6.5): the CraftEngine block entry tfp:scraping needs a hitbox carrier, which only the selector may choose (HardcodedCarrierTest, GeneratedCarrierTest): a tfc:scraping row in hitbox/tfc-shapes.json (TfcShapes evaluator, ScrapingBlock SHAPE 16x1x16) and a HitboxDemands entry, both outside this item's files. PAR-PROC-29 adds them, writes configuration/scraping/scraping.yml (tfp:device behaviour, TFC strength 0.2, stem sounds, no loot of its own) and runs the in-game steps moved there (ce item give Bot tfp:unrefined_paper, later the hides of PAR-FAUNA-17; place on a log; click 16 cells with a knife)."
  - "Visual: an entity_renderer follows block states and cannot hold 2^16 masks, so the surface is one persistent item display per block whose 1.21.4 item model definition picks each cell's input or output tile on custom_model_data flags and tints them with its colours (ledger divergence)."
  - "Done: core/scraping (ScrapingState: mask, waxed, two dyes, kept look, codec; ScrapingAim: TFC calculatePoint + clamped cell; ScrapingVisuals: shared model names and flags), paper/scraping (ScrapingModule, ScrapingDevice kind tfp:scraping on the device framework, ScrapingListener placement on the top face of tfc:scraping_surface, ScrapingSupport rule on the neighbour runner, PaperScrapingEffects display/particles/wear/extra drop), datagen/scraping (ScrapingArt: 14 textures, 16 parents + 224 tiles, 7 item model definitions; one Datagen run line). Tests: core ScrapingStateTest 5, ScrapingAimTest 4, ScrapingVisualsTest 3; paper ScrapingMockBukkitTest 8 (hit -> cell mapping from four eyes, soaked hide -> scraped hide in 16 clicks, sheepskin wool drop, wax + dyes, creative, placement rules, break drop, support rule), PaperScrapingEffectsMockBukkitTest 4 (tints, marker, the spawned display at yaw SURFACE_YAW = -180 so cell 0 lands at min x / min z against the item display renderer's pi turn, reuse and re-turn of a marked display with duplicates removed); datagen ScrapingArtTest 5. Ledger: feature row + 4 divergences. In-game check deferred to PAR-PROC-29 (orientation step added there; the knife's missing tfp:mineable/knife tag at CE item load is PAR-PROC-24's, a dep of PAR-PROC-29). Editing PAR-PROC-29.md is allowed here as the follow-up item this item created (PROTOCOL 3a)."
tests:
  - "paper: hit -> cell mapping"
done_criteria:
  - "Scraping a soaked hide yields a scraped hide"
  - "Deferred check tracked in PAR-PROC-29"
---

# PAR-PROC-05: Scraping hides on a log

> see_not_parity: NP-DATA-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
