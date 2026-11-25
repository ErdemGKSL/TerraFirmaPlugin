---
id: PAR-POT-03
title: "Pit kiln: straw and log stages, lighting, lazy firing"
area: POTTERY
status: done
wave: 24
deps: [PAR-POT-01, PAR-POT-02, PAR-HEAT-05, PAR-HEAT-04, PAR-INFRA-13]
size: M
aliases: ["M3-POT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/kiln/**"
  - "core/src/test/java/dev/tfp/core/kiln/**"
  - "core/src/test/resources/dev/tfp/core/kiln/**"
  - "paper/src/main/java/dev/tfp/paper/kiln/**"
  - "paper/src/test/java/dev/tfp/paper/kiln/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/kiln/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/kiln/**"
  - "content/generated/content/craftengine/tfp/configuration/kiln/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/kiln rules (layers, progress, evaluate) golden-tested."
  - "Straw on a placed-item block converts it to a pit kiln (stages 0-7 straw, 8-15 logs); fire above lights it (FireLightable); evaluate lazily on interaction/chunk load; contents fired via HeatingService; failure when the structure becomes invalid (first invalid tick recorded)."
  - "Built (2026-11-25): core/kiln PitKilnState (TFC NBT keys, 20 slots: 4 contents, 8 straw, 8 logs), PitKilnFiring (the contents once from the outcome: own recipe at its first tick, TFC's re-cache on the last tick of a fired kiln, final temperature at the last cooked tick; no-heat items untouched), PitKilnLayers helpers (strawValue, stage from a placed item, add slots, empty-hand take, renderer stage <= 9). paper/kiln: PitKilnDevice (kind tfp:pit_kiln, stage 0..16, DROP; lighting clears the fuel so DROP equals TFC ejectInventory), PitKilns (fromPlaced, use, tryLight with diagonals, evaluate = updateShape + serverTick checks in TFC order, convert to a placed item), PitKilnListener (LOW, main hand, vanilla sneak rule, deny when used so no thatch/log block is placed and the placed-item hold drops), KilnClock (global 20-tick task hands due kilns to their regions), chunk-load catch-up through the lazy block index, FireLightable (strong start at stage 15), PitKilnModule (config pit-kiln.ticks / pit-kiln.temperature read raw)."
  - "Not here (files outside files_owned): the CraftEngine block (hitbox demands, models, Datagen line), the shipped config keys, the itest case kiln.fire and the in-game check -> follow-up PAR-POT-14 (in_game_steps and the self-test criterion moved there, PROTOCOL §6.5)."
  - "Done: PAR-POT-03 pit kiln: core PitKilnState/PitKilnFiring + layer helpers, paper/kiln device, listener, lazy evaluation, clock, chunk catch-up, lightable, module. Tests: core PitKilnFiringTest 6 (plus the existing kiln rule tests 14), paper PitKilnMockBukkitTest 14. Ran: ./gradlew :core:test --tests dev.tfp.core.kiln.* and :paper:test --tests dev.tfp.paper.kiln.* --tests dev.tfp.paper.placed.* (green); full check not run (other modules' license headers, module ids, BootstrapTest debug list fail outside this item). Ledger row + 6 divergence rows. Pending in-game check (container cannot run client); tracked in PAR-POT-14 (docs/verification/PAR-POT-03/)."
tests:
  - "paper: MockBukkit stage progression and lazy fire result"
done_criteria:
  - "Deferred check tracked in PAR-POT-14"
---

# PAR-POT-03: Pit kiln: straw and log stages, lighting, lazy firing


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
