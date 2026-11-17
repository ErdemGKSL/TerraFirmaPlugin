---
id: PAR-HEAT-32
title: "Firebox kiln and heat consumers: placed items heat in an insulated chamber, the crucible above heats (self-test), hot floor, comparator"
area: HEAT
status: open
wave: 34
deps: [PAR-HEAT-31, PAR-POT-01, PAR-METAL-12]
size: M
aliases: ["follow-up of PAR-HEAT-12 (its crucible self-test and the kiln heating need the crucible and placed items, which do not exist yet; PROTOCOL 3)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/firebox/kiln/**"
  - "core/src/test/java/dev/tfp/core/firebox/kiln/**"
  - "paper/src/main/java/dev/tfp/paper/firebox/kiln/**"
  - "paper/src/test/java/dev/tfp/paper/firebox/kiln/**"
  - "itest/src/main/java/dev/tfp/itest/cases/FireboxCrucibleCase.java"
  - "docs/verification/PAR-HEAT-32/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:firebox[lit=false]; place a crucible on it; put 4 coal into the firebox and light it with a firestarter; open the crucible: its temperature climbs toward 1415 C; screenshot both screens"
  - "build a 3 x 3 x 3 chamber of fire bricks over a firebox with placed items (clay vessels) inside; light the firebox; the firebox screen's status row reads 'Heating 27 blocks in ...'; after /tfp debug time skip past the time to heat the vessels are fired; screenshot"
  - "survival Bot stands on a lit firebox: 1 hot-floor damage per hurt cooldown; creative: none"
done_criteria:
  - "Firebox under a crucible heats it (self-test FireboxCrucibleCase on Paper and Folia)"
  - "Placed items inside an insulated kiln over a lit firebox reach the firebox temperature and fire after TFC's getTimeToHeat, lazily (performHeating / performSkippedHeating)"
  - "A lit firebox hurts entities standing on it (hot floor) per game mode; the comparator reads TFC's getAnalogOutputSignal"
plan:
  - "Deferred from PAR-HEAT-12: the firebox runtime is done (core/firebox FireboxSim lazy serverTick with the heat runs, FireboxKiln flood fill, FireboxData timer; paper/firebox FireboxDevice, HeatConsumers registry: a device registers HeatConsumers.Consumer for its block id and receives the firebox's heat runs (from, to, temperature) on its region thread, re-supplied every FireboxDevice.HEAT_PERIOD ticks while lit)."
  - "Crucible: PAR-METAL-12's crucible registers its HeatConsumers.Consumer (TFC IHeatConsumer.setTemperature per tick of the run, CrucibleTemperature) and the self-test places a firebox under a crucible, lights it and checks the crucible temperature rises. Re-check CrucibleTemperature.setTemperature against TFC (TFC sets the target and the stability window, not the temperature)."
  - "Kiln: with PAR-POT-01's placed items, apply TFC performHeating (addTemp to each heatable placed item, recipes every 20 ticks) and performSkippedHeating (setTemperatureIfWarmer, recipe, cooling for the ticks after the fuel ran out) to FireboxKiln's positions from the firebox's catch-up, without a per-tick pass (lazy: the heating span is known from the firebox's heat runs and timer)."
  - "Hot floor (FireboxBlock.stepOn: hotFloor 1 while lit) and the comparator (FireboxSim.analogOutput, fireboxEnableAutomation) through whatever PAR-HEAT-30 and PAR-INFRA-60 build for the firepit."
tests:
  - "itest: FireboxCrucibleCase; paper: kiln heating of placed items (MockBukkit)"
---

# PAR-HEAT-32: Firebox kiln and heat consumers

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
