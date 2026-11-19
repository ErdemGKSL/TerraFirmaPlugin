---
id: PAR-POT-12
title: "Jug fluid overlay and the four glass bottle items"
area: POTTERY
status: open
wave: 27
deps: [PAR-POT-07]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/GlassBottleItem.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/resources/assets/tfc/models/item/ceramic/jug.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/glassbottle/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/glassbottle/**"
ui: false
in_game_steps: []
plan:
  - "PAR-POT-07's runtime already handles tfp:ceramic/jug and the four glass bottles, but datagen emits no CE item for the silica/hematitic/olivine/volcanic glass bottles (ItemCoverage owns them as PAR-POT-07) and the jug shows only its empty texture. Generate the four bottle CE items (TFC models, names, stack 1) and a jug fluid overlay (item model definition with a tint or per-fluid overlay layer as the bucket visuals do)."
tests:
  - "datagen: the four bottle ids exist as CE items; the coverage report no longer lists them as owned"
done_criteria:
  - "`ce item give` for each bottle works and a filled jug shows its fluid overlay"
---

# PAR-POT-12: Jug fluid overlay and the four glass bottle items

Follow-up of PAR-POT-07. Follow `plans/parity/PROTOCOL.md`.
