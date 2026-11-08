---
id: PAR-SURV-08
title: "Alcohol and intoxication"
area: SURV
status: done
wave: 9
deps: [PAR-SURV-04, PAR-FLUID-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/fluids/Alcohol.java"
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
  - "src/generated/resources/data/tfc/tfc/drinkable"
files_owned:
  - "core/src/main/java/dev/tfp/core/intox/**"
  - "core/src/test/java/dev/tfp/core/intox/**"
  - "core/src/test/resources/dev/tfp/core/intox/**"
  - "paper/src/main/java/dev/tfp/paper/intox/**"
  - "paper/src/test/java/dev/tfp/paper/intox/**"
ui: false
in_game_steps: []
plan:
  - "Drinking alcohol (from a jug) adds intoxication ticks capped at maxIntoxicationTicks; nausea/effects as the drinkable defines; lazy decay from a timestamp in PlayerData."
  - "Finding (2026-11-08): TFC c3b4d9536 gives no nausea or any other effect for alcohol: alcohol.json has no effects, and intoxication's only consumer is ForgeEventHandler.onServerChat (drunk chat above 0.2). The original criterion 'shows nausea' is replaced by TFC's visible effect, drunk chat. One drink adds 4000 ticks whatever the amount (FoodData.mul keeps intoxication); the cap is 36 h = 43,200 ticks, so chat slurs from the third jug of beer."
  - "Deferred in-game check (PROTOCOL 6.5): jug drinking itself is PAR-POT-07 (wave 26), which calls IntoxicationService.drink with the drained fluid; the screenshot check moves to PAR-SURV-13, which depends on PAR-POT-07 and this item."
  - "Done: core/intox (Intoxication, ChatSlur, Intoxicants) and paper/intox (IntoxicationModule: drunk chat on AsyncChatEvent, sober after death, /tfp debug intox; IntoxicationService for the jug drink). Tests: core IntoxicationGoldenTest 7 (128 TFC golden rows), IntoxicantsTest 2; paper IntoxicationMockBukkitTest 7. Divergences: chat random source, fixed maxIntoxicationTicks, intoxication kept across an End exit. Verification: deferred to PAR-SURV-13."
tests:
  - "core: golden for intoxication add/decay"
done_criteria:
  - "Deferred check tracked in PAR-SURV-13"
---

# PAR-SURV-08: Alcohol and intoxication

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
