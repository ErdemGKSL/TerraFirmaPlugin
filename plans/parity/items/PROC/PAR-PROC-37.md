---
id: PAR-PROC-37
title: "Log pile, burning log pile and charcoal pile CE blocks: hitbox rows and demands, configuration/charcoal, support rule, in-game check"
area: PROC
status: open
wave: 14
deps: [PAR-PROC-10, PAR-INFRA-13]
size: M
aliases: ["follow-up of PAR-PROC-10 (its CraftEngine blocks and its in_game_steps, PROTOCOL 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates/log_pile.json"
  - "src/main/resources/assets/tfc/blockstates/burning_log_pile.json"
  - "src/main/resources/assets/tfc/blockstates/charcoal_pile.json"
  - "src/main/resources/assets/tfc/models/block/log_pile"
  - "src/main/resources/data/tfc/loot_table/blocks/charcoal_pile.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/charcoal/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/charcoal/**"
  - "content/generated/content/craftengine/tfp/configuration/charcoal/**"
  - "paper/src/main/java/dev/tfp/paper/charcoal/CharcoalPileSupport.java"
  - "paper/src/test/java/dev/tfp/paper/charcoal/CharcoalPileSupportTest.java"
  - "docs/verification/PAR-PROC-10/**"
ui: true
in_game_steps:
  - "sneak-place 16 logs"
  - "cover with dirt, light top"
  - "tfp time add hours 20"
  - "dig in; screenshot"
  - "right-click a furnace and a log pile with charcoal (no pile; the furnace opens, the log pile gives a log)"
plan:
  - "Deferred from PAR-PROC-10 (PROTOCOL 3 + 6.5): the runtime is done and tested (core/charcoal LogPileColumn, LogPileData, BurningPileData, CharcoalPit, CharcoalSpread, CharcoalPile; paper/charcoal CharcoalPitDevice with the device kinds tfp:log_pile and tfp:burning_log_pile, CharcoalListener, PitClock, CharcoalModule, PaperCharcoalEffects). The CraftEngine blocks are missing because their hitbox carriers must come from the selector (GeneratedCarrierTest) and HitboxDemands.java / tfc-shapes.json were outside PAR-PROC-10's files_owned. Without them nothing can be placed (CharcoalPitDevice checks CustomBlocks.exists)."
  - "Hitbox: tfc-shapes.json rows for tfc:log_pile and tfc:burning_log_pile[axis=x|z,count=1..16] (LogPileBlock.getShapeByDirByCount: the based selection shape below 4 logs, the collision without the base; the burning pile's default full-cube shape with getVisualShape) — tfc:charcoal_pile[layers=1..8] rows already exist; HitboxDemands for the three blocks (32 + 32 + 8 states; the log pile models per count and axis from blockstates/log_pile.json, the burning ones from burning_log_pile.json, the charcoal pile models per layer). Pin them in GeneratedCarrierTest."
  - "Block entries: datagen/charcoal writes configuration/charcoal/*.yml: tfp:log_pile and tfp:burning_log_pile with the tfp:device behaviour (properties axis x/z = CharcoalPitDevice.AXIS, count 1..16 = COUNT; the device writes both), TFC's strength 0.6, wood sounds, flammable(60, 30) for the log pile and flammableLikeLogs for the burning pile, no loot (TFC SelfTests lists both as no-loot blocks; the logs drop through the device), the burning pile's light and its fire-like look; tfp:charcoal_pile (layers 1..8 = LAYERS, strength 0.2, tfp:block.charcoal.* sounds, push reaction destroy, loot: one minecraft:charcoal per break (survives_explosion)). No block items (TFC registerNoItem). Add the section to CeYamlCheck if it is new."
  - "Support rule: CharcoalPileSupport, a ShapeRule for tfp:charcoal_pile (TFC canSurvive: a full top face below or an eight-layer pile, else air) on the neighbour runner; the full-top-face test of a vanilla state off the region thread needs a thread-safe lookup (a cached BlockData parse per state string)."
  - "In-game check (PROTOCOL 6): run the steps with the bot (oak logs, dirt cover, a firestarter or flint and steel on the top pile); screenshot the log pile at 1, 8 and 16 logs, the burning pile and the charcoal pile after the time skip; store docs/verification/PAR-PROC-10/. Done criterion of PAR-PROC-10 moved here. Check also that sneak + CE log does not place the log block (CharcoalListener denies the use before CraftEngine's HIGHEST handler) and that a layer break of a charcoal pile is not doubled by CraftEngine's own break handling. Check also that a plain right-click with charcoal on a furnace opens it and on a log pile takes a log (no charcoal pile appears), and that a sneak click places a charcoal pile."
tests:
  - "datagen: CharcoalBlocksTest (properties, carriers from the allocation, TFC's models)"
  - "paper: CharcoalPileSupportTest"
done_criteria:
  - "A covered lit log pile becomes charcoal in-game"
---

# PAR-PROC-37: Log pile, burning log pile and charcoal pile CE blocks: hitbox rows and demands, configuration/charcoal, support rule, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
