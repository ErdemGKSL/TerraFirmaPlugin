---
id: PAR-FARM-02
title: "Fertilizers"
area: FARM
status: done
wave: 28
deps: [PAR-FARM-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/data/Fertilizer.java"
  - "src/generated/resources/data/tfc/tfc/fertilizer"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/farm/fertilizer/**"
  - "paper/src/test/java/dev/tfp/paper/farm/fertilizer/**"
ui: false
in_game_steps:
  - "ce item give Bot tfp:pure_nitrogen; use on farmland; screenshot actionbar"
plan:
  - "Right-click farmland/crop with a fertilizer item adds nutrients per the data (FarmlandRules.addNutrients); items consumed."
  - "Done: paper farm/fertilizer (Fertilizers data lookup, FertilizerUse pure plan, FertilizerListener on farmland and living crops, FertilizerModule) + 4 tests; ledger row and divergence (vanilla dust particles, sand sound). Pending in-game check (container cannot run client); the screenshot step is tracked in PAR-FARM-20."
tests:
  - "paper: nutrient add"
done_criteria:
  - "Pure nitrogen raises N in the readout: deferred check tracked in PAR-FARM-20"
---

# PAR-FARM-02: Fertilizers

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
