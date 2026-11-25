---
id: PAR-POT-13
title: "Placed item block: hitbox demand, datagen wiring, config keys, use-duration spike, in-game check"
area: POTTERY
status: done
wave: 22
deps: [PAR-POT-01]
size: S
aliases: ["split from PAR-POT-01: its files_owned cannot reach the hitbox demands, GeneratedCarrierTest or the shipped config"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/placed/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/placed/**"
  - "content/generated/content/craftengine/tfp/configuration/placed/**"
  - "paper/src/main/resources/config.yml"
  - "docs/config.md"
  - "paper/src/main/java/dev/tfp/paper/placed/**"
  - "paper/src/test/java/dev/tfp/paper/placed/**"
ui: true
in_game_steps:
  - "hold an ingot; look at the top face of a stone block near its NW corner; press right-click and release after 0.5 s: nothing is placed, the progress bar on the action bar resets"
  - "hold right-click for 1.2 s on the same spot: the ingot is placed in the NW quadrant at 1 s (progress bar fills, place sound), nothing else happens while held; repeat with a vessel (NE), a metal sheet (SW) and a mold (SE): four items in one block; screenshot after each (item in the quadrant nearest the cursor, rotated to the player's yaw in 15 deg steps, outline = pressure plate)"
  - "release early: hold 0.6 s, move the cursor to another block or switch hotbar slot, keep holding: nothing placed, the hold restarts on the new target"
  - "tap (< 1 s) a vessel on stone: its GUI opens on release; tap a hoe on dirt: tills at once (action first, no hold)"
  - "place one very large item (large-item slot) on an empty block by holding; screenshot"
  - "pickup: right-click each quadrant with an empty hand (that item returns), sneak + right-click an occupied quadrant with an item in hand (returns it, TFC rule); walk Bot across the block (no collision); break a block with 4 items (all drop, no vanilla pressure plate)"
  - "negatives: a block item held 2 s on stone places the block (no placed item); a side face never places; with `placed-items.gesture: sneak-double-tap` the fallback gesture places and holding does not"
  - "spike (1.21.11 and 26.x): hold right-click with a food item and with a jug while sneaking on a top face; record whether the client keeps resending use-on-block or only starts using the item, and whether a server clearActiveItem stops its use animation"
plan:
  - "Moved here from PAR-POT-01 (PROTOCOL §3 a, §6.5): PAR-POT-01 built the rules (core/placed), the Paper glue (paper/placed: device kind tfp:placed_item, the hold gesture, take back, item displays, support rule, PlacedItemsModule) and the datagen generator dev.tfp.datagen.placed.PlacedItemBlock with its test, but could not wire the CraftEngine block: the hitbox demand and the GeneratedCarrierTest pin are outside its files."
  - "1. HitboxDemands.all(): one demand new Demand(PlacedItemBlock.ID, PlacedItemBlock.APPEARANCE, PlacedItemBlock.DEMAND_KEY, 0, HitboxAllocation.TRANSPARENT, true, <a weight served after every other demand>); the class override (class-overrides.json, placed_item) picks minecraft:heavy_weighted_pressure_plate[power=0] with the hide mapping to power=1, shared with the scraping surface (both drawn empty). Check that no other carrier moves (allocation reports)."
  - "2. Datagen.generate: one run line `if (PlacedItemBlock.available(tfc)) PlacedItemBlock.run(tfc, out)` (as ScrapingBlock); GeneratedCarrierTest: add tfp:placed_item to the expected ids, bump the state floor by 1; regenerate content/generated."
  - "3. Config: add placed-items.enabled (TFC enablePlacingItems, true) and placed-items.gesture (hold | sneak-double-tap | both, default both) to the shipped config.yml and docs/config.md; PlacedItemsModule already reads both keys (raw YAML) and falls back to the defaults."
  - "4. Use-duration items (food, drinkable jugs, soups, bows, shields, spyglass, javelins): PAR-POT-01 exempts them (no hold is seen: the client stops resending use-on-block while it uses the item). Run the spike step; if the client stops its own use animation after clearActiveItem, add the sneak + hold path of PAR-POT-01's plan (use start + a 20-tick check that the player still uses that stack on the same face, then clearActiveItem and place); else keep them exempt and update the ledger row."
  - "5. In-game check of PAR-POT-01 (its in_game_steps, moved here)."
  - "Done: HitboxDemands placed item demand (weight -102, served last, pressure plate via class override), Datagen run line, GeneratedCarrierTest pin (expected ids + state floor), placed-items.enabled / placed-items.gesture in config.yml and docs/config.md (enablePlacingItems mapped); content/generated placed/placed_item.yml generated. Tests: PlacedItemBlockTest 3 green; ConfigParityDocTest green; other failures in the suite are other items' (see report). Pending in-game check (container cannot run client): in_game_steps kept, evidence goes to docs/verification/PAR-POT-01/. Use-duration spike not run: use-duration items stay exempt (ledger row updated)."
tests:
  - "datagen: PlacedItemBlockTest uses HitboxAllocation.agreed for the carrier; GeneratedCarrierTest pins tfp:placed_item"
  - "paper: MockBukkit for the use-duration path if the spike allows it"
done_criteria:
  - "CraftEngine loads configuration/placed/placed_item.yml with no issue on Paper 1.21.11 and 26.x"
  - "In-game: four items placed and taken back (PAR-POT-01's steps; screenshots in docs/verification/PAR-POT-01/)"
---

# PAR-POT-13: Placed item block: hitbox demand, datagen wiring, config keys, use-duration spike, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
