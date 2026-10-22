---
id: PAR-METAL-02
title: "Fire ingot mold and bell casting; casting crafting"
area: METAL
status: open
wave: 28
deps: [PAR-POT-05]
size: S
aliases: ["M4-MET-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/CastingCraftingRecipe.java"
  - "src/main/java/net/dries007/tfc/common/items/MoldItem.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/mold/special/**"
  - "paper/src/test/java/dev/tfp/paper/mold/special/**"
ui: true
in_game_steps:
  - "vessel with molten brass; pour into bell mold; screenshot result"
plan:
  - "Fire ingot mold (reusable, break chance 0.01), bell mold (bronze/brass bells) as MoldKind entries on the Paper glue."
tests:
  - "paper: break chance roll"
done_criteria:
  - "Casting a brass bell in-game"
---

# PAR-METAL-02: Fire ingot mold and bell casting; casting crafting

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
