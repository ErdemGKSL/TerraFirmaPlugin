---
id: PAR-MECH-25
title: "Crankshaft in game: a creative motor or water wheel turns a crankshaft that drives a bellows into a charcoal forge (PAR-MECH-21 in-game check)"
area: MECH
status: open
wave: 55
deps: [PAR-MECH-21]
size: S
aliases: ["moved from PAR-MECH-21 (2026-11-25, PROTOCOL 6.5): no headless client run in that session"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java"
files_owned:
  - "docs/verification/PAR-MECH-25/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:creative_motor, tfp:wood/axle/oak, tfp:crankshaft, tfp:metal/rod/steel, tfp:bellows, tfp:charcoal_forge setup; place the motor (axis z) and two axles going south; stand so that the crankshaft placed south of the last axle faces east (TFC: facing = the clockwise side of the look, so look north; the sink face is the facing's counter-clockwise side, north, toward the axle); right-click the base with a steel rod: the shaft grows east; place the bellows two blocks east of the base facing east, a lit charcoal forge at its front offset; click the motor's east face 8 times (30 rpm)"
  - "/tfp debug rotation on the base: it is listed as crankshaft at 30 rpm; the forge's air rises without clicking the bellows (/tfp debug air or the forge readout), the blow sound plays every ~0.8 s; break an axle: the pushes stop; screenshots at GUI scale 2 of the line, the base with its wheel and the shaft, and the forge heat level"
  - "break the shaft: it drops a steel rod; break the base: it drops the crankshaft and the shaft goes with it"
plan:
  - "Created by PAR-MECH-21 (deferred in-game check, PROTOCOL 6.5): the CE block tfp:crankshaft, its sink node (paper/rotation CrankSinks) and the CrankRotation service are done and tested off-server. A failure is fixed through PAR-MECH-21's files (tools/datagen crankshaft, paper/rotation, core/rotation MechanicalBlock) or a follow-up."
tests:
  - "none (in-game check)"
done_criteria:
  - "A water wheel (or creative motor) turns a crankshaft that drives a bellows into a charcoal forge in game (frames in docs/verification/PAR-MECH-25/)"
---

# PAR-MECH-25: Crankshaft in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
