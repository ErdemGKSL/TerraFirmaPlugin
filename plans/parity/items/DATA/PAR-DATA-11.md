---
id: PAR-DATA-11
title: "Paintings, armour trims, misc data registries"
area: DATA
status: open
wave: 38
deps: []
size: S
aliases: []
tfc_refs:
  - "src/generated/resources/data/tfc/painting_variant"
  - "src/main/resources/data/tfc/trim_material"
files_owned:
  - "paper/src/main/resources/datapack/data/tfp/painting_variant/**"
  - "paper/src/main/resources/datapack/data/tfp/trim_material/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/registry/**"
ui: true
in_game_steps:
  - "give Bot painting; place; screenshot"
plan:
  - "Ship TFC paintings and trim materials (gems/metals) via the datapack with textures in the pack."
tests:
  - "datagen counts"
done_criteria:
  - "A TFC painting can be placed"
---

# PAR-DATA-11: Paintings, armour trims, misc data registries

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
