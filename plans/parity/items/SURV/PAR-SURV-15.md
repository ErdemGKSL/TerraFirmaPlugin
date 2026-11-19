---
id: PAR-SURV-15
title: "TFC exhaustion on Paper: passive hunger drain on the vanilla food bar, vanilla action exhaustion x0.4, thirst lost on every 4 exhaustion"
area: SURV
status: blocked
wave: 17
deps: [PAR-SURV-04, PAR-SURV-06, PAR-SURV-09, PAR-HUD-12]
size: M
aliases: ["parity audit 2026-11-17: ledger divergence 'TFC's thirst loss also comes from action exhaustion' (revisit M3-SURV-03, whose item PAR-SURV-04 closed without it); no Paper code applies HungerState's passive exhaustion to the vanilla food level"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
  - "src/main/java/net/dries007/tfc/common/effect/ExhaustedEffect.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/player/exhaustion/**"
  - "paper/src/test/java/dev/tfp/paper/player/exhaustion/**"
  - "paper/src/main/java/dev/tfp/paper/player/ThirstDecay.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerData.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerDataCodec.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/test/java/dev/tfp/paper/player/ThirstDecayTest.java"
  - "paper/src/test/java/dev/tfp/paper/player/PlayerDataCodecTest.java"
  - "core/src/main/java/dev/tfp/core/food/HungerState.java"
  - "core/src/test/java/dev/tfp/core/food/HungerState*Test.java"
  - "itest/src/main/java/dev/tfp/itest/cases/ExhaustionCase.java"
  - "docs/verification/PAR-SURV-15/**"
ui: true
in_game_steps:
  - "gamemode survival Bot; difficulty easy; tfp debug hunger Bot 20; effect clear Bot; tfp debug player Bot (note food, saturation, thirst)"
  - "tfp time add 2d while Bot stands still; tfp debug player Bot: the food bar fell at TFC's passive rate (one point per 3000 player ticks once saturation is gone) and thirst fell with it; screenshot the HUD at GUI scale 2"
  - "tfp debug hunger Bot 20; Bot sprint-jumps for 60 s (bot script in spikes/client/); tfp debug player Bot: food and thirst fell by the 0.4-scaled vanilla action exhaustion plus the passive share, not by vanilla's full rate"
  - "gamemode creative Bot; tfp time add 1d; gamemode survival Bot; tfp debug player Bot: food and thirst unchanged by the creative day"
plan:
  - "BLOCKED (2026-11-19): code, tests and ledger are done and pushed (FoodSpend, ExhaustionModule, ThirstDecay.addExhaustion, PlayerData.pendingFoodPoints, codec v3; paper tests green). The ui in-game check (docs/verification/PAR-SURV-15) could not run: this container has no /opt/tfc checkout and no headless Paper+client setup, so datagen content cannot be built. Run the in_game_steps where /opt/tfc exists, then set done. itest ExhaustionCase and the core TFC-golden export are also still to do."
  - "What exists: core HungerState evaluates TFC's passive exhaustion lazily (exactly 3000 ticks per point, x passiveExhaustionModifier, x0.4) but only ThirstDecay uses it, as the thirst clock; nothing applies it to the vanilla food level, so a survival player standing still never gets hungry. Vanilla's own action exhaustion (sprint, jump, mining, attack, damage) reaches the vanilla FoodData at full rate, while TFC's PlayerInfo.addExhaustion multiplies every vanilla source by EXHAUSTION_MULTIPLIER 0.4; and thirst ignores it, while TFC drops `thirstModifier x (1 + temperature share)` each time the exhaustion reaches 4, whatever its source. The exhausted effect's extra exhaustion (BurdenApplier, PAR-SURV-06) therefore costs no thirst either (ledger divergence)."
  - "1. Action exhaustion: a listener in player/exhaustion on EntityExhaustionEvent (players, every reason except REGEN, which HealthRegenerator already cancels) sets the amount to 0.4x (TFC addExhaustion) and counts the scaled amount into an action-exhaustion pool in PlayerData (new field; PlayerDataCodec version bump with a migration starting at 0)."
  - "2. Passive exhaustion on the vanilla bar, lazily: at each evaluation (the 1 s needs pass of PlayerNeedsHudService, join, eat, respawn, game-mode change) the passive units since the last evaluation plus the action pool are applied on the player's thread in vanilla FoodData's order (exhaustion >= 4 takes saturation first, then 1 food; TFC's peaceful branch of PlayerInfo.tick), and each crossed 4.0 costs TFC's thirst step. ThirstDecay counts every exhaustion point, passive and action, through one pure rule, golden against TFC (scratch export of PlayerInfo.tick over N ticks with a scripted action list)."
  - "3. Game modes (PROTOCOL 4.7): no exhaustion in creative and spectator (vanilla adds none there; the lazy clock re-stamps without catch-up, as GameModeNeeds does); adventure as survival. Death and respawn restart the pool (PAR-SURV-09 rules); a dimension change keeps it; invulnerable players add none."
  - "4. Ledger: close the 'thirst loss also comes from action exhaustion' and the exhausted-effect thirst divergences (or rewrite them to what remains); the 'Food & nutrition' row gains the passive hunger drain. passiveExhaustionModifier and thirstModifier stay at TFC defaults here; their config.yml keys are PAR-DATA-06's."
tests:
  - "core: HungerState golden vs TFC PlayerInfo.tick with passive plus scripted action exhaustion (food, saturation, thirst after 1, 3000 and 72000 ticks); lazy-vs-stepped over random gaps"
  - "paper MockBukkit: the exhaustion listener scales a SPRINT exhaustion to 0.4x and adds nothing in creative; PlayerDataCodec migration; ThirstDecayTest with action points"
  - "itest ExhaustionCase: a real player's vanilla exhaustion after a scripted jump is 0.4x vanilla's, and food falls after a forced calendar skip"
done_criteria:
  - "A survival player standing still loses food at TFC's passive rate once saturation is gone (golden), and thirst falls with each exhaustion step"
  - "Vanilla action exhaustion reaches the food bar at 0.4x and costs thirst per 4 exhaustion like TFC"
  - "Creative/spectator time costs neither food nor thirst; ./gradlew check passes; screenshots and verdict in docs/verification/PAR-SURV-15/"
---

# PAR-SURV-15: TFC exhaustion on Paper: passive hunger drain on the vanilla food bar, vanilla action exhaustion x0.4, thirst lost on every 4 exhaustion

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
