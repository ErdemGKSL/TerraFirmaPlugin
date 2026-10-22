---
id: PAR-HEAT-13
title: "Fire behaviour: fire arrows, dousing, fire spread limits"
area: HEAT
status: open
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
tests:
  - "paper: MockBukkit projectile hit"
done_criteria:
  - "Config respected in-game"
---

# PAR-HEAT-13: Fire behaviour: fire arrows, dousing, fire spread limits

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
