---
id: PAR-HEAT-16
title: "Item heat of list ingredients: metal tools and parts (item_heat parts_*) cannot be heated"
area: HEAT
status: open
wave: 12
deps: [PAR-HEAT-01]
size: S
aliases: ["found by PAR-HEAT-14's in-game check 2026-11-13: a copper pickaxe heated with tfp debug heat reads 0 C"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/heat/HeatDefinition.java"
  - "src/generated/resources/data/tfc/tfc/item_heat/copper/parts_100.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/heat/ItemHeatDefinitions.java"
  - "core/src/test/java/dev/tfp/core/heat/ItemHeatDefinitions*"
ui: false
in_game_steps: []
plan:
  - "Current: `ItemHeatDefinitions.fromRegistry` reads only `Ingredient.Item` and `Ingredient.Tag`; TFC's `item_heat/<metal>/parts_*.json` use a list ingredient (decoded as `Ingredient.AnyOf`), so every metal tool, tool head, armour part and the other parts listed there has heat capacity 0: `tfp debug heat` on a copper pickaxe sets the record but reads 'temperature now: 0.0 C', the item neither glows (PAR-HEAT-01), shows a heat line (PAR-HEAT-02) nor a heat bar (PAR-HEAT-14), and cannot be heated in devices."
  - "Expand `AnyOf` (and nested item/tag children) into its member items, keeping TFC's first-definition-wins order (`HeatDefinition` lookup by ingredient test in registry order)."
tests:
  - "core: fromRegistry with a list ingredient of items and tags gives each member the definition; copper pickaxe has capacity 2.857143 from the real content"
done_criteria:
  - "tfp:metal/pickaxe/copper has TFC's heat capacity; tests green"
---

# PAR-HEAT-16: Item heat of list ingredients

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
