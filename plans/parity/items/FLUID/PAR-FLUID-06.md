---
id: PAR-FLUID-06
title: "River current in-game verification: a boat and a dropped item drift downstream"
area: FLUID
status: done
wave: 10
deps: [PAR-FLUID-03]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/fluids/RiverWaterFluid.java"
files_owned:
  - "docs/verification/PAR-FLUID-06/**"
ui: true
in_game_steps:
  - "find a river: tp Bot along a visible river (or try candidate columns with tfp debug water Bot until it answers `river water, river flow <dir>`); stand on the bank"
  - "tfp debug water Bot with Bot in the river water: note the flow (e = +x, n = -z, w = -x, s = +z)"
  - "summon minecraft:oak_boat on the river surface; drop a stick into the river; record 3 frames 2 s apart (GUI scale 2)"
plan:
  - "Deferred in-game check of PAR-FLUID-03 (PROTOCOL §6.5): the worker session that built PAR-FLUID-03 had no subagent tool to run the headless server and client, so the screenshot check moved here. Run it on the current branch; a failure is fixed in PAR-FLUID-03's files (`core/.../worldwater/**`, `paper/.../worldwater/**`) through a follow-up."
  - "Done: PAR-FLUID-06 verification on Paper 1.21.11 + vanilla client (river at x 1456..1480, z 1470..1496): the boat drifts with the ssw/sww flow at 0.126 blocks/tick, the stick at about 0.43 (vanilla item drag), a swimming player keeps x/z; no code change; :core:test :paper:test :tools:datagen:test green; evidence docs/verification/PAR-FLUID-06/"
tests:
  - "none (in-game check only)"
done_criteria:
  - "The boat and the stick move with the reported flow across the 3 frames (an empty boat drifts at up to about 0.13 blocks per tick); a player swimming in the river is not pushed by the plugin"
---

# PAR-FLUID-06: River current in-game verification: a boat and a dropped item drift downstream

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
