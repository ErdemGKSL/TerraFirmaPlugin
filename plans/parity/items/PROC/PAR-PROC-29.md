---
id: PAR-PROC-29
title: "Scraping block CE entry: tfc:scraping hitbox row and demand, configuration/scraping, in-game check"
area: PROC
status: open
wave: 8
deps: [PAR-PROC-24]
size: S
aliases: ["follow-up of PAR-PROC-05 (its CraftEngine block entry and its in_game_steps)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/client/model/ScrapingBlockModel.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/scraping/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/scraping/**"
  - "content/generated/content/craftengine/tfp/configuration/scraping/**"
  - "docs/verification/PAR-PROC-05/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:unrefined_paper; ce item give Bot a knife (tfp:metal/knife/copper or tfp:stone/knife/igneous_extrusive; needs PAR-PROC-24); give Bot a log"
  - "place the log; right-click its top face with the unrefined paper: one item goes onto the log and the 1 px surface shows the unrefined paper texture (screenshot, GUI scale 2, look from above and at 35 deg)"
  - "click 16 cells with the knife (aim at each cell centre): each click turns that 4 x 4 px tile into the paper texture (screenshot after 1, 8 and 16 cells; 8x crop of the half-scraped surface); the knife loses 16 durability; break the block: minecraft:paper drops"
  - "wax: right-click a fresh surface with minecraft:honeycomb, then red dye and blue dye: unscraped tiles red, scraped tiles blue (screenshot); the knife no longer scrapes it"
  - "break the log under a surface: the surface and its display go, the item drops"
  - "relog and restart the server with a half-scraped surface: the display comes back with the same cells"
  - "Pending (PAR-PROC-05): once PAR-FAUNA-17 ships the hides, repeat with tfp:medium_soaked_hide (result tfp:medium_scraped_hide) and tfp:medium_sheepskin_hide (result raw hide + tfp:wool drop); until then the hide steps are not run"
plan:
  - "Deferred from PAR-PROC-05 (PROTOCOL 3a + 6.5): the runtime is done and tested (core/scraping, paper/scraping, datagen/scraping ScrapingArt); the CraftEngine block tfp:scraping is missing because its carrier must come from the selector (HardcodedCarrierTest)."
  - "Hitbox: add a TfcShapes Spec for tfc:scraping (ScrapingBlock SHAPE = box(0, 0, 0, 16, 1, 16), selection = collision) and regenerate tfc-shapes.json (TfcShapesTest compares it with /opt/tfc); add the demand Demand(\"tfp:scraping\", \"base\", \"tfc:scraping\", 0, HitboxAllocation.TRANSPARENT, true, 1) to HitboxDemands (the look is the item display drawn by PaperScrapingEffects); add tfp:scraping to the pinned block ids of GeneratedCarrierTest."
  - "Block entry: ScrapingArt (or a sibling in datagen/scraping) writes configuration/scraping/scraping.yml through DeviceBlockSpec + DeviceYaml.templated: one appearance on HitboxAllocation.agreed(\"tfp:scraping\", \"base\"), TFC settings read from TFCBlocks (strength 0.2, SoundType.STEM, noOcclusion), no block item (registerNoItem) and no loot of its own (TFC expectedNoLootTableBlocks: the device framework drops the slot). Add the section to CeYamlCheck if it is new."
  - "Run every in_game_step at GUI scale 2 and 3, store crops and verdict.md in docs/verification/PAR-PROC-05/."
tests:
  - "datagen: tfc:scraping shape row, the carrier from the selector, the generated scraping.yml loads in CeYamlCheck"
done_criteria:
  - "Scraping unrefined paper on a log yields paper in game, the surface shows each scraped tile (screenshots, docs/verification/PAR-PROC-05/verdict.md)"
  - "Scraping a soaked hide yields a scraped hide in game (once PAR-FAUNA-17 is done; pending line)"
---

# PAR-PROC-29: Scraping block CE entry: tfc:scraping hitbox row and demand, configuration/scraping, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
