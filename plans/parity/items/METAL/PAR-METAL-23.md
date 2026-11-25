---
id: PAR-METAL-23
title: "Crucible molds: pour into and out of the crucible, output slot, held crucible tooltip"
area: METAL
status: done
wave: 34
deps: [PAR-METAL-12, PAR-POT-05]
size: M
aliases: ["follow-up of PAR-METAL-12"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/crucible/**"
  - "core/src/test/java/dev/tfp/core/crucible/**"
  - "paper/src/main/java/dev/tfp/paper/crucible/**"
  - "paper/src/test/java/dev/tfp/paper/crucible/**"
ui: true
in_game_steps:
  - "melted crucible with an ingot mold in the output slot: the mold fills; a molten mold in an input slot pours back; screenshot"
plan:
  - "PAR-METAL-12 left the output slot closed (CrucibleDevice.Services.output) and pours nothing. Use CrucibleRules.Pour/pourTick/outputTick and the mold items of PAR-POT-05 (MoldItems.pourStep): molten molds in input slots drain into the alloy at the pour rate (fast pour on shift), the output slot's mold fills one mB per tick while the alloy is molten, with the temperature set from the crucible."
  - "Write the alloy to the dropped crucible item with AlloyPdc so the existing tooltip shows the contents (also PAR-METAL-22's crucible line); apply TFC's burnt_to_a_crisp trait to food left by a melt."
  - "Done: PAR-METAL-23 commit. CrucibleData.pour (saved TFC lastFillTicks/fastPourTicks/fastPourSlot), CrucibleDevice.moldPass (lazy pour into the alloy and output mold fill after the heat replay) and pourFaster, CrucibleMolds adapter over MoldItems + AlloyRecipes (lazy: mold module loads after), shift-click on a molten input mold = fast pour, CrucibleDrops (tfp:alloy on the dropped crucible item for the tooltip), burnt_to_a_crisp on melted food. Tests: core CrucibleSimTest +1, paper CrucibleCatchUpMockBukkitTest +4, CrucibleDropsMockBukkitTest 1. Pending in-game check (container cannot run client); steps kept in in_game_steps."
tests:
  - "paper: a mold in the output slot fills from a molten alloy by catch-up; a molten mold in an input pours"
done_criteria:
  - "Crucible fills a mold in its output slot"
---

# PAR-METAL-23: Crucible molds

Follow-up of PAR-METAL-12. Follow `plans/parity/PROTOCOL.md`.
