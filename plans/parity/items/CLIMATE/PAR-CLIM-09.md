---
id: PAR-CLIM-09
title: "Climate instruments: thermometer, anemometer, vane, calendar clock"
area: CLIMATE
status: open
wave: 34
deps: [PAR-INFRA-02, PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/AnemometerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/VaneBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CalendarClockBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ThermometerBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/AnemometerBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/VaneBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CalendarClockBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/instrument/**"
  - "core/src/test/java/dev/tfp/core/instrument/**"
  - "core/src/test/resources/dev/tfp/core/instrument/**"
  - "paper/src/main/java/dev/tfp/paper/instrument/**"
  - "paper/src/test/java/dev/tfp/paper/instrument/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/instrument/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/instrument/**"
  - "content/generated/content/craftengine/tfp/configuration/instrument/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:thermometer"
  - "place it, right-click, compare with tfp debug chunk"
plan:
  - "CE blocks with entity_renderer parts (needle/cups/vane) whose rotation is set when a player looks or on a slow sweep (no ticking); thermometer shows the TFC temperature at the block, anemometer the wind speed, vane the wind direction (core/power WindSpeed), the clock the calendar time."
  - "Readout as actionbar on right-click (TFC shows block tooltips via Jade: divergence row)."
tests:
  - "core: needle angle golden from TFC block entity math"
done_criteria:
  - "All four place and show correct readouts"
---

# PAR-CLIM-09: Climate instruments: thermometer, anemometer, vane, calendar clock

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
