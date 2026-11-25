---
id: PAR-MECH-13
title: "Powered quern in game: handstone spin, grind sounds and particles, hopper and axle screenshots"
area: MECH
status: open
wave: 52
deps: [PAR-MECH-06, PAR-MECH-11, PAR-MECH-12]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/QuernBlockEntityRenderer.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/quern/**"
  - "paper/src/test/java/dev/tfp/paper/quern/**"
  - "docs/verification/PAR-MECH-13/**"
ui: true
in_game_steps:
  - "an axle of the MECH-01 setup placed on top of a quern with a handstone; a hopper beside it (horizontal side) with grain, a hopper below it; creative motor at step 8: the handstone turns, grain becomes flour in the output and the lower hopper takes it, 3 grinds counted by the clock; step 0 stops it; breaking the axle lets the grind finish by hand; screenshot the turning handstone (10 frames)"
plan:
  - "PAR-MECH-06 starts and finishes powered grinds through the device state, with no effects: expose `PaperQuernEffects` start sound (QUERN_DRAG), the input item particles each tick while grinding, and the handstone's spin to the powered service (a `PoweredQuernAccess.started(key)` hook), the spin at the network angle (PAR-MECH-02 `RotationAnglesSpin`, TFC's `getRotationAngle`: -recipeTimer * speed while off the network)."
  - "Run the in-game steps and store the evidence."
tests:
  - "paper: effects fire once per powered grind start"
done_criteria:
  - "An axle-driven quern grinds a hopper of grain in game on 1.21.11 and 26.x, the handstone visibly turns (screenshots in docs/verification/PAR-MECH-13/)"
---

# PAR-MECH-13: Powered quern in game

> Created by PAR-MECH-06, which owns only `paper/quern/powered/**`: the effects live in `paper/quern/**`. Start after PAR-MECH-11 (axles on their carriers) and PAR-MECH-12 (a network speed on the wire).

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
