---
id: PAR-METAL-29
title: "Vessels take molten metal from the charcoal forge's extra slots and the crucible's output slot (follow-up of PAR-METAL-22, -23, -25)"
area: METAL
status: done
wave: 34
deps: [PAR-METAL-22, PAR-METAL-23, PAR-METAL-25]
size: S
aliases: ["follow-up of PAR-METAL-25 (vessels in extra slots are not poured) and PAR-METAL-23 (output slot accepts only fired molds); asked as PAR-METAL-28, whose id is taken by the anvil remote work"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Helpers.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/component/mold/Vessel.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/**"
  - "paper/src/test/java/dev/tfp/paper/charcoalforge/**"
  - "paper/src/main/java/dev/tfp/paper/crucible/**"
  - "paper/src/test/java/dev/tfp/paper/crucible/**"
  - "paper/src/main/java/dev/tfp/paper/vessel/VesselItems.java"
  - "paper/src/test/java/dev/tfp/paper/vessel/VesselMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "`VesselItems.pour(vessel, fluid, amount, sourceTemperature)`: `fill` then TFC's `addTemperatureFromSourceWithHeatCapacity` (the vessel's capacity from before the fill, the fluid's specific heat times the filled amount)."
  - "`CrucibleMolds`: `isVessel`, `isContainer` (mold or vessel) and `pour` (a vessel takes the whole fitting amount at once, a mold one mB per step); `lazy`/`of` take the vessels."
  - "Forge `melted` pours into every container of slots 10..13 in order through `pour`; the crucible's output slot accepts and fills vessels through `isContainer` and `pourStep` (one mB per tick)."
  - "The held crucible's `tfp:alloy` (PAR-METAL-12 follow-up) was already written by PAR-METAL-23's `CrucibleDrops`; the ledger row is closed."
tests:
  - "paper: VesselMockBukkitTest (pour mixes the temperature), CharcoalForgeMockBukkitTest (vessel after a full mold, vessel alone), CrucibleCatchUpMockBukkitTest (vessel in the output slot)"
done_criteria:
  - "A small vessel in an extra forge slot or the crucible's output slot fills with the melted or molten metal and heats by the mix"
---

Done: PAR-METAL-29 `VesselItems.pour`, `CrucibleMolds.isVessel/isContainer/pour` with vessel-aware `lazy`/`of`, forge `melted` and crucible `moldPass` and output predicate use them. Tests: `VesselMockBukkitTest` +1, `CharcoalForgeMockBukkitTest` +2, `CrucibleCatchUpMockBukkitTest` +1. No UI. Not done: a molten vessel in a crucible input slot draining into the alloy (TFC `IMold` covers vessels).

# PAR-METAL-29: Vessels take molten metal from the forge's extra slots and the crucible's output slot

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
