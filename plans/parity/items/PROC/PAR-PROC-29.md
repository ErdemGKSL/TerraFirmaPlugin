---
id: PAR-PROC-29
title: "Scraping block CE entry: tfc:scraping hitbox row and demand, configuration/scraping, in-game check"
area: PROC
status: blocked
wave: 8
deps: [PAR-PROC-24, PAR-INFRA-42]
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
  - "orientation: on a fresh unrefined paper surface scrape only the north-west cell (min x, min z; F3 facing north, the cell at the far left): the tile in that corner of the surface changes, not the opposite one (screenshot from above, north at the top)"
  - "wax: right-click a fresh surface with minecraft:honeycomb, then red dye and blue dye: unscraped tiles red, scraped tiles blue (screenshot); the knife no longer scrapes it"
  - "break the log under a surface: the surface and its display go, the item drops"
  - "relog and restart the server with a half-scraped surface: the display comes back with the same cells"
  - "Pending (PAR-PROC-05): once PAR-FAUNA-17 ships the hides, repeat with tfp:medium_soaked_hide (result tfp:medium_scraped_hide) and tfp:medium_sheepskin_hide (result raw hide + tfp:wool drop); until then the hide steps are not run"
plan:
  - "Rejected after 2 repairs, set blocked (reviewers' last notes, ae3f14f): fidelity and code reviews accepted the plan-only change (tests core 2597, paper 716, datagen 351, 0 failures; :dist:shadowJar builds). The in-game check was BLOCKED: no server run, no screenshots, because tfp:scraping is not generated (no content/generated/content/craftengine/tfp/configuration/scraping/, nothing in the jar's configuration/ names tfp:scraping; Datagen runs only ScrapingArt; ScrapingBlock's run line is withdrawn pending PAR-INFRA-42). Step 1 items exist (tfp:unrefined_paper, tfp:metal/knife/copper); step 2 onward needs the block. Next worker: wait for PAR-INFRA-42, re-add the run line, rebuild (run tools/worktree/share-build.sh in the worktree first), then run spikes/client/server.sh paper 1.21.11 <run-dir> <port> <jar>, session.sh and the room setup, and run every in_game_step (hide steps stay pending on PAR-FAUNA-17)."
  - "Reviewers' non-blocking notes for the re-add: (1) pressure-plate carrier box is box(1,0,1,15,1,15) = 14 x 1 x 14 at power=0 (14 x 0.5 x 14 pressed); read the real box from shape data and write it into the reworded ledger outline row; (2) that row must record the 1 px inset selection (14 vs TFC 16, the ring selects through to the log) and the missing collision, and its Revisit column must name the missing collision; (3) rewrite the lily-pad outline divergence row and docs/verification/PAR-PROC-05/verdict.md in the same change as the re-add, on the new carrier's commit; never reuse the lily-pad run quoted in the Superseded line."
  - "Blocked on PAR-INFRA-42 (tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json, outside files_owned): review found the selector's carrier minecraft:lily_pad is policy `absent` but our worldgen places real lily pads (PlantBlocks FLOATING/FLOATING_FRESH); the transparent appearance would draw every worldgen floating plant empty (an invisible 14 x 1.5 x 14 hitbox). The ScrapingBlock run line is withdrawn from Datagen (no scraping.yml is generated; GeneratedCarrierTest no longer pins tfp:scraping); the demand, shape row, ScrapingBlock and ScrapingBlockTest stay. When PAR-INFRA-42 is done: re-add the run line, re-pin tfp:scraping in GeneratedCarrierTest and restore its explicit-state lower bound to 21, reword the outline divergence row and the verdict Carrier section to the new carrier's real box, and redo the outline screenshot (plus one look at a pond: lily pads still drawn)."
  - "Re-add fidelity checks (review of the withdrawal): if the new carrier is minecraft:heavy_weighted_pressure_plate[power=0] (vanilla BasePressurePlateBlock box(1, 0, 1, 15, 1, 15): selection 14 x 1 x 14 at power=0, 14 x 0.5 x 14 pressed; no collision; read the real box from the shape data, not this note), the rewritten outline divergence row (ledger, now the lily-pad row) must also record the 1 px inset on each side (14 vs TFC 16; the ring selects through to the log) and the missing collision (TFC collides as a 16 x 1 x 16 plate), and its Revisit column must name the missing collision, confirm that sharing the state with the transparent placed-item carrier has no visible side effect, and confirm that no vanilla heavy weighted pressure plate (crafted or placed) can occur in our world; then rerun every in_game_step on that commit and write a new docs/verification/PAR-PROC-05/verdict.md for it (never reuse the lily-pad run quoted in the Superseded line)."
  - "Deferred from PAR-PROC-05 (PROTOCOL 3a + 6.5): the runtime is done and tested (core/scraping, paper/scraping, datagen/scraping ScrapingArt); the CraftEngine block tfp:scraping is missing because its carrier must come from the selector (HardcodedCarrierTest)."
  - "Hitbox: add a TfcShapes Spec for tfc:scraping (ScrapingBlock SHAPE = box(0, 0, 0, 16, 1, 16), selection = collision) and regenerate tfc-shapes.json (TfcShapesTest compares it with /opt/tfc); add the demand Demand(\"tfp:scraping\", \"base\", \"tfc:scraping\", 0, HitboxAllocation.TRANSPARENT, true, 1) to HitboxDemands (the look is the item display drawn by PaperScrapingEffects); add tfp:scraping to the pinned block ids of GeneratedCarrierTest."
  - "Block entry: ScrapingArt (or a sibling in datagen/scraping) writes configuration/scraping/scraping.yml through DeviceBlockSpec + DeviceYaml.templated: one appearance on HitboxAllocation.agreed(\"tfp:scraping\", \"base\"), TFC settings read from TFCBlocks (strength 0.2, SoundType.STEM, noOcclusion), no block item (registerNoItem) and no loot of its own (TFC expectedNoLootTableBlocks: the device framework drops the slot). Add the section to CeYamlCheck if it is new."
  - "Run every in_game_step at GUI scale 2 and 3, store crops and verdict.md in docs/verification/PAR-PROC-05/."
  - "Note: written through DeviceBlockSpec + DeviceYaml.literal (not templated): the device template always names one minecraft:mineable tag, which tfc:scraping is in none of, and DeviceAppearance refuses a transparent appearance without entity_renderer elements, so ScrapingBlock adds the settings (strength 0.2, stem sounds as plain ids, not occluding/suffocating/view-blocking) and transparent: true to the literal entry; single-state, so states holds only the one appearance."
  - "Superseded by the withdrawal (status blocked; a new Done line follows once PAR-INFRA-42 lands): Done: tfc:scraping shape row + HitboxDemands demand (carrier minecraft:lily_pad, hitbox-only), datagen/scraping/ScrapingBlock writes configuration/scraping/scraping.yml, GeneratedCarrierTest pins tfp:scraping; tests ScrapingBlockTest (3); in-game unrefined paper on a log at GUI 2 and 3 passed (docs/verification/PAR-PROC-05/verdict.md); hide steps stay pending on PAR-FAUNA-17; lily pad outline recorded as a divergence."
tests:
  - "datagen: tfc:scraping shape row, the carrier from the selector, the generated scraping.yml loads in CeYamlCheck"
done_criteria:
  - "Scraping unrefined paper on a log yields paper in game, the surface shows each scraped tile (screenshots, docs/verification/PAR-PROC-05/verdict.md)"
  - "Scraping a soaked hide yields a scraped hide in game (once PAR-FAUNA-17 is done; pending line)"
---

# PAR-PROC-29: Scraping block CE entry: tfc:scraping hitbox row and demand, configuration/scraping, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
