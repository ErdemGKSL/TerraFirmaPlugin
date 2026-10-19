# Image list per milestone

Backlog of the 2D images the plans need (plan item X-ART-01). Scope: textures, GUI backgrounds and sheets, HUD sprites, icons, buttons and pack images that a plan item names or clearly implies. 3D models, colours, sounds and text are out of scope.

Generated images are tracked here until they are made. The image-generation skill keeps its own backlog in `.claude/skills/image-generation/references/backlog.md`. That file holds the two specs with a full size (`ui-inventory-tab-button` and `pack-icon`). Rows in this list point to them.

## How to read the list

- **Asset id**: the plan item id in lower case, then a short slug. Example: `m3-gui-01-player-calendar-gui` belongs to `M3-GUI-01`.
- **Plan item**: the first plan item that names the image. A later item that reuses it says so in the section text and is not counted again.
- **Kind**: `texture` (block, item or entity texture), `gui` (inventory or screen background), `hud` (BetterHud sprite), `icon`, `button`, `pack`. The one row with kind `mixed` is a catch-all for categories the plan names without any image.
- **Size**: `tbd` unless the plan states a size. Layout hints from the plan are kept (for example "6 rows"). A size from the image-generation backlog is written in brackets, because the plan itself does not state it.
- **Status**: `needed` for every row, since no image has been generated yet.
- **TFC** in the description: the image is a TFC texture that `tfc-asset-migration` copies or converts. It is not generated. It is listed because the plan names it, and it still needs a row.
- **unspecified in plan**: the plan mentions art without naming an image, so the row is one asset for that mention.
- A family of images named as one set (for example "all 20 woods") is one row. The count is in the description.

Left out on purpose: colour bands (M2-SEA-01, M2-SEA-02, which read TFC colormaps as data), datagen allocators and config (M1-DGN-04), debug map renders (M2-GEN-03, M2-GEN-04), and the listing banner for the release (M6-REL-01). The plan names a listing but no image, so it is an open question, not a row.

## M0: Spikes

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m0-vis-01-rock-textures | Rock block textures, one rock with all its variants (TFC) | M0-VIS-01 | texture | tbd | needed |
| m0-vis-01-ore-grade-textures | Ore textures for 3 ores x grades, base and overlay flattened into one PNG each (TFC, flattened by datagen) | M0-VIS-01 | texture | tbd | needed |
| m0-vis-01-small-ore-button-texture | Small ore piece texture for the button-state block (TFC) | M0-VIS-01 | texture | tbd | needed |
| m0-vis-02-gem-face-overlay | Gem texture on each exposed face, shown as one item_display per face (unspecified in plan) | M0-VIS-02 | texture | tbd | needed |
| m0-gui-01-inventory-tab-button | Outside-inventory button sheet, normal and hovered cells, drawn as a crafting-title glyph beside the player inventory. Backlog id ui-inventory-tab-button. Reused by M3-GUI-01 | M0-GUI-01 | button | tbd [backlog: 48x24, two 24x24 cells] | needed |
| m0-gui-01-test-chest-6-row | Textured 6-row chest GUI for the pixel alignment check at GUI scales 1 to 4 (unspecified in plan) | M0-GUI-01 | gui | 6 rows, px tbd | needed |
| m0-bh-01-heart-sprite | Transparent vanilla heart sprite in our CE pack, so the vanilla hearts are hidden | M0-BH-01 | hud | tbd | needed |
| m0-bh-01-food-sprite | Transparent vanilla food sprite in our CE pack | M0-BH-01 | hud | tbd | needed |
| m0-bh-01-air-sprite | Transparent vanilla air sprite in our CE pack | M0-BH-01 | hud | tbd | needed |
| m0-bh-01-thirst-bar | Thirst bar art for the BetterHud layout (unspecified in plan) | M0-BH-01 | hud | 82x10 | generated (pending review): docs/art/hud/m3_surv_06_thirst_bar_fill.png, _bg.png |
| m0-bm-01-deer-texture | Entity texture for the converted TFC deer model (TFC) | M0-BM-01 | texture | tbd | needed |
| m0-bm-01-bovine-texture | Entity texture for the bovine example model (unspecified in plan) | M0-BM-01 | texture | tbd | needed |

## M1: Skeleton

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m1-dgn-03-rock-textures | Rock block texture set for the rock families (TFC, copied with refs rewritten) | M1-DGN-03 | texture | tbd | needed |
| m1-dgn-03-ore-textures | Ore textures flattened to one PNG per ore x rock, full set (TFC, flattened by datagen) | M1-DGN-03 | texture | tbd | needed |
| m1-dgn-03-soil-textures | Soil textures for the dirt, grass, clay and sand families (TFC) | M1-DGN-03 | texture | tbd | needed |
| m1-dgn-03-connected-grass-textures | Connected grass textures for the multipart grass block, not emitted yet (TFC) | M1-DGN-03 | texture | tbd | needed |
| m1-inst-03-pack-icon | Resource pack icon (pack.png): layered rock strata, a copper ingot and a sapling. Backlog id pack-icon. No plan item names it; it is listed here because the pack is built in this item | M1-INST-03 | pack | tbd [backlog: 128x128, drawn at 32x32 and upscaled 4x] | needed |

## M2: Living world

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m2-wth-03-snow-pile-textures | Snow pile block textures, piles over vegetation and loose rocks (TFC) | M2-WTH-03 | texture | tbd | needed |
| m2-wth-03-ice-pile-texture | Ice pile block texture, ice under a solid top (TFC) | M2-WTH-03 | texture | tbd | needed |
| m2-wth-03-icicle-texture | Icicle (thin spike) block texture under overhangs (TFC) | M2-WTH-03 | texture | tbd | needed |
| m2-wth-03-sea-ice-texture | Sea ice block texture (TFC) | M2-WTH-03 | texture | tbd | needed |
| m2-gen-08-rock-gravel-textures | Rock gravel block textures (TFC) | M2-GEN-08 | texture | tbd | needed |
| m2-gen-08-soil-variant-textures | Soil variant block textures for the TFC soil set (TFC) | M2-GEN-08 | texture | tbd | needed |
| m2-gen-08-ice-sheet-texture | Ice sheet block texture; may reuse the vanilla ice texture (unspecified in plan) | M2-GEN-08 | texture | tbd | needed |
| m2-gen-08-lava-flow-texture | Lava flow block texture; may reuse the vanilla lava texture (unspecified in plan) | M2-GEN-08 | texture | tbd | needed |
| m2-gen-11-loose-rock-textures | Loose rock block textures, surface and underground (TFC). Also used by M3-GATH-01 | M2-GEN-11 | texture | tbd | needed |
| m2-gen-11-small-ore-textures | Small ore indicator block textures, the surface piece above a vein (TFC) | M2-GEN-11 | texture | tbd | needed |
| m2-gen-11-groundcover-textures | Groundcover block textures (TFC). Also used by M3-GATH-01 | M2-GEN-11 | texture | tbd | needed |
| m2-gen-11-guano-texture | Guano deposit block texture for riverbeds (TFC) | M2-GEN-11 | texture | tbd | needed |
| m2-gen-11-water-clay-texture | Water clay deposit block texture for riverbeds (TFC) | M2-GEN-11 | texture | tbd | needed |
| m2-gen-11-rooted-dirt-texture | Rooted dirt block texture (TFC) | M2-GEN-11 | texture | tbd | needed |
| m2-gen-11-mollisol-disc-texture | Mollisol disc block texture (TFC) | M2-GEN-11 | texture | tbd | needed |
| m2-gen-12-tree-textures | Log and leaf textures per wood; leaves use the leaves visual pool (TFC, family of the wood set) | M2-GEN-12 | texture | tbd | needed |
| m2-gen-13-plant-textures | Plant, grass and water plant block textures (TFC, family) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-wild-crop-textures | Wild crop block textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-coral-textures | Coral block textures, including atoll corals (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-vine-textures | Vine block textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-kelp-textures | Kelp block textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-cactus-textures | Barrel cactus and saguaro cactus textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-algae-textures | Algae block textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-epiphyte-textures | Epiphyte and creeping plant block textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-13-forest-groundcover-textures | Forest groundcover block textures (TFC) | M2-GEN-13 | texture | tbd | needed |
| m2-gen-14-volcano-block-textures | Volcano block textures (TFC) | M2-GEN-14 | texture | tbd | needed |
| m2-gen-14-hot-spring-textures | Hot spring block textures (unspecified in plan) | M2-GEN-14 | texture | tbd | needed |
| m2-gen-14-glacier-ice-texture | Ice and glacier block textures (TFC) | M2-GEN-14 | texture | tbd | needed |

## M3: Stone age

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m3-surv-06-health-bar | Health bar sprite for the "45/50" HUD line, from the TFC icon sheets (TFC) | M3-SURV-06 | hud | 82x10 | generated (pending review): docs/art/hud/m3_surv_06_health_bar_fill.png, _bg.png |
| m3-surv-06-hunger-bar | Hunger bar sprite, from the TFC icon sheets (TFC). The thirst bar is m0-bh-01-thirst-bar | M3-SURV-06 | hud | 82x10 | generated (pending review): docs/art/hud/m3_surv_06_hunger_bar_fill.png, _bg.png |
| m3-surv-06-temperature-popup | Held-item temperature popup frame and icon (unspecified in plan; "extra art") | M3-SURV-06 | hud | tbd | needed |
| m3-heat-01-heat-glow-texture | Heat glow overlay for hot items, via range_dispatch item models (unspecified in plan) | M3-HEAT-01 | texture | tbd | needed |
| m3-heat-03-firepit-gui | Fire pit GUI background (TFC gui/fire_pit*.png) | M3-HEAT-03 | gui | tbd | needed |
| m3-heat-03-firepit-flame-texture | Animated flame texture for the lit fire pit; logs come from properties (unspecified in plan) | M3-HEAT-03 | texture | tbd | needed |
| m3-heat-04-dead-torch-texture | Dead torch block texture, the swap after burnout (TFC) | M3-HEAT-04 | texture | tbd | needed |
| m3-heat-04-candle-textures | Candle block textures (TFC). Also used by M6-DECO-03 | M3-HEAT-04 | texture | tbd | needed |
| m3-gath-02-fallen-leaves-textures | Fallen leaves block textures (TFC) | M3-GATH-02 | texture | tbd | needed |
| m3-gath-02-twig-textures | Twig and stick textures (TFC) | M3-GATH-02 | texture | tbd | needed |
| m3-knap-01-knapping-gui | Knapping GUI background: textured 6-row chest, 5x5 grid in columns 2 to 6 (TFC gui style) | M3-KNAP-01 | gui | 6 rows, 5x5 grid, px tbd | needed |
| m3-knap-01-cell-button | Knapping cell button sheet for the 5x5 grid (unspecified in plan) | M3-KNAP-01 | button | tbd | needed |
| m3-tool-01-stone-tool-textures | Stone tool item textures for the four stone tiers (TFC, family) | M3-TOOL-01 | texture | tbd | needed |
| m3-tool-01-javelin-texture | Javelin item texture (TFC). Reused by M5-HUNT-01 | M3-TOOL-01 | texture | tbd | needed |
| m3-phys-03-support-beam-textures | Vertical and horizontal support beam textures, with the renderer visuals (TFC) | M3-PHYS-03 | texture | tbd | needed |
| m3-pot-01-pit-kiln-stage-textures | Pit kiln stage textures: straw, log layers, lit and fired (unspecified in plan) | M3-POT-01 | texture | tbd | needed |
| m3-pot-02-mold-fill-textures | Mold item textures per fill level, via range_dispatch (TFC, family) | M3-POT-02 | texture | tbd | needed |
| m3-pot-02-small-vessel-gui | Small vessel inventory GUI background (unspecified in plan) | M3-POT-02 | gui | tbd | needed |
| m3-pot-03-large-vessel-textures | Large vessel block textures (TFC) | M3-POT-03 | texture | tbd | needed |
| m3-pot-03-large-vessel-gui | Large vessel 9-slot inventory GUI background (unspecified in plan) | M3-POT-03 | gui | 9 slots, px tbd | needed |
| m3-pot-03-jug-texture | Jug item texture (TFC) | M3-POT-03 | texture | tbd | needed |
| m3-pot-04-drying-brick-pile-textures | Drying brick pile block textures, one state per brick count (TFC) | M3-POT-04 | texture | tbd | needed |
| m3-pot-04-mud-brick-item-texture | Mud brick item texture, the dried result (TFC) | M3-POT-04 | texture | tbd | needed |
| m3-pot-04-thatch-texture | Thatch block texture (TFC) | M3-POT-04 | texture | tbd | needed |
| m3-proc-01-quern-textures | Quern block textures, with the spinning top element (TFC). Also used by M6-MECH-03 for the automated quern | M3-PROC-01 | texture | tbd | needed |
| m3-proc-01-handstone-texture | Handstone item texture (TFC) | M3-PROC-01 | texture | tbd | needed |
| m3-proc-02-scraping-progress-texture | Scraping in-progress block texture (TFC) | M3-PROC-02 | texture | tbd | needed |
| m3-proc-02-loom-progress-texture | Loom in-progress block texture (TFC) | M3-PROC-02 | texture | tbd | needed |
| m3-proc-02-sewing-gui | Sewing table container GUI background (unspecified in plan; TFC sewing container) | M3-PROC-02 | gui | tbd | needed |
| m3-proc-02-sewing-button | Sewing button sheet for the stitch clicks (unspecified in plan) | M3-PROC-02 | button | tbd | needed |
| m3-proc-03-barrel-textures | Barrel block textures (TFC) | M3-PROC-03 | texture | tbd | needed |
| m3-proc-03-barrel-gui | Barrel GUI background (unspecified in plan) | M3-PROC-03 | gui | tbd | needed |
| m3-proc-03-barrel-fluid-tint | Barrel fluid level tint texture for the tint_source visual (unspecified in plan) | M3-PROC-03 | texture | tbd | needed |
| m3-proc-04-log-pile-textures | Log pile block textures, one state per log count (TFC) | M3-PROC-04 | texture | tbd | needed |
| m3-proc-04-charcoal-pile-textures | Charcoal pile layer textures (TFC) | M3-PROC-04 | texture | tbd | needed |
| m3-proc-05-composter-textures | Composter block textures (TFC) | M3-PROC-05 | texture | tbd | needed |
| m3-farm-01-farmland-textures | Farmland block textures (TFC) | M3-FARM-01 | texture | tbd | needed |
| m3-farm-01-fertilizer-item-textures | Fertilizer item textures (unspecified in plan) | M3-FARM-01 | texture | tbd | needed |
| m3-farm-02-crop-stage-textures | Crop stage block textures for all 29 crops (TFC, family) | M3-FARM-02 | texture | tbd | needed |
| m3-farm-02-seed-item-textures | Seed item textures, placed via block_item (TFC, family) | M3-FARM-02 | texture | tbd | needed |
| m3-farm-02-spread-fruit-textures | Spread fruit item textures (unspecified in plan) | M3-FARM-02 | texture | tbd | needed |
| m3-farm-03-berry-bush-stage-textures | Berry bush stage textures: dormant, healthy, flowering, fruiting (TFC) | M3-FARM-03 | texture | tbd | needed |
| m3-farm-03-cane-textures | Cane bush block textures (TFC) | M3-FARM-03 | texture | tbd | needed |
| m3-farm-03-cranberry-texture | Cranberry bush block texture (TFC) | M3-FARM-03 | texture | tbd | needed |
| m3-gui-01-player-calendar-gui | Player calendar tab GUI background (TFC gui/player_calendar.png). The tab button is m0-gui-01-inventory-tab-button | M3-GUI-01 | gui | tbd | needed |
| m3-gui-01-player-nutrition-gui | Player nutrition tab GUI background (TFC gui/player_nutrition.png) | M3-GUI-01 | gui | tbd | needed |
| m3-gui-01-player-climate-gui | Player climate tab GUI background (TFC gui/player_climate.png) | M3-GUI-01 | gui | tbd | needed |
| m3-gui-01-bar-glyph-overlays | Title glyph overlays drawn as bars on the player tabs (unspecified in plan) | M3-GUI-01 | gui | tbd | needed |
| m3-pan-01-pan-stage-textures | Pan item stage textures, via item model data (TFC) | M3-PAN-01 | texture | tbd | needed |
| m3-pan-01-sluice-textures | Sluice block textures (TFC) | M3-PAN-01 | texture | tbd | needed |

## M4: Metal age

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m4-met-01-metal-item-textures | Item textures for ingots, double ingots, sheets, double sheets, rods and tool heads, 27 metals (TFC, family) | M4-MET-01 | texture | tbd | needed |
| m4-met-01-metal-block-textures | Base metal block textures: full block, slab and stairs, entity_renderer (TFC, family) | M4-MET-01 | texture | tbd | needed |
| m4-met-02-alloy-gui | Alloy GUI or display (unspecified in plan) | M4-MET-02 | gui | tbd | needed |
| m4-met-03-mold-break-texture | Mold break visual, the cracked mold (unspecified in plan) | M4-MET-03 | texture | tbd | needed |
| m4-met-03-casting-gui | Casting block GUI background (unspecified in plan) | M4-MET-03 | gui | tbd | needed |
| m4-anv-02-rock-anvil-textures | Rock anvil block textures (TFC, RockAnvilBlock). Shown on the anvil top as well | M4-ANV-02 | texture | tbd | needed |
| m4-anv-02-metal-anvil-textures | Tiered metal anvil block textures (TFC) | M4-ANV-02 | texture | tbd | needed |
| m4-anv-02-plan-select-gui | Anvil plan selection GUI background (TFC gui/anvil*.png) | M4-ANV-02 | gui | tbd | needed |
| m4-anv-02-forging-gui | Forging GUI background with step slots (TFC gui/anvil*.png) | M4-ANV-02 | gui | tbd | needed |
| m4-anv-02-step-buttons | Forging step button sheet (unspecified in plan) | M4-ANV-02 | button | tbd | needed |
| m4-anv-02-progress-glyphs | Target and progress markers drawn as title glyph overlays (unspecified in plan) | M4-ANV-02 | gui | tbd | needed |
| m4-forge-01-charcoal-forge-textures | Charcoal forge block textures, lit and unlit (TFC) | M4-FORGE-01 | texture | tbd | needed |
| m4-forge-01-bellows-textures | Bellows block textures, with press animation frames (TFC) | M4-FORGE-01 | texture | tbd | needed |
| m4-forge-01-forge-gui | Charcoal forge GUI background (unspecified in plan) | M4-FORGE-01 | gui | tbd | needed |
| m4-cruc-01-crucible-textures | Crucible block textures (TFC) | M4-CRUC-01 | texture | tbd | needed |
| m4-cruc-01-crucible-gui | Crucible GUI background (unspecified in plan) | M4-CRUC-01 | gui | tbd | needed |
| m4-cruc-01-molten-metal-texture | Emissive animated molten metal texture (unspecified in plan) | M4-CRUC-01 | texture | tbd | needed |
| m4-cruc-01-channel-textures | Channel block textures (TFC) | M4-CRUC-01 | texture | tbd | needed |
| m4-cruc-01-mold-table-textures | Mold table block textures (TFC) | M4-CRUC-01 | texture | tbd | needed |
| m4-bloom-01-bloomery-textures | Bloomery block textures, with the bloom layers (TFC) | M4-BLOOM-01 | texture | tbd | needed |
| m4-bloom-01-bloomery-gui | Bloomery GUI or display (unspecified in plan) | M4-BLOOM-01 | gui | tbd | needed |
| m4-bloom-01-bloom-item-texture | Refined iron bloom item texture (TFC) | M4-BLOOM-01 | texture | tbd | needed |
| m4-blast-01-blast-furnace-textures | Blast furnace block textures (TFC) | M4-BLAST-01 | texture | tbd | needed |
| m4-blast-01-blast-furnace-gui | Blast furnace GUI background (unspecified in plan) | M4-BLAST-01 | gui | tbd | needed |
| m4-ingot-01-ingot-pile-textures | Ingot pile textures per count and metal, via tint_source (TFC) | M4-INGOT-01 | texture | tbd | needed |
| m4-ingot-01-double-ingot-pile-textures | Double ingot pile block textures (TFC) | M4-INGOT-01 | texture | tbd | needed |
| m4-ingot-01-sheet-pile-textures | Sheet pile block textures (TFC) | M4-INGOT-01 | texture | tbd | needed |
| m4-tool-02-metal-tool-textures | Metal tool and armour part item textures, per tier (TFC, family) | M4-TOOL-02 | texture | tbd | needed |
| m4-tool-02-armour-equipment-textures | Armour equipment layer textures for the metal armour sets (TFC, family) | M4-TOOL-02 | texture | tbd | needed |
| m4-pros-01-propick-texture | Propick item texture (TFC) | M4-PROS-01 | texture | tbd | needed |
| m4-chis-01-chisel-texture | Chisel item texture (TFC) | M4-CHIS-01 | texture | tbd | needed |
| m4-chis-01-chiselled-variant-textures | Smooth, slab and stair variant textures from chiselling (TFC) | M4-CHIS-01 | texture | tbd | needed |
| m4-weath-01-weathering-textures | Exposed, weathered and oxidized metal block textures (TFC) | M4-WEATH-01 | texture | tbd | needed |
| m4-weath-01-lamp-textures | Metal lamp block textures, lit and unlit (TFC). Reused by M6-DECO-03 | M4-WEATH-01 | texture | tbd | needed |

## M5: Husbandry

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m5-ent-02-animal-textures | Entity textures for the 44 TFC bbmodels, including aquatic, pest and wildlife models (TFC, family) | M5-ENT-02 | texture | tbd | needed |
| m5-ent-02-animal-variant-textures | Variant textures per species, emitted per texture (unspecified in plan; count from TFC) | M5-ENT-02 | texture | tbd | needed |
| m5-liv-02-nest-box-textures | Nest box block textures (TFC) | M5-LIV-02 | texture | tbd | needed |
| m5-liv-02-egg-item-textures | Egg item textures (TFC) | M5-LIV-02 | texture | tbd | needed |
| m5-liv-02-wool-item-textures | Wool item textures (TFC) | M5-LIV-02 | texture | tbd | needed |
| m5-liv-02-milk-item-texture | Milk item texture (unspecified in plan) | M5-LIV-02 | texture | tbd | needed |
| m5-pet-01-command-gui | Pet command GUI opened by sneak and right-click (unspecified in plan) | M5-PET-01 | gui | tbd | needed |
| m5-mnt-01-saddle-texture | Saddle texture for mounts; may reuse the vanilla saddle (unspecified in plan) | M5-MNT-01 | texture | tbd | needed |
| m5-hunt-01-meat-textures | Raw and cooked meat item textures per animal (TFC, family) | M5-HUNT-01 | texture | tbd | needed |
| m5-hunt-01-hide-textures | Hide item textures, one per HideItemType (TFC, family) | M5-HUNT-01 | texture | tbd | needed |
| m5-fish-01-fishing-rod-texture | Fishing rod item texture (TFC) | M5-FISH-01 | texture | tbd | needed |
| m5-fish-01-lobster-trap-textures | Lobster trap block textures (TFC) | M5-FISH-01 | texture | tbd | needed |
| m5-fish-01-bait-textures | Bait item textures (TFC, family) | M5-FISH-01 | texture | tbd | needed |

## M6: Parity and release

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| m6-mech-01-shaft-gear-textures | Shaft and gear block textures and models (TFC) | M6-MECH-01 | texture | tbd | needed |
| m6-mech-01-readout-gui | Rotation readout panel (unspecified in plan) | M6-MECH-01 | gui | tbd | needed |
| m6-mech-02-water-wheel-texture | Water wheel model texture (TFC waterwheel.bbmodel) | M6-MECH-02 | texture | tbd | needed |
| m6-mech-02-windmill-blade-texture | Windmill blade model texture (TFC WindmillBlade*.bbmodel) | M6-MECH-02 | texture | tbd | needed |
| m6-mech-02-transmission-textures | Axle, encased axle, clutch, gearbox and crankshaft textures (TFC, family) | M6-MECH-02 | texture | tbd | needed |
| m6-mech-03-trip-hammer-textures | Trip hammer block textures (TFC) | M6-MECH-03 | texture | tbd | needed |
| m6-mech-03-power-loom-textures | Power loom block textures (TFC) | M6-MECH-03 | texture | tbd | needed |
| m6-mech-03-pump-pipe-textures | Fluid pump and fluid pipe block textures (TFC) | M6-MECH-03 | texture | tbd | needed |
| m6-glass-01-blowpipe-gui | Blowpipe textured GUI, or action bar steps (unspecified in plan) | M6-GLASS-01 | gui | tbd | needed |
| m6-glass-01-glass-basin-texture | Glass basin block texture (TFC) | M6-GLASS-01 | texture | tbd | needed |
| m6-glass-01-hot-poured-glass-texture | Hot-poured glass block texture (TFC) | M6-GLASS-01 | texture | tbd | needed |
| m6-glass-01-glass-item-textures | Glass item textures for the glassworking outputs (TFC, family) | M6-GLASS-01 | texture | tbd | needed |
| m6-deco-01-wood-block-textures | Wood family block textures for the 20 woods: stairs, slabs, fences, gates, doors, trapdoors, buttons, plates, signs, chests, bookshelves, lecterns, racks, tables, looms, sluices (TFC, family of 20 woods) | M6-DECO-01 | texture | tbd | needed |
| m6-deco-02-rock-stair-slab-wall-textures | Rock stair, slab and wall textures for every rock variant (TFC, family; listed as not emitted in M1-DGN-03) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-02-spike-texture | Rock spike block texture (TFC; not emitted in M1-DGN-03) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-02-aqueduct-texture | Aqueduct block texture (TFC; not emitted in M1-DGN-03) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-02-rope-anchor-texture | Rope anchor block texture (TFC; not emitted in M1-DGN-03) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-02-mud-brick-block-textures | Mud brick block textures for the 8 soils: full block, slab, stairs and wall (TFC, family) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-02-alabaster-texture | Alabaster block texture (TFC) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-02-wattle-textures | Wattle and stained wattle block textures (TFC, family) | M6-DECO-02 | texture | tbd | needed |
| m6-deco-03-metal-bars-chain-grate-textures | Metal bars, chain, grate and trapdoor textures (TFC, family) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-03-cake-texture | Cake block textures (TFC) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-03-buoy-texture | Buoy block textures (TFC) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-03-bell-texture | Bell block textures (TFC) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-03-jack-o-lantern-texture | Jack-o'-lantern block textures (TFC) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-03-crate-texture | Crate block textures (TFC) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-03-powderkeg-texture | Powderkeg block textures (TFC) | M6-DECO-03 | texture | tbd | needed |
| m6-deco-04-growing-branch-textures | Fruit tree growing branch textures (TFC) | M6-DECO-04 | texture | tbd | needed |
| m6-deco-04-seasonal-leaf-textures | Seasonal fruit tree leaf textures, one per season (TFC) | M6-DECO-04 | texture | tbd | needed |
| m6-deco-04-fruit-item-textures | Fruit food item textures (unspecified in plan) | M6-DECO-04 | texture | tbd | needed |
| m6-fluid-01-container-textures | Fluid container block and item textures for the 12 container kinds (TFC, family) | M6-FLUID-01 | texture | tbd | needed |
| m6-guide-01-field-guide-gui | Field guide book view GUI (unspecified in plan; the form is undecided) | M6-GUIDE-01 | gui | tbd | needed |
| m6-guide-01-field-guide-book-texture | Field guide book item texture (unspecified in plan) | M6-GUIDE-01 | texture | tbd | needed |

## Cross-cutting

| Asset id | What it is | Plan item | Kind | Size | Status |
|---|---|---|---|---|---|
| x-art-01-category-catch-all | Art the plan names only by category: GUI overlays, HUD frames, buttons and icons (unspecified in plan). Each named image is a row above instead | X-ART-01 | mixed | tbd | needed |

## Notes on shared and reused assets

- The tab button (m0-gui-01-inventory-tab-button) is used by M3-GUI-01. It has one row, under M0.
- The thirst bar (m0-bh-01-thirst-bar) is the HUD thirst bar for M3-SURV-06 too.
- Loose rock and groundcover textures (M2-GEN-11) are reused by M3-GATH-01.
- The javelin texture (M3-TOOL-01) is reused by M5-HUNT-01.
- Candles (M3-HEAT-04) are reused by M6-DECO-03. Metal lamps (M4-WEATH-01) are reused by M6-DECO-03.
- The thatch row (M3-POT-04) is the thatch the plan names. M6-DECO-02 lists thatch with the mud bricks, and that is not counted again.
- The M1-DGN-03 deferred list (small ores, loose rocks, anvils, buttons, plates, slabs, stairs, walls, spikes, aqueducts, rope anchors) is placed under the milestone that owns each one: small ores and loose rocks (M2-GEN-11), anvils (M4-ANV-02), buttons and plates (M6-DECO-01, in the wood family), slabs and stairs and walls, spikes, aqueducts and rope anchors (M6-DECO-02). Connected grass stays with M1-DGN-03.
- The M0 and M1 spike rows are a subset of the full family rows. Both are counted, because each names its own scope.

## Counts

Method: the table rows in this file were counted by script. A row is a line that starts with `| `, whose first cell is a lower-case asset id. The header and separator lines are skipped. Rows were grouped by the `##` heading they sit under, and by the Kind column. The total is the sum over the rows.

Totals: **176 assets**, all with status `needed`.

| Milestone | Assets |
|---|---|
| M0: Spikes | 12 |
| M1: Skeleton | 5 |
| M2: Living world | 28 |
| M3: Stone age | 50 |
| M4: Metal age | 34 |
| M5: Husbandry | 13 |
| M6: Parity and release | 33 |
| Cross-cutting | 1 |
| **Total** | **176** |

| Kind | Assets |
|---|---|
| texture | 139 |
| gui | 24 |
| hud | 7 |
| button | 4 |
| pack | 1 |
| mixed (the X-ART-01 catch-all) | 1 |
| **Total** | **176** |
