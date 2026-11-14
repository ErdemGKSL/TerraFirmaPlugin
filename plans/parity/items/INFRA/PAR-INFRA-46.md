---
id: PAR-INFRA-46
title: "Static info tooltip lines: damage type and resistances, fuel, sluice/pan deposits, rock-category and glass-type lore"
area: INFRA
status: done
wave: 13
deps: [PAR-INFRA-05, PAR-SURV-05]
size: M
aliases: ["parity audit 2026-11-13: ClientForgeEventHandler.onItemTooltip contributors and the static Lore components that no item owns"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/PhysicalDamageType.java"
  - "src/main/java/net/dries007/tfc/util/data/Fuel.java"
  - "src/main/java/net/dries007/tfc/util/data/Deposit.java"
  - "src/main/java/net/dries007/tfc/util/data/ItemDamageResistance.java"
  - "src/main/java/net/dries007/tfc/common/Lore.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockCategory.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockDisplayCategory.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/tooltip/info/**"
  - "paper/src/test/java/dev/tfp/paper/tooltip/info/**"
  - "core/src/main/java/dev/tfp/core/item/info/**"
  - "core/src/test/java/dev/tfp/core/item/info/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/sword/copper; hover at GUI scale 2: 'Deals Slashing Damage' line in TFC's grey/white; screenshot"
  - "ce item give Bot tfp:metal/chestplate/copper; hover: 'Resistances: Slashing x, Piercing y, Crushing z' with TFC's percentages; screenshot"
  - "give Bot minecraft:charcoal 4 and tfp:peat; hover: 'Burns at <heat colour text> for <time delta>' (charcoal 1350 C for 1800 ticks); screenshot"
  - "ce item give Bot tfp:rock/raw/granite and tfp:rock/bricks/chalk; hover: grey italic rock display category ('Felsic Igneous Intrusive' / 'Sedimentary') right under the name; screenshot"
plan:
  - "Current (audit 2026-11-13): PAR-INFRA-05 built the pipeline and the heat/food/size/fluid/metal contributors exist, but these TFC tooltip lines have no owner: PhysicalDamageType.addTooltipInfo (`tfc.tooltip.deals_damage.<slashing|piercing|crushing>` from the `tfc:deals_*_damage` item tags, `tfc.tooltip.resists_damage` from item_damage_resistance with calculatePercentageForDisplay), the fuel line (`tfc.tooltip.fuel_burns_at`, the fuel's temperature in the configured heat style and its duration as a calendar time delta), the deposit line (`tfc.tooltip.usable_in_sluice_and_pan`, grey, for items in the deposit data), and the static `minecraft:lore` that TFC puts on items at registration: rock display category on rock block items and rock bricks (`Rock.createItemProperties`, grey italic `tfc.enum.rockdisplaycategory.*`), rock category on stone tool items (`RockCategory.ItemType`, `tfc.enum.rockcategory.*`), glass type on glass batches and bottles (`Lore.SILICA/HEMATITIC/OLIVINE/VOLCANIC`: aqua, red, green, dark purple, italic)."
  - "Pure part in core/item/info: which lines an item id gets from the tag snapshot and the data (damage-type tag order as TFC checks it, resistance percentages, fuel lookup by ingredient incl. tags, deposit membership, lore by id pattern from TFC's registration code); golden rows exported from /opt/tfc by a scratch script."
  - "Paper part in paper/tooltip/info: one feature module registering TooltipContributors in TFC's order (damage after size and before forging; fuel after heat; deposit after metal content), language through the existing i18n keys. Lore lines go first, directly under the name, as TFC's lore component does; record that they are packet lore, not an item component (same reason as the jar Sealed/Unsealed row in the ledger)."
  - "Items that do not exist yet (stone tool heads PAR-TOOL-01, glass batches PAR-PROC-17, deposit items PAR-WORLD-09/PAR-PROC-12) get their lines from the id rules as soon as they are generated: unit-test those ids now; their in-game look is checked by those items' own tooltip steps."
  - "Done: core/item/info (DamageInfo, FuelInfo, DepositItems, StaticLore, RockDisplayCategory, RockCategory) and paper/tooltip/info (four contributors at orders 50/150/600/750, LegacyLines, module static_info_tooltip); lang en_us and tr_tr; tests ItemInfoGoldenTest (70) and StaticInfoTooltipMockBukkitTest (10); ledger row and two divergences (packet lore, fuel lookup order). In-game check: docs/verification/PAR-INFRA-46/ when run."
tests:
  - "core: golden lines per sample id (sword, mace, javelin, knife, armour piece, charcoal, peat, a log tag, a deposit, raw granite, chalk bricks, a stone axe head, each glass batch) vs TFC"
  - "paper: MockBukkit pipeline run: order relative to size/heat/metal lines, no duplicate after a creative round trip"
done_criteria:
  - "Every listed TFC line appears on the matching items with TFC's text, colours and order; golden and pipeline tests green; screenshots pass review"
---

# PAR-INFRA-46: Static info tooltip lines: damage type and resistances, fuel, sluice/pan deposits, rock-category and glass-type lore

> see_not_parity: NP-CLIENT-02, NP-CLIENT-04 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
