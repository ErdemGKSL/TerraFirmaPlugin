---
id: PAR-HEAT-11
title: "Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving"
area: HEAT
status: done
wave: 15
deps: [PAR-HEAT-10, PAR-INFRA-13]
size: S
aliases: ["M3-HEAT-04 (candles)", "M6-DECO-03 (candles, jack-o'-lanterns)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCandleCakeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/JackOLanternBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/light/candle/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/candle/**"
  - "content/generated/content/craftengine/tfp/configuration/light/candle/**"
ui: false
in_game_steps: []
plan:
  - "Candles (16 dyed + plain) and candle cakes with lazy burnout; pumpkin carving with a knife; jack-o'-lantern burnout."
  - "Deferred (PROTOCOL §3/§6.5): writing the CraftEngine entries needs the candle carriers in tools/datagen hitbox/HitboxDemands.java and the 35 ids in GeneratedCarrierTest (both outside this item, owned by PAR-HEAT-21 now), so light/candle/CandleBlocks is not yet called from Datagen; PAR-HEAT-26 wires it and carries the in-game steps and the burnout criterion (with 13 days: TFC candleTicks 288,000 is 12 days, the old 5-day step would still show a lit candle). The candle cake's bite needs the cake (PAR-DECO-12) and the carving's rot the pumpkin (PAR-FARM-05): hooks CandleDevice.CakeEating and CandleService.Rotting, provided by PAR-HEAT-27. Tests live under paper/src/test/java/dev/tfp/paper/light/candle/ and tools/datagen/src/test/java/dev/tfp/datagen/light/candle/ (the test mirrors of files_owned)."
  - "Review fix: vanilla CandleBlock.placeLiquid (TFCCandleBlock does not override it) puts a lit candle out when water enters it. CandleRules.drowned (lit + waterlogged candle) is now a 'goes out now' case in CandleService.decide/callback (so the chunk-load catch-up too), CandleDevice's click and neighbour paths, and CandleListener re-checks the block a tick after a water bucket is emptied by a player or a dispenser (CandleService.waterEntered); the commit writes lit=false with the extinguish sound and keeps the counter. In-game step in PAR-HEAT-26."
  - "Done: paper/light/candle CandleRules (pure: ids, TFC candleTicks 288,000 and jackOLanternTicks 108,000, lazy burnout = TFC random tick, use table with the firestarter QoL and the candle cake's top half, fire start / douse branches, light levels, carved facing, candle cake per candle), CandleDevice (kinds tfp:candle, tfp:candle_cake (dyed sub-ids) and tfp:jack_o_lantern, record = TFC TickCounterBlockEntity with Integer.MIN_VALUE initial counter; extinguish on an empty hand, the bite through CakeEating; a placed jack-o'-lantern has no counter and goes out as in TFC), CandleService (decide async, apply sync burnout pipeline: chunk-load catch-up, burnout callback, re-validated commit to lit=false or vanilla's carved pumpkin with the facing; FireLightable lighting candles, candle cakes, carved pumpkins (to the jack-o'-lantern) and relighting; douse for PAR-HEAT-13; addCandle on a fresh tfp:cake; carve tfp:pumpkin), PaperCandleEffects, CandleListener (held tfp:torch weak start instead of placing, candle on cake, knife or shears carving; game modes as vanilla), CandleModule (service file after fire and charcoal). Datagen light/candle/CandleBlocks (35 blocks, 18 items, TFC's vanilla models per state, loot tables, names; not wired, PAR-HEAT-26). Tests: paper CandleRulesTest (4, incl. lazy vs stepped), CandleMockBukkitTest (19: catch-up after 13 days puts a candle out and keeps the 4 candles, before the limit waits for the exact rest, unlit never changes, candle cake, jack-o'-lantern to carved pumpkin with facing after 108,001 ticks, placed jack goes out, waterlogged not lit, fire start resets, extinguish and the firestarter/adventure rules, burnt-out click, candle cake top and bite, relight between decision and commit wins, douse, candle on cake incl. creative, knife carving incl. top face, rotten and sneaking, held torch, service-file order, a water bucket on a lit candle puts it out with the sound and keeps the counter, a lit waterlogged candle goes out on a neighbour update, click or chunk load), datagen CandleBlocksTest (4). In-game check: PAR-HEAT-26."
tests:
  - "paper: catch-up tests"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-26"
---

# PAR-HEAT-11: Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
