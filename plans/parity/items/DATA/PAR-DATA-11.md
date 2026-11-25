---
id: PAR-DATA-11
title: "Paintings, armour trims, misc data registries"
area: DATA
status: done
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

> see_not_parity: NP-CLIENT-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Pending in-game check (container cannot run client)

Done: datagen `registry/PaintingsAndTrims` ships TFC's 5 painting variants (+ textures, titles, `placeable` tag) and 14 trim materials (+ palettes, `armor_trims` atlas, lang) in the bootstrap datapack and pack; `/tfp trim` accepts the TFC materials (`TrimCommands.TFC_MATERIALS`). Tests: `PaintingsAndTrimsTest` (3), `TrimTfcMaterialsTest` (1). Follow-up: `provides_trim_material` on the gem and ingot items.
