---
id: PAR-PROC-11
title: "Composter"
area: PROC
status: done
wave: 11
deps: [PAR-INFRA-02, PAR-INFRA-11]
size: S
aliases: ["M3-PROC-05"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/compost/**"
  - "core/src/test/java/dev/tfp/core/compost/**"
  - "core/src/test/resources/dev/tfp/core/compost/**"
  - "paper/src/main/java/dev/tfp/paper/compost/**"
  - "paper/src/test/java/dev/tfp/paper/compost/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/compost/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/compost/**"
  - "content/generated/content/craftengine/tfp/configuration/compost/**"
ui: false
in_game_steps: []
plan:
  - "Per-wood composter? (TFC: one composter); add greens/browns, lazy ready time, sneak extraction, rotten state and pest attraction (PAR-FAUNA-13), hopper automation, ready particles only on interaction."
  - "One composter (TFC registers one, not per wood). The CraftEngine block needs hitbox carriers from the selector (HardcodedCarrierTest), and HitboxDemands.java / tfc-shapes.json are outside this item's files_owned: the block entry, its carriers and the in-game check (in_game_steps and the screenshot) moved to the follow-up PAR-PROC-34 (PROTOCOL 3 + 6.5); ui set to false here."
  - "Done: core ComposterState (bin + sampled ready ticks, codec, catch-up settle; ComposterStateTest 5) on the existing CompostGoldenTest / CompostLazyVsSteppedTest / CompostDeterminismTest; paper/compost ComposterDevice (kind tfp:composter: TFC use order, sneak extraction above, messages, poison/rotten output, creative, standing-inside FAIL, block state stage/type sync, ripening callback, catch-up settle, hopper-below automation, output drop on removal), ComposterModule (TFC composter tags in getCompost order, food rot, chunk rainfall + horizontal composters for getReadyTicks), PaperComposterEffects, ComposterLang; ComposterMockBukkitTest 12; ledger row and divergences. In-game check deferred to PAR-PROC-34."
  - "Review follow-up: PaperComposterEffects.ripenAt keeps the due time per block, so an earlier request replaces the queued callback and a dropped one is replaced once past due (PaperComposterEffectsRipenTest 3); ledger corrected (TFC never random-ticks a rotten composter, so no infestation), hoe overlay listed under Not yet, standing-inside pass and rotten extraction recorded as divergences; PAR-PROC-34 gains TFC's block properties, the rainfall condition for the wait and drops PAR-PROC-11 from its deps (PROTOCOL 6.5)."
tests:
  - "core: CompostGoldenTest exists"
  - "paper: extraction"
done_criteria:
  - "Compost ready after composterTicks"
  - "Deferred check tracked in PAR-PROC-34"
---

# PAR-PROC-11: Composter

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
