---
id: PAR-MECH-06
title: "Quern automation"
area: MECH
status: done
wave: 50
deps: [PAR-MECH-01, PAR-PROC-04]
size: S
aliases: ["M6-MECH-03 (quern)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/RotationSinkBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/quern/powered/**"
  - "paper/src/test/java/dev/tfp/paper/quern/powered/**"
ui: false
in_game_steps:
  - "axle into quern; hopper of grain; wait; screenshot"
plan:
  - "Powered quern grinds lazily from network speed; hopper feed."
  - "Done: `paper/quern/powered`: `PoweredQuern` (TFC's recipeTimer arithmetic: 90 units, |speed| * 90/2pi per tick, 1 per tick off the network; ticks to finish by the same float subtraction), `PoweredQuernService` (a watched quern is looked at every 10 ticks, TFC's idle check, or at its finish tick: settle a due grind, chain the next from the planned finish, re-plan a running grind when the rate changes, park it on a standing network), `PoweredQuernAccess` seam, `PaperPoweredAccess`, `PoweredQuernIndex` (chunk list of watched querns for chunk load), `PoweredQuernModule` (id quern_powered, one line in the service file, the device access hook of tfp:quern). A powered grind is stored as the quern's own `QuernState` start = finish - 90, so `QuernDevice.settle` finishes it with TFC's handstone damage, sounds and output merge. Hoppers feed and empty it through PAR-INFRA-60's quern sides. Tests: PoweredQuernTest 8, PoweredQuernServiceTest 12 (fake quern and clock: 3 grinds in 3 grind-times, 37-tick grinds not aligned to the looks, long gap = one finish then a restart from now, speed change, lost axle, standing network, no handstone, hopper refill, broken quern). The quern is not a rotation node (no SINK MechanicalBlock in core/rotation, which PAR-MECH-01 owns): it reads the node above that connects down. Handstone spin, start sound and particles, the in-game check and the hopper screenshot: PAR-MECH-13."
tests:
  - "paper: powered grind catch-up"
done_criteria:
  - "An axle-driven quern grinds a hopper of grain (MockBukkit-free: PoweredQuernServiceTest drives a fake quern through the real service; the in-game screenshot is deferred: deferred check tracked in PAR-MECH-13)"
---

# PAR-MECH-06: Quern automation


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
