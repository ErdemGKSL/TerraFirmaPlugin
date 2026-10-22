---
id: PAR-HEAT-05
title: "Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules"
area: HEAT
status: open
wave: 12
deps: [PAR-INFRA-02]
size: M
aliases: ["M3-HEAT-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FirestarterItem.java"
  - "src/main/java/net/dries007/tfc/common/items/FlintAndPyriteItem.java"
  - "src/main/java/net/dries007/tfc/util/events/StartFireEvent.java"
  - "src/generated/resources/data/tfc/tfc/fuel"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/fire/**"
  - "paper/src/test/java/dev/tfp/paper/fire/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/fire/**"
  - "core/src/main/java/dev/tfp/core/fuel/**"
  - "core/src/test/java/dev/tfp/core/fuel/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:firestarter"
  - "place 3 sticks + 1 log on the ground (right-click)"
  - "hold right-click with the firestarter on them"
  - "tfp debug block"
plan:
  - "Current: core/fuel FuelTable, FuelBurn, FireStart golden-tested."
  - "Firestarter: vanilla `consumable` with use time (or a bow-like use) tracked by PlayerInteractEvent + use-duration; on finish roll FireStart (fireStarterChance) at the target block."
  - "StartFireEvent: an SPI `FireLightable` that devices register (firepit pile, log pile, pit kiln, charcoal forge, torch, candle, lamp); otherwise place vanilla fire if canSurvive."
  - "Firepit creation: firestarter on a pile of 1 log + 3 sticks on the ground (placed items or groundcover) creates the firepit as TFC."
  - "CE items for the firestarter and flint & pyrite."
tests:
  - "paper: MockBukkit use-duration completes and calls the SPI"
  - "core: FireStartGoldenTest unchanged"
done_criteria:
  - "In-game: a firestarter lights a stick+log pile into a firepit (self-test `fire.start`)"
---

# PAR-HEAT-05: Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
