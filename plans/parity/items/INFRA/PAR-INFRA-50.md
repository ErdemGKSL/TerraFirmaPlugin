---
id: PAR-INFRA-50
title: "Feature item tooltips no item owns: item-list contents of sealed device items, crucible contents, fertilized egg and hatch day, javelin thrown damage, pan deposit, flower cutting, lamp fluid, glass batch steps"
area: INFRA
status: open
wave: 14
deps: [PAR-INFRA-05, PAR-INFRA-04, PAR-INFRA-46, PAR-PROC-08]
size: M
aliases: ["parity audit 2026-11-14: the appendHoverText/addTooltipInfo lines of EggComponent, ItemListComponent, SealableDeviceBlock/BarrelBlock/CrucibleBlock items, JavelinItem, PanItem, FlowerCuttingItem, LampBlockItem and GlassWorking have no TooltipContributor and are named by no item (PAR-INFRA-46 owns only the static info lines; PAR-POT-04 owns the small vessel's lines)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/EggComponent.java"
  - "src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java"
  - "src/main/java/net/dries007/tfc/common/items/JavelinItem.java"
  - "src/main/java/net/dries007/tfc/common/items/PanItem.java"
  - "src/main/java/net/dries007/tfc/common/items/FlowerCuttingItem.java"
  - "src/main/java/net/dries007/tfc/common/items/LampBlockItem.java"
  - "src/main/java/net/dries007/tfc/common/items/MoldItem.java"
  - "src/main/java/net/dries007/tfc/common/component/glass/GlassWorking.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/java/net/dries007/tfc/util/Helpers.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/item/feature/**"
  - "core/src/test/java/dev/tfp/core/item/feature/**"
  - "paper/src/main/java/dev/tfp/paper/tooltip/feature/**"
  - "paper/src/test/java/dev/tfp/paper/tooltip/feature/**"
ui: true
in_game_steps:
  - "Survival Bot: fill a barrel (PAR-PROC-08) with 3 tfp:food/barley_grain and 1000 mB water, seal it, break it; hover the dropped barrel item at GUI scale 2: 'Contents:' then '3x Barley Grain' (grey) and '1000 mB of Water'; screenshot"
  - "tfp debug egg Bot fertilized <days> (a debug literal of this item, NP-CMD-01) writes TFC's egg component on a minecraft:egg in hand with hatch day = today + 3; hover: gold 'Fertilized' then 'Will hatch in 3 days'; tfp time add days 3; hover again: 'Will hatch today!'; screenshot both"
  - "For items that exist when the item is done (javelins PAR-TOOL-05, pans PAR-PROC-12, flower cuttings PAR-FARM-11, lamps PAR-METAL-19, crucible PAR-METAL-12, blowpipe with glass PAR-PROC-16): hover one of each with its component set and screenshot the line; the others are covered by the unit tests until they exist"
plan:
  - "Current (audit 2026-11-14): PAR-INFRA-05's pipeline and the heat, food, size, fluid (FluidContainerItem line), metal and static-info contributors exist. TFC's per-feature lines have none: ItemListComponent.addTooltipInfo and Helpers.addInventoryTooltipInfo ('Contents:' tfc.tooltip.contents, then Tooltips.countOfItem per stack in GRAY, merged by item as TFC does), BarrelBlock.appendHoverText (contents items plus Tooltips.fluidUnitsOf), SealableDeviceBlock (large vessels), CrucibleBlock (DARK_GREEN tfc.tooltip.small_vessel.contents, the inventory lines, the fluid line), EggComponent.addTooltipInfo (GOLD 'Fertilized', 'Will hatch in %s days' / 'Will hatch today!' from hatchDay minus the calendar's total days), JavelinItem (DARK_GREEN '%s Thrown Damage', String.format(\"%.0f\", getThrownDamage())), PanItem ('Contains ' + the deposit's name), FlowerCuttingItem ('Cutting of %s'), LampBlockItem (fluidUnitsOf), GlassWorking.addTooltipInfo (the batch name, AQUA title, '- <operation>' per step, the 'Danger!!' line). The items that carry them are added by other items; their data components exist through PAR-INFRA-04 (item lists, fluids) or arrive with those items (egg, deposit, plant, glass)."
  - "core/item/feature: pure line builders over immutable snapshots (item list entries with counts, fluid id and amount, egg {fertilized, hatchDay} plus the current calendar day, javelin tier damage from TFC's ToolItem/JavelinItem formula, deposit/plant item ids, glass batch and operation list), returning TFC's keys, arguments and colours in TFC's order; golden rows exported from /opt/tfc by a scratch script kept outside the repo."
  - "paper/tooltip/feature: one FeatureModule registering TooltipContributors at orders that reproduce TFC's position of each line relative to the existing contributors (contents after the name and size line as appendHoverText runs before the tooltip event; heat/food lines from the event after). Netty-thread rule of TooltipContributor (immutable inputs, no world access): the calendar day comes from the published calendar snapshot. Language through the existing i18n keys (lang hub). The `/tfp debug egg` literal goes through DebugTree and its own line in BootstrapTest (hub). If PAR-FLUID-01's FluidTooltipContributor already prints TFC's mold line (fluidUnitsAndCapacityOf with the mold capacity), only add a test; otherwise add the mold line here. Do not duplicate the small vessel lines (PAR-POT-04)."
  - "Record any line that cannot match (for example item names that a vanilla client resolves itself) in the ledger Divergences table."
tests:
  - "core: FeatureTooltipGoldenTest: one row per line kind vs TFC (contents merge order and counts, barrel with fluid, crucible with alloy, egg 3 days / today / unfertilized, copper and obsidian javelins, pan with a deposit, flower cutting, lamp with olive oil, glass batch with two steps and the danger line)"
  - "paper: MockBukkit pipeline run: lines appear once in TFC order next to size/heat/food lines, survive a creative round trip, nothing for items without the component; the egg line moves from '3 days' to 'today' after a calendar advance"
done_criteria:
  - "Every listed TFC line appears on items that carry the component with TFC's text, colours and order; golden and pipeline tests green; screenshots of the barrel and egg (and any other existing item) pass review"
---

# PAR-INFRA-50: Feature item tooltips no item owns: item-list contents of sealed device items, crucible contents, fertilized egg and hatch day, javelin thrown damage, pan deposit, flower cutting, lamp fluid, glass batch steps

> see_not_parity: NP-CLIENT-02, NP-CMD-01 (plans/parity/NOT-PARITY.md): tooltips refresh when the item packet is sent; the debug literal is tooling.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
