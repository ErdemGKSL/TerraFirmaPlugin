---
id: PAR-POT-14
title: "Pit kiln block: CraftEngine entry, hitboxes and look per stage, config keys, self-test kiln.fire, in-game check"
area: POTTERY
status: open
wave: 25
deps: [PAR-POT-03, PAR-POT-13]
size: M
aliases: ["split from PAR-POT-03: its files_owned cannot reach the hitbox demands, GeneratedCarrierTest, the shipped config or the itest cases"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/PitKilnBlockEntityRenderer.java"
  - "src/main/resources/assets/tfc/blockstates/pit_kiln.json"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/kiln/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/kiln/**"
  - "content/generated/content/craftengine/tfp/configuration/kiln/**"
  - "paper/src/main/resources/config.yml"
  - "docs/config.md"
  - "itest/src/main/java/dev/tfp/itest/cases/PitKilnFireCase.java"
  - "paper/src/main/java/dev/tfp/paper/kiln/**"
  - "paper/src/test/java/dev/tfp/paper/kiln/**"
ui: true
in_game_steps:
  - "place an unfired vessel on stone in a 1-block pit with stone on all four sides (hold right-click 1 s, PAR-POT-01)"
  - "right-click it with straw 8 times (the straw layers grow, stage 0..7), then with 8 logs (stage 8..15); screenshot at stage 4 (vessel visible) and 15"
  - "empty hand: take one log back and put it on again"
  - "light the kiln with a firestarter (strong start): fire above, lit look; screenshot"
  - "tfp time add hours 8"
  - "screenshot: the kiln is a placed item block holding a fired vessel, no fire above; take the vessel (hot)"
  - "negatives: straw on a placed item without a sturdy side does nothing; place dirt over the fire of a second lit kiln: it becomes a placed item block with the unfired vessel"
plan:
  - "Moved here from PAR-POT-03 (PROTOCOL §3 a, §6.5): PAR-POT-03 built the rules (core/kiln) and the Paper glue (paper/kiln: device kind tfp:pit_kiln with property stage 0..16 and 20 slots, PitKilns, PitKilnListener, KilnClock, PitKilnModule), but the CraftEngine block needs hitbox demands outside its files."
  - "1. Datagen dev.tfp.datagen.kiln.PitKilnBlock: block tfp:pit_kiln with property stage 0..16, the tfp:device behaviour, TFC's settings (MapColor.WOOD, SoundType.WOOD, strength 0.6, noOcclusion, no block item, no loot: the device framework drops the slots; the lit stage is not breakable in survival, TFC getDestroyProgress 0), checks of TFC's SHAPE_BY_LAYER and registration. Looks: TFC's pit_kiln blockstate models per stage (straw layers, logs, lit) through tfc-asset-migration."
  - "2. HitboxDemands: one demand per stage (TFC SHAPE_BY_LAYER: 1..8 px straw, the log rows, the full lit block); GeneratedCarrierTest pins; Datagen.generate run line; regenerate content/generated."
  - "3. Config: pit-kiln.ticks (TFC pitKilnTicks 7200, >= 20) and pit-kiln.temperature (pitKilnTemperature 1400, >= 0) in the shipped config.yml and docs/config.md; PitKilnModule already reads both (raw YAML, TFC ranges)."
  - "4. Self-test kiln.fire (itest case + SelfTestSuite line): an unfired vessel in a built, lit kiln becomes a fired vessel after the calendar advances pitKilnTicks."
  - "5. In-game check of PAR-POT-03 (in_game_steps above; screenshots in docs/verification/PAR-POT-03/)."
tests:
  - "datagen: PitKilnBlockTest (TFC shapes and settings, the carrier of every stage from HitboxAllocation.agreed); GeneratedCarrierTest pins tfp:pit_kiln"
  - "itest: kiln.fire"
done_criteria:
  - "CraftEngine loads configuration/kiln/pit_kiln.yml with no issue on Paper 1.21.11 and 26.x"
  - "Firing an unfired vessel in a pit kiln yields a vessel after pitKilnTicks (self-test `kiln.fire`)"
  - "In-game: straw and log stages, lighting and the fired vessel (PAR-POT-03's steps; screenshots in docs/verification/PAR-POT-03/)"
---

# PAR-POT-14: Pit kiln block: CraftEngine entry, hitboxes and look per stage, config keys, self-test kiln.fire, in-game check

> Claimed: subagent PAR-POT-14, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
