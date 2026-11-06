---
id: PAR-SURV-04
title: "Thirst completion: thirst effects, rain drinking, salt water, open-water drinking decision"
area: SURV
status: done
wave: 7
deps: []
size: M
aliases: ["M3-SURV-03", "gaps: M3-SURV-03 drink"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/effect/ThirstEffect.java"
  - "src/main/java/net/dries007/tfc/util/data/Drinkable.java"
  - "src/main/java/net/dries007/tfc/network/PlayerDrinkPacket.java"
  - "src/generated/resources/data/tfc/tfc/drinkable"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/player/Thirst*"
  - "paper/src/main/java/dev/tfp/paper/player/WaterDrink*"
  - "paper/src/test/java/dev/tfp/paper/player/Thirst*"
  - "paper/src/test/java/dev/tfp/paper/player/WaterDrink*"
  - "core/src/main/java/dev/tfp/core/thirst/**"
  - "core/src/test/java/dev/tfp/core/thirst/**"
ui: true
in_game_steps:
  - "tfp debug thirst Bot 5"
  - "wait 10 s; screenshot slowness icon"
  - "weather rain; look up; tfp debug player Bot"
plan:
  - "Current: thirst decay lazy, lakebed drink verified in-game; open-water empty-hand clicks send no packet (vanilla)."
  - "Thirst effects in Paper: slowdown amplifier per ThirstStage, dehydration damage every 100 ticks (not in peaceful), `tfp:dehydration` damage type."
  - "thirstGainedFromDrinkingInTheRain: while raining at the player and looking up (TFC rule), add thirst on the HUD tick."
  - "Salt water: drinkable `salt_water` applies its effects; the ocean is vanilla water in a salt biome (decide per biome tag)."
  - "Open water: record the divergence (needs a block behind or an item) in the ledger and close the gaps entry."
  - "Notes: TFC's doDrink passes JUG_DRINK_MB (100 mB), so a source drink is 4x the JSON (water +40, salt -4, effect chance 1-(0.75)^4); WaterDrink now reads the drinkable data and drinks that. TFC's lack-of-thirst effects have no icon (visible=false), so the in-game check reads the effects with /data get entity. No tfp:dehydration damage type exists on the server: minecraft:starve with TFC's death message, follow-up PAR-SURV-10."
  - "Done: core thirst/{Drinkable,Drinkables,ThirstEffect,RainDrinking} + ThirstState.drinkRain; paper ThirstModule (feature module thirst), ThirstEffects (PDC), WaterDrink/WaterDrinkListener on drinkable data with salt by ocean biome, sound, swing, sprint stop; tests ThirstGoldenTest (+3, 90 rows), DrinkablesTest 4, RainDrinkingTest 4, ThirstModuleMockBukkitTest 11, WaterDrinkTest 7, WaterDrinkListenerMockBukkitTest 2; ledger feature row + 5 divergence rows; verification docs/verification/PAR-SURV-04/."
tests:
  - "core: ThirstGoldenTest extended for rain gain"
  - "paper: MockBukkit effect application per stage"
done_criteria:
  - "Thirst 5 shows slowness and periodic damage in-game; rain drinking raises thirst"
---

# PAR-SURV-04: Thirst completion: thirst effects, rain drinking, salt water, open-water drinking decision

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
