---
id: PAR-HUD-08
title: "Held item temperature popup with real values"
area: HUD
status: open
wave: 4
deps: []
size: S
aliases: ["gaps: tfp_held_temp returns 0"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
files_owned:
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClimateValues.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 900"
  - "screenshot centre popup"
plan:
  - "Publish the held item's current temperature (ItemHeatHooks, lazy) into HudSnapshot on the needs tick; popup shown only when > 0 C above ambient as TFC."
tests:
  - "integration/betterhud: placeholder returns the snapshot value"
done_criteria:
  - "Holding a 900 C ingot shows the popup with the TFC level text"
---

# PAR-HUD-08: Held item temperature popup with real values

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
