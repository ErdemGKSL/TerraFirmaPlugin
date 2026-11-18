---
id: PAR-HEAT-26
title: "Candles loaded in game: hitbox carriers, configuration/light/candle/candle.yml and the in-game candle burnout check"
area: HEAT
status: blocked
wave: 16
deps: [PAR-HEAT-11, PAR-HEAT-21]
size: S
aliases: ["follow-up of PAR-HEAT-11 (the CraftEngine entries need the hitbox demands and GeneratedCarrierTest; its in-game criterion moved here); after PAR-HEAT-21, which owns the same hitbox files"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/candle.json"
  - "src/main/resources/assets/tfc/blockstates/candle_cake.json"
  - "src/main/resources/assets/tfc/blockstates/jack_o_lantern.json"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rendered/RenderedSound.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/candle/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/light/candle/**"
  - "content/generated/content/craftengine/tfp/configuration/light/candle/**"
  - "docs/verification/PAR-HEAT-11/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:candle 4; ce item give Bot tfp:candle/red 1; ce item give Bot tfp:firestarter"
  - "place a tfp:candle and click it three more times with tfp:candle (4 candles), place the red candle next to it"
  - "light both with the firestarter (hold right click); F3 shows light 14 at the four candles and 5 at the red one"
  - "right-click the lit red candle with an empty hand: it goes out (vanilla candle extinguish sound); light it again"
  - "light the red candle again and empty a water bucket on it: it is waterlogged and goes out at once (vanilla candle extinguish sound and smoke, F3 light 0); the firestarter does not light it while waterlogged; repeat with a dispenser facing a lit candle and a water bucket in it; pick the water back up and light it again"
  - "tfp time add days 13 (TFC candleTicks 288,000 = 12 days of 24,000 ticks; 5 days, as the old step said, is too short)"
  - "relog / tp away (out of view distance) and back"
  - "screenshot: both candles are unlit (no flame, no light), the four candles are still four"
  - "light a vanilla carved pumpkin with the firestarter: it becomes tfp:jack_o_lantern facing the same way (light 15); tfp time add days 5 (jackOLanternTicks 108,000 = 4.5 days), reload the chunk: it is a carved pumpkin again"
  - "Screenshots at GUI scale 2 and 3 of the lit and the burnt-out candles and of the jack-o'-lantern"
plan:
  - "BLOCKED (2026-11-18): the carriers cannot be selected with the files this item owns. HitboxAllocation.agreed (the only carrier source GeneratedCarrierTest and HardcodedCarrierTest accept) needs (1) TFC rows in the committed tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json (TfcState.of throws 'has no row' for tfc:candle[candles=N], tfc:candle_cake, tfc:jack_o_lantern[facing=*]; TfcShapesTest requires the json to equal TfcShapes.generate, so adding the candle specs to TfcShapes.java alone fails ./gradlew check) and (2) the vanilla carriers in vanilla-shapes-{1.21.4,1.21.11,26.1.2,26.3}.json: only minecraft:candle (16 states) is dumped; the 16 dyed candles, the 17 candle cakes and minecraft:jack_o_lantern are absent (grep: 0 hits), so the 16 dyed candles (128 distinct candles x lit looks) and the cakes have no carrier at all and the allocation overflows. (3) carrier-policy.json needs presence rows for the dyed candles, candle cakes and jack_o_lantern (minecraft:candle is already absent). Blocking files: tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/** (tfc-shapes.json, vanilla-shapes-*.json, carrier-policy.json; done items PAR-HEAT-21/29 own them, open PAR-INFRA-53 only unusable.json, blocked PAR-INFRA-51 carrier-policy.json) and a carrier test under tools/datagen/src/test/java/dev/tfp/datagen/hitbox/ (like TorchCarrierTest). Unblock: add tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/** and tools/datagen/src/test/java/dev/tfp/datagen/hitbox/** to files_owned (re-dump the dyed candles, candle cakes and jack_o_lantern from each server jar with a scratch dumper outside the repo, as PAR-HEAT-21 did for the torches), then redo the plan below. No code changed in this round."
  - "Current: PAR-HEAT-11 ships the rules and runtime (paper/light/candle: device kinds tfp:candle, tfp:candle_cake (each with its dyed sub-ids) and tfp:jack_o_lantern, lazy burnout pipeline, lighting through FireStarting, extinguish, douse, the candle on a cake, the knife carving, the held torch's weak start) and the generator tools/datagen light/candle/CandleBlocks.run(tfc, out, carriers), which is not called yet: an explicit appearance state needs a HitboxDemands entry and GeneratedCarrierTest pins the checked block ids."
  - "Add the demands: the 17 candles per candles/lit/waterlogged (TFC draws vanilla's candle models; the natural carriers are vanilla's own candle states of the same colour, which never occur in a TFC world; candle shapes in TfcShapes if the selector does not know them), the 17 candle cakes per lit (vanilla's candle cake states), the jack-o'-lantern per facing (vanilla's jack_o_lantern states, light 15)."
  - "Call CandleBlocks.run from Datagen.generate with carriers from HitboxAllocation.agreed, add the 35 ids to GeneratedCarrierTest, regenerate content (configuration/light/candle/candle.yml, item models, loot tables blocks/candle*, blocks/jack_o_lantern, tfp_candle lang)."
  - "CandleBlocks writes plain vanilla candle and wool sound ids because RenderedSound has no entry for SoundType.CANDLE and WOOL (both 1.0/1.0); add them there so the hit-sound packet rewrite reads them."
  - "The candle cakes have no settings.item (pick block): TFC cloneItem(Blocks.CAKE) means tfp:cake, which only exists with PAR-DECO-12; add it then (PAR-HEAT-27)."
  - "Check in game: CraftEngine's stackable_block adds a candle with the same item (vanilla CandleBlock.canBeReplaced), waterlogging a candle (hardcoded waterlogged behaviour) and that a waterlogged candle cannot be lit; that cancelling PlayerInteractEvent in CandleListener stops CraftEngine placing a held tfp:torch on a candle; that the extinguish click's state write (block data without physics) keeps the CraftEngine block entity and its counter; the candle cake top-click height (CandleModule.hitHeight ray trace). Water on a lit candle (vanilla CandleBlock.placeLiquid, kept by TFC): PAR-HEAT-11's CandleListener re-checks the block a tick after PlayerBucketEmptyEvent / BlockDispenseEvent, so check that CraftEngine's waterlogging of the candle fires those events (if it does not, the candle goes out only at its next click, neighbour update or chunk load: record it). The smoke particles of the extinguish come from the vanilla client's own prediction on the carrier: include them in the visual check. TFC's candle cakes give comparator output 14 (CakeBlock FULL_CAKE_SIGNAL) and are not pathfindable: check both on the chosen carriers and record a divergence if a carrier differs."
tests:
  - "datagen: GeneratedCarrierTest covers the 35 candle ids; CandleBlocksTest with the agreed carriers"
done_criteria:
  - "A candle lit and `tfp time add days 13` later is unlit after chunk reload (screenshot, docs/verification/PAR-HEAT-11/)"
  - "A jack-o'-lantern lit from a carved pumpkin is a carved pumpkin again after jackOLanternTicks"
---

# PAR-HEAT-26: Candles loaded in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
