---
id: PAR-SURV-03
title: "Eating completion: undefined foods, peaceful, rotten advancement hook, in-game check"
area: SURV
status: open
wave: 6
deps: [PAR-SURV-01]
size: S
aliases: ["M3-SURV-02", "gaps: M3-SURV-02 eating"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/player/Nutrition*"
  - "paper/src/test/java/dev/tfp/paper/player/Nutrition*"
  - "core/src/main/java/dev/tfp/core/food/**"
  - "core/src/test/java/dev/tfp/core/food/**"
ui: true
in_game_steps:
  - "effect give Bot minecraft:hunger 10 5"
  - "ce item give Bot tfp:food/mutton"
  - "eat it"
  - "tfp debug player Bot"
plan:
  - "Current: NutritionEatListener records nutrition, applies rotten effects and vanilla food sync."
  - "Vanilla foods without a TFC definition: block eating vanilla food items that TFC replaces, or map them (divergence row)."
  - "Fire the EAT_ROTTEN_FOOD advancement criterion via PAR-DATA-03's trigger API (stub call now)."
  - "In-game check: eat a TFC food, `tfp debug player Bot` shows the nutrition record; persists across restart."
tests:
  - "paper: NutritionEatingTest additions"
done_criteria:
  - "In-game eat recorded and persisted"
---

# PAR-SURV-03: Eating completion: undefined foods, peaceful, rotten advancement hook, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
