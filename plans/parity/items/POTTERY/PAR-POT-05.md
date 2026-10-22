---
id: PAR-POT-05
title: "Molds: filling from a vessel/crucible, solidifying, breaking out the casting"
area: POTTERY
status: open
wave: 25
deps: [PAR-POT-04]
size: M
aliases: ["M3-POT-02 (molds)", "M4-MET-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/MoldItem.java"
  - "src/main/java/net/dries007/tfc/common/component/mold/Mold.java"
  - "src/main/java/net/dries007/tfc/common/container/MoldLikeAlloyContainer.java"
  - "src/main/java/net/dries007/tfc/common/recipes/CastingRecipe.java"
  - "src/generated/resources/data/tfc/recipe/casting"
files_owned:
  - "core/src/main/java/dev/tfp/core/mold/**"
  - "core/src/test/java/dev/tfp/core/mold/**"
  - "core/src/test/resources/dev/tfp/core/mold/**"
  - "paper/src/main/java/dev/tfp/paper/mold/**"
  - "paper/src/test/java/dev/tfp/paper/mold/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/mold/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/mold/**"
  - "core/src/main/java/dev/tfp/core/vessel/Mold*"
  - "core/src/test/java/dev/tfp/core/vessel/Mold*"
  - "core/src/main/java/dev/tfp/core/casting/**"
  - "core/src/test/java/dev/tfp/core/casting/**"
ui: true
in_game_steps:
  - "vessel with molten copper (PAR-POT-04)"
  - "sneak right-click with an axe head mold in offhand"
  - "wait; craft the mold; screenshot"
plan:
  - "Mold fill state on the item (fluid via PAR-INFRA-04), lazy solidify by heat; crafting the cooled mold gives the casting with the break chance (CastingRules); fill visuals via item model range_dispatch on fill level."
tests:
  - "paper: MockBukkit pour and break roll"
  - "core: CastingRulesTest exists"
done_criteria:
  - "Native copper path: vessel -> axe head mold -> copper axe head (self-test `casting.copper_axe`)"
---

# PAR-POT-05: Molds: filling from a vessel/crucible, solidifying, breaking out the casting

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
