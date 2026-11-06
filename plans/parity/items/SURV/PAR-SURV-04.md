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
  - "setup before every capture: forceload add -16 -16 16 16 and build the floor (fill, confirm with execute if block) BEFORE the client joins or before any tp, so the Bot never stands over unloaded chunks (the server kicks it for flying); gamemode survival Bot; difficulty normal; effect clear Bot; restore health; data get entity Bot Health must be > 0 before each shot; wait out the chat-verification toast"
  - "tfp debug thirst Bot 5; wait 10 s; data get entity Bot active_effects: minecraft:slowness and minecraft:mining_fatigue, amplifier 1, duration <= 160, show_icon 0b (TFC's MobEffectInstance(effect, 160, 1, false, false) has no icon: do not expect an icon on screen, and never give the effect by hand)"
  - "tfp debug thirst Bot 0 (survival, difficulty normal); data get entity Bot Health now and again after 10-20 s: down 1 per 5 s (100 ticks); at thirst 5 health does not drop (TFC hurts only at thirst <= 0). While foodLevel >= 18 vanilla natural regeneration still heals 1 per 4 s and hides the hits (its replacement is PAR-SURV-05), so start this step with foodLevel below 18 or read health over 20 s"
  - "rain: open floor with no block above; tfp debug thirst Bot 5; weather rain; tp Bot ~ ~ ~ ~ -90 (pitch must be <= -80, TFC's wrapDegrees(pitch) <= -80: -60 or -70 does NOT drink); tfp debug rain Bot: every gate true; screenshot (before)"
  - "wait at least 2 HUD periods (2 s or more); tfp debug player Bot shows thirst above 5 (about +4.17 per second; lastDrinkTick stays never, TFC's addThirst does not set it); screenshot (after): the thirst bar is visibly longer than in the before frame"
  - "salt water: find an ocean-biome water source with a block behind it within 4.5 blocks; tfp debug thirst Bot 50; right-click the water with an empty hand; thirst -4 per drink, the tfp:thirst effect sometimes (chance 1-(0.75)^4); then a river/lake source with an empty hand: thirst +40 per drink and no tfp:thirst effect (fresh water unaffected)"
  - "note: rain drawn at a TFC ambient below 0 C is the ledger divergence 'raining at' (the client draws the vanilla biome's rain), not a defect"
plan:
  - "Current: thirst decay lazy, lakebed drink verified in-game; open-water empty-hand clicks send no packet (vanilla)."
  - "Thirst effects in Paper: slowdown amplifier per ThirstStage, dehydration damage every 100 ticks (not in peaceful), `tfp:dehydration` damage type."
  - "thirstGainedFromDrinkingInTheRain: while raining at the player and looking up (TFC rule), add thirst on the HUD tick."
  - "Salt water: drinkable `salt_water` applies its effects; the ocean is vanilla water in a salt biome (decide per biome tag)."
  - "Open water: record the divergence (needs a block behind or an item) in the ledger and close the gaps entry."
  - "Notes: TFC's doDrink passes JUG_DRINK_MB (100 mB), so a source drink is 4x the JSON (water +40, salt -4, effect chance 1-(0.75)^4); WaterDrink now reads the drinkable data and drinks that. TFC's lack-of-thirst effects have no icon (visible=false), so the in-game check reads the effects with /data get entity. No tfp:dehydration damage type exists on the server: minecraft:starve with TFC's death message, follow-up PAR-SURV-10."
  - "Repair (independent REJECT of 4e6e960: rain drawn, no drink): the gate used the TFC ambient temperature, which the vanilla client does not draw. Rain drinking now follows the client's rain: storm, open sky, the client's biome (seasonal variant in a TFC world, else the server biome) has precipitation and is >= 0.15. /tfp debug rain [name] prints the gates. Re-captured on the default calendar at spawn (8.0 C) and at z -9000 (-19.8 C): thirst 5 to ~47 in 10 s. Tests: RainDrinkingTest 5, SeasonalVariantRainTest 1, ThirstModuleMockBukkitTest 13 (cold/NaN ambient under visible rain drinks)."
  - "Unblock (in-game REJECT of 8068b05: the tester used pitch -60, which TFC's <= -80 gate refuses, and looked for an icon TFC never shows): root cause in the plan, not the code. done_criteria and in_game_steps now follow TFC (effects read with data get entity, damage only at 0, pitch -90 with the gate spelled out, floor and forceload before the join). Re-run on 8068b05's code (docs/verification/PAR-SURV-04/verdict.md, Unblock run): active_effects slowness + mining_fatigue amp 1, show_icon 0b; thirst 0 health 20 -> 10 at one point per 5 s once food < 18; pitch -60 in rain stays 5.0, pitch -90 5.0 -> 17.5 -> 25.8 -> 34.2 at 2 s steps with a longer bar in the after frame; no fly kick."
  - "Done: core thirst/{Drinkable,Drinkables,ThirstEffect,RainDrinking} + ThirstState.drinkRain; paper ThirstModule (feature module thirst), ThirstEffects (PDC), WaterDrink/WaterDrinkListener on drinkable data with salt by ocean biome, sound, swing, sprint stop; tests ThirstGoldenTest (+3, 90 rows), DrinkablesTest 4, RainDrinkingTest 5, SeasonalVariantRainTest 1, ThirstModuleMockBukkitTest 13, WaterDrinkTest 7, WaterDrinkListenerMockBukkitTest 2; ledger feature row + 5 divergence rows; verification docs/verification/PAR-SURV-04/."
tests:
  - "core: ThirstGoldenTest extended for rain gain"
  - "paper: MockBukkit effect application per stage"
done_criteria:
  - "Thirst < 10 (checked at 5): slowness and mining_fatigue amplifier 1, about 160 ticks, show_icon 0b, read with data get entity (no icon, as TFC); no damage above 0"
  - "Thirst 0: 1 dehydration damage per 100 ticks outside peaceful (health down 1 per 5 s in survival, difficulty normal)"
  - "Looking up (pitch <= -80) in visible rain raises thirst: tfp debug player above 5 after 2 HUD periods and a longer thirst bar in the after frame"
---

# PAR-SURV-04: Thirst completion: thirst effects, rain drinking, salt water, open-water drinking decision

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
