---
id: PAR-HEAT-27
title: "Candle cake bite and pumpkin carving hooked to the cake and pumpkin blocks"
area: HEAT
status: open
wave: 49
deps: [PAR-HEAT-11, PAR-DECO-12, PAR-FARM-05]
size: S
aliases: ["follow-up of PAR-HEAT-11 (the cake is PAR-DECO-12's, TFC's pumpkin and its rot PAR-FARM-05's)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCakeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCandleCakeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java"
  - "src/main/java/net/dries007/tfc/common/items/CandleBlockItem.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/light/candle/hooks/**"
  - "paper/src/test/java/dev/tfp/paper/light/candle/hooks/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/candle/**"
  - "content/generated/content/craftengine/tfp/configuration/light/candle/**"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-HEAT-11's CandleDevice eats a candle cake through a CandleDevice.CakeEating found in the module services (TFC TFCCakeBlock.eatCake: player.canEat(false), FoodData.CAKE, the block becomes tfp:cake[bites=1] and the candle drops); none is provided, so a click on a candle cake below its top only passes. CandleListener carves tfp:pumpkin with a knife or shears and asks a CandleService.Rotting from the services (TFC DecayingBlockEntity.isRotten); none is provided, so a pumpkin is never rotten. The candle on a cake (CandleService.addCandle) needs tfp:cake[bites] from PAR-DECO-12."
  - "Provide both from a small module in light/candle/hooks (service file line after the cake and crop modules): CakeEating from the cake's eating (the same nutrition and canEat as the cake block), Rotting from the pumpkin's decaying record. Check the property name bites and the ids tfp:cake and tfp:pumpkin against what PAR-DECO-12 and PAR-FARM-05 generated."
  - "Order of interactions on a candle cake (PAR-HEAT-11 review): in TFC the cake block's useItemOn runs before the item's useOn, so a click with a held tfp:torch, or with a candle on the lower half, eats a bite when the player can eat. PAR-HEAT-11's CandleListener (LOWEST) lights or adds first; once CakeEating exists, run the bite check before the torch's weak start when not sneaking."
  - "Add settings.item tfp:cake (TFC cloneItem(Blocks.CAKE)) to the candle cakes in CandleBlocks."
tests:
  - "paper: MockBukkit eat a candle cake (bite, candle dropped, full player passes), carve a rotten pumpkin (consumed, not carved)"
done_criteria:
  - "Eating a candle cake gives the cake's food and drops its candle; a rotten pumpkin is not carved"
---

# PAR-HEAT-27: Candle cake bite and pumpkin carving hooked up

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
