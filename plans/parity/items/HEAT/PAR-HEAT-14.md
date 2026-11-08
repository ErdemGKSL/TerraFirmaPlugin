---
id: PAR-HEAT-14
title: "Item bars: the heat bar on hot items (TFC BarSystem HEAT) as a packet-time durability-style bar, with the shared item-bar layer"
area: HEAT
status: open
wave: 11
deps: [PAR-HEAT-01, PAR-INFRA-05]
size: M
aliases: ["parity audit 2026-11-08: client/BarSystem (heat and egg bars) and ItemStackMixin isBarVisible/getBarColor/getBarWidth named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/BarSystem.java"
  - "src/main/java/net/dries007/tfc/mixin/ItemStackMixin.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/Heat.java"
  - "src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java"
  - "src/main/java/net/dries007/tfc/config/ClientConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/itembar/**"
  - "core/src/test/java/dev/tfp/core/itembar/**"
  - "paper/src/main/java/dev/tfp/paper/itembar/**"
  - "paper/src/test/java/dev/tfp/paper/itembar/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper; tfp debug heat Bot 540; screenshot the hotbar slot at GUI scale 2 and 3 (bar about half width: 13 * 540 / copper's heating temperature)"
  - "tfp debug heat Bot 0; screenshot the slot (no bar)"
  - "Give Bot a damaged metal pickaxe and heat it; screenshot: the vanilla durability bar wins, as TFC (the heat bar only shows when the item has no other bar)"
plan:
  - "Current (audit 2026-11-08): hot items show a tooltip line and (PAR-HEAT-01) a glow, but TFC also draws a durability-style bar on every hot item: width clamp(round(13 * temperature / heating recipe temperature), 1, 13), or / Heat.maxVisibleTemperature() without a recipe; colour = the Heat level colour; shown only while hot and only when the item has no bar of its own (`createDefaultItem` check); client option displayItemHeatBars (default true)."
  - "core/itembar: pure `ItemBar` registry in TFC's order (heat first, egg next: PAR-FAUNA-23 adds it), each giving visible/width 0..13/colour from the lazy item state; the 'other bar wins' rule; golden widths."
  - "Spike first, then build: vanilla clients draw the bar from minecraft:damage / minecraft:max_damage. On the outgoing item packet copy (the PAR-INFRA-05 decoration pipeline, never the server stack) set max_damage = 13 and damage = 13 - width. Check on 1.21.11 and 26.3 whether a stackable item (max_stack_size > 1) with max_damage is accepted by the client codec and keeps its count; if not, apply the bar only to single items and record the rest. Strip both components on creative-mode return (creative inventory sends stacks back) and in the recipe book/merchant paths as PAR-HEAT-01 does for its glow float."
  - "Colour: vanilla colours the bar by the damage fraction (green to red), not by TFC's heat colour: Divergences row. Refresh happens on the next item packet (NP-CLIENT-02)."
  - "Server config `item-bars.heat` (TFC displayItemHeatBars, default true) through TfpConfig (NP-CLIENT-04: server-wide instead of per player)."
tests:
  - "core: ItemBarGoldenTest (heat widths for recipe and no-recipe items, the 1..13 clamp, cold item invisible, other bar wins)"
  - "paper: packet copy gets max_damage/damage, the server stack does not; creative return strips them; config off shows nothing"
done_criteria:
  - "A hot ingot shows a bar whose width follows TFC; a cold one shows none; a damaged tool keeps its durability bar; tests green; screenshots pass"
---

# PAR-HEAT-14: Item bars: the heat bar on hot items

> see_not_parity: NP-CLIENT-02, NP-CLIENT-04 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
