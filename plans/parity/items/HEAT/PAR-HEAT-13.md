---
id: PAR-HEAT-13
title: "Fire behaviour: fire arrows, dousing, fire spread limits"
area: HEAT
status: done
wave: 35
deps: [PAR-HEAT-05]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/events/DouseFireEvent.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/fire/spread/**"
  - "paper/src/test/java/dev/tfp/paper/fire/spread/**"
ui: false
in_game_steps: []
plan:
  - "Fire arrows ignite blocks only when enableFireArrowSpreading; water splash/bucket douses TFC devices through the FireLightable SPI (douse)."
  - "Done: paper/fire/spread FireSpreadModule + FireSpreadListener (burning projectile hit starts a strong fire when tfp.fire.arrowSpreading is true; water potion douses fire, lit candles/campfires, burning entities); FireSpreadMockBukkitTest 3 tests. Pending in-game check (arrow and potion in a client); the config key is a JVM property (ledger divergence)."
tests:
  - "paper: MockBukkit projectile hit"
done_criteria:
  - "Config respected in-game"
---

# PAR-HEAT-13: Fire behaviour: fire arrows, dousing, fire spread limits


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
