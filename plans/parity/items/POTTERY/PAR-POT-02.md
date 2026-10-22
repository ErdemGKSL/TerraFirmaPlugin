---
id: PAR-POT-02
title: "Unfired and fired pottery items, clay knapping outputs"
area: POTTERY
status: open
wave: 23
deps: []
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/resources/assets/tfc/textures/item/ceramic"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/pottery/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/pottery/**"
  - "content/generated/content/craftengine/tfp/configuration/pottery/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/unfired_vessel"
  - "ce item give Bot tfp:ceramic/ingot_mold"
plan:
  - "Generate every unfired/fired ceramic item (incl. 16 glazed vessels and large vessels, all molds, fire bricks) as CE items with names, models, item_heat (pit kiln firing at 1399 via heating recipes) and sizes."
tests:
  - "tools/datagen: counts vs TFCItems"
done_criteria:
  - "`ce item give` for each ceramic family works"
---

# PAR-POT-02: Unfired and fired pottery items, clay knapping outputs

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
