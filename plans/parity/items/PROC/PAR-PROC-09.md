---
id: PAR-PROC-09
title: "Barrel GUI, fluid level visual, rain fill, barrel rack"
area: PROC
status: open
wave: 12
deps: [PAR-PROC-08, PAR-INFRA-03, PAR-CLIM-02]
size: M
aliases: ["M3-PROC-03 (GUI/visual)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/BarrelContainer.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/barrel/gui/**"
  - "paper/src/test/java/dev/tfp/paper/barrel/gui/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrelrack/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:wood/barrel/oak"
  - "fill half; right-click; screenshot GUI and block"
plan:
  - "GUI per catalogue `m3-proc-03-barrel` with tank overlay glyphs and seal button; fluid surface as an entity_renderer element tinted via tint_source (water/milk/brine colours); open barrels fill with rain lazily; horizontal barrels on a barrel rack with a tap (drain)."
tests:
  - "paper: tank glyph frame per level"
done_criteria:
  - "Screenshot of the barrel GUI and a half-full barrel showing liquid"
---

# PAR-PROC-09: Barrel GUI, fluid level visual, rain fill, barrel rack

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
