---
id: PAR-HEAT-34
title: "Grill and pot burn-out after a calendar jump: the lit look turns off without a click, as the firepit since PAR-HEAT-33"
area: HEAT
status: open
wave: 18
deps: [PAR-HEAT-33]
size: S
aliases: ["follow-up of PAR-HEAT-33 (the grill and pot effects are outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/grill/GrillModule.java"
  - "paper/src/main/java/dev/tfp/paper/pot/PaperPotEffects.java"
  - "paper/src/main/java/dev/tfp/paper/pot/PotModule.java"
  - "paper/src/test/java/dev/tfp/paper/grill/GrillCalendarJumpMockBukkitTest.java"
  - "paper/src/test/java/dev/tfp/paper/pot/PotCalendarJumpMockBukkitTest.java"
ui: false
plan:
  - "PAR-HEAT-33 gave the firepit `PaperFirepitEffects.BurnOutWatch`: each burn-out callback is due in server ticks and on the device clock, and a global sweep every 20 ticks fires the ones a calendar jump (`/tfp time add`, the sleep skip) moved past. The grill still builds its `PaperFirepitEffects` with the 4-argument constructor (server ticks only) and the pot has its own server-tick callback, so a jump that burns their fuel out leaves the lit look until the next click or chunk load."
  - "Fix: the grill passes the device clock (`context.clock().get().playerTicks()`) to the 5-argument `PaperFirepitEffects` constructor and starts/cancels its sweep in enable/disable; the pot's `callbackIn` delegates to a `BurnOutWatch` started by its module."
tests:
  - "MockBukkit per device: lit with one fuel, the clock advanced past the fuel without server ticks, one sweep shows the unlit state; the server-tick task later does nothing"
done_criteria:
  - "Grill and pot turn unlit after a calendar jump past the fuel without a click (MockBukkit)"
---

# PAR-HEAT-34: Grill and pot burn-out after a calendar jump

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
