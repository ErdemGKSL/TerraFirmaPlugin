---
id: PAR-FARM-13
title: "Plant info and fertilizer tooltips: climate range, nutrients, growth time and lifecycle on seeds and saplings; N/P/K on fertilizers"
area: FARM
status: open
wave: 33
deps: [PAR-INFRA-46, PAR-FARM-02, PAR-FARM-03, PAR-FARM-06, PAR-FARM-07, PAR-FARM-08, PAR-FARM-09, PAR-FARM-10]
size: M
aliases: ["parity audit 2026-11-13: PlantableInfo.addTooltipInfo and the Fertilizer lines of ClientForgeEventHandler.onItemTooltip named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/PlantableInfo.java"
  - "src/main/java/net/dries007/tfc/common/items/SeedItem.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java"
  - "src/main/java/net/dries007/tfc/util/data/Fertilizer.java"
  - "src/main/java/net/dries007/tfc/util/climate/ClimateRange.java"
  - "src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/plantable/**"
  - "core/src/test/java/dev/tfp/core/plantable/**"
  - "core/src/test/resources/dev/tfp/core/plantable/**"
  - "paper/src/main/java/dev/tfp/paper/plantable/**"
  - "paper/src/test/java/dev/tfp/paper/plantable/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:seeds/barley; hover at GUI scale 2 and 3: climate block (temperature range in the configured climate style, hydration range), nutrients block (N/P/K with the consumed nutrient), lifecycle block (growth speed as a time delta); screenshot"
  - "ce item give Bot a fruit tree sapling (tfp:plant/cherry_sapling) and a berry bush (tfp:plant/blueberry_bush); hover: lifecycle months (grows from / fruits in); screenshot"
  - "ce item give Bot tfp:pure_nitrogen and tfp:compost; hover: '(N) Nitrogen: x%' style lines in TFC's colours; screenshot"
plan:
  - "Current (audit 2026-11-13): TFC shows plant info on every PlantableInfo item (SeedItem, fruit tree and bush blocks of FruitBlocks, TFCSaplingBlock): with Shift held the climate range (`tfc.tooltip.plantable.climate*`), nutrients (`tfc.tooltip.plantable.nutrients` and the three fertilizer keys with formatNutrientAmount), lifecycle (`tfc.tooltip.plantable.lifecycle*`: growth speed time delta, the grow and fruit months from the Lifecycle list), otherwise the hint 'Hold (Shift) for Plant Info'. Fertilizer items get `tfc.tooltip.fertilizer.nitrogen/phosphorus/potassium` with one decimal percent. No item builds these lines."
  - "core/plantable: the pure line model per item (climate range from climate_range data, nutrients from the crop table, growth ticks, lifecycle months by hemisphere-neutral month as TFC prints them, fertilizer values from the fertilizer data); golden rows from /opt/tfc."
  - "paper/plantable: TooltipContributors at TFC's place (after fertilizer, before metal content) through the PAR-INFRA-05 pipeline; climate temperatures in the server's hud.climate-temperature-style (PAR-HUD-14) as TFC uses climateTooltipStyle."
  - "Shift cannot be read on a vanilla client: show the expanded block always and never send the hint line, the same choice as the nutrition block of PAR-SURV-02 (extend that Divergences row or add one)."
tests:
  - "core: golden plant info for one seed per crop shape, a fruit tree, a stationary bush, a spreading bush, a tree sapling, banana; fertilizer lines for every entry of the fertilizer data (pure N/P/K, compost, bone meal, food, groundcover and powder files)"
  - "paper: contributor order with heat/fuel/metal lines; climate style switch changes the temperature unit"
done_criteria:
  - "Seeds, saplings and bushes show TFC's plant info; fertilizers show their N/P/K lines; tests green; screenshots pass review"
---

# PAR-FARM-13: Plant info and fertilizer tooltips: climate range, nutrients, growth time and lifecycle on seeds and saplings; N/P/K on fertilizers

> see_not_parity: NP-CLIENT-02, NP-CLIENT-04 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
