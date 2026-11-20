---
id: PAR-CLIM-27
title: "Climate instrument blocks: CraftEngine entries, hitbox demands, models and animated parts"
area: CLIMATE
status: open
wave: 35
deps: [PAR-CLIM-09]
size: M
aliases: ["follow-up of PAR-CLIM-09 (2026-11-19): the rules and device kinds exist, the blocks do not"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/AnemometerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/VaneBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CalendarClockBlock.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/AnemometerBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/VaneBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/CalendarClockBlockEntityRenderer.java"
  - "src/main/resources/assets/tfc/blockstates/thermometer.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/instrument/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/instrument/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/instrument/**"
  - "paper/src/main/java/dev/tfp/paper/instrument/**"
  - "paper/src/test/java/dev/tfp/paper/instrument/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:thermometer, tfp:anemometer, tfp:vane, tfp:calendar_clock"
  - "place each, right-click, compare the action bar with tfp debug chunk"
plan:
  - "UNBLOCKED (worker, 2026-11-20): files_owned widened to the shape table and the carrier pin test (shared: edit only the instrument rows, demands and pins)."
  - "CE entries for tfp:thermometer (facing x power 0..15 x attached; TFC's 11 needle models), tfp:anemometer and tfp:vane (attached_wind_devices), tfp:calendar_clock (facing x mode) with the tfp:device behaviour, TFC shapes from the four block classes (new HitboxDemands rows and TfcShapes specs), loot self, support rules (canSurvive: thermometer on a sturdy face or thermometer_readable, vane on top of an anemometer or a centre support)."
  - "Anemometer cups, vane and clock hands: TFC draws them with Java entity models; build bbmodel or item-display parts with model-authoring and rotate them with entity_renderer on a slow sweep, no ticking."
  - "Register InstrumentSources for the firebox, crucible, blast furnace, barrel and bloomery (thermometer_readable and clock_readable tags)."
tests:
  - "datagen: the four entries on the real checkout; paper: placement and support rules"
done_criteria:
  - "All four place, survive correctly and show correct readouts in game"
---

# PAR-CLIM-27: Climate instrument blocks: CraftEngine entries, hitbox demands, models and animated parts

> Claimed: session_011osUp5o449zjy3Qcb6jpEk, 2026-11-20

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
