# GUI catalogue

Plan item: X-GUI-01 (`plans/90-cross-cutting.md`). Pinned TFC reference: `c3b4d9536` (`docs/porting/tfc-pin.txt`).
Last checked against the plans: 2026-10-19. Counts are generated (section 4).

## Scope

Every textured GUI and interaction screen the plans name. Each row has a plan item id, a TFC screen or container class for reference, and the slots or buttons it needs. Status is `needed` for every row: nothing is built in production.

Not in section 1:
- Plan mentions with no specific screen (quern, loom, bloomery, and similar): section 3.
- TFC screens that no plan item names (firebox, salad): section 3.
- Button sets and progress overlays: these are shared framework pieces (section 2), not screens. Section 1 uses three kinds: textured screen, interaction screen, inventory tab.
- HUD elements and item tooltips: `docs/hud/catalogue.md` (the blowpipe step text is `m6-glass-01-step-indicator` there).

Ids are the lower-case plan item id plus a name (`m4-anv-02-anvil`). Framework pieces use `fw-`. TFC references are file names with line numbers in the pinned clone; the sources are `src/main/java/net/dries007/tfc/`.

## 1. Screens

| id | what it does | source | kind | slots or buttons | status |
|---|---|---|---|---|---|
| `m4-anv-02-anvil-plan` | Pick the forging plan (recipe) for an anvil, paged. | `M4-ANV-02` · TFC `AnvilPlanScreen`, `AnvilPlanContainer` | textured screen | One `AnvilPlanSelectButton` per recipe. `NextPageButton` left at (7,56) and right at (160,56) (`AnvilPlanScreen` 75-82). No item slots. | needed |
| `m4-anv-02-anvil` | Forging: work and target markers, limits, step buttons, plan and weld buttons. Rock and metal anvil tiers are both in the plan. | `M4-ANV-02` · TFC `AnvilScreen`, `AnvilContainer` | textured screen | Slots: main input (22,76), second input (22,20), hammer (138,76), catalyst (22,38). Buttons: `AnvilPlanButton`, `AnvilWeldButton`, one `AnvilStepButton` per offered step (`AnvilScreen` 48-53). Overlays: `fw-progress-bars`, `fw-title-glyphs`. | needed |
| `m4-forge-01-charcoal-forge` | Charcoal forge: fuel, temperature bar, inputs and extra slots. | `M4-FORGE-01` · TFC `CharcoalForgeScreen`, `CharcoalForgeContainer` | textured screen | Fuel slots 5 at (80,70), (98,52), (62,52), (116,34), (44,34). Input slots 5 at (80,52), (98,34), (62,34), (116,16), (44,16). Extra slots 4 (block entity slots 10 to 13). Temperature bar overlay at (8, 76 - min(51, temp)), 15x5. | needed |
| `m3-heat-03-firepit` | Firepit: four fuel slots, one input, two outputs, temperature bar. | `M3-HEAT-03` · TFC `FirepitScreen`, `FirepitContainer` | textured screen | Fuel 4 at (8, 70 - 18i). Input (80,29). Outputs (71,57) and (89,57). Temperature bar overlay at (30, 76 - min(51, temp)), 15x5. | needed |
| `m3-heat-03-grill` | Grill: four fuel slots, five grill input slots, temperature bar. | `M3-HEAT-03` · TFC `GrillScreen`, `GrillContainer` | textured screen | Fuel 4 at (8, 70 - 18i). Inputs 5 at (62 + 18k, 20), block entity slots 4 to 8. Temperature bar overlay at (30, 76 - min(51, temp)), 15x5. | needed |
| `m3-heat-03-pot` | Pot: four fuel slots, five ingredient slots, temperature bar, cooking progress. Makes soups and jams. | `M3-HEAT-03` · TFC `PotScreen`, `PotContainer` | textured screen | Fuel 4 at (8, 70 - 18i). Ingredients 5 at (65,23), (83,23), (56,41), (74,41), (92,41). Temperature bar overlay at (30, ...), 15x5. Progress sprite 24x6 at (131,33). Three 13x13 status sprites at x 121, 136, 151, y 58 (`PotScreen` 112-161). | needed |
| `m3-knap-01-knapping` | Knapping: a 5x5 pattern of buttons and one output slot. Types: stone, clay, fire clay, leather, goat horn. | `M3-KNAP-01` · TFC `KnappingScreen`, `KnappingContainer`, `KnappingButton` | textured screen | 25 `KnappingButton`s, 16x16, in a 5x5 grid (`KnappingScreen` 80, index x + 5y), fitting columns 2 to 6 of a 6-row chest. One output slot at (128,46) (`KnappingContainer` 158). | needed |
| `m3-proc-03-barrel` | Barrel: fluid in and out, one item slot, seal and unseal, fluid tank. | `M3-PROC-03` · TFC `BarrelScreen`, `BarrelContainer` | textured screen | Slots: fluid in (35,20), fluid out (35,54), item (89,37). `BarrelSealButton` (seal or unseal). Tank frame 18x52 at (7,19) (`BarrelScreen` 104). Slot edits are refused while sealed (`BarrelContainer` 40). | needed |
| `m4-cruc-01-crucible` | Crucible: nine melt input slots, one output, temperature bar, scroll marker. | `M4-CRUC-01` · TFC `CrucibleScreen`, `CrucibleContainer` | textured screen | Nine inputs (block entity slots 0 to 8) at (26 + 18 column, 82 + 18 line). Output (152,100). Temperature bar overlay at (7, 131 - min(temp, 51)), 15x5. Scroll marker at (154, 11 + scroll), 12x15 (`CrucibleScreen` 121-125). | needed |
| `m4-blast-01-blast-furnace` | Blast furnace: one output slot, temperature bar, two tank overlays. | `M4-BLAST-01` · TFC `BlastFurnaceScreen`, `BlastFurnaceContainer` | textured screen | One slot at (152,17). Temperature bar overlay at (8, 76 - min(51, temp)), 15x5. Tank overlays 10x66 (`BlastFurnaceScreen` 112-127). | needed |
| `m3-proc-02-sewing-table` | Sewing: yarn, tool, two inputs and a result. A 12-pixel grid of squares and stitches. | `M3-PROC-02` · TFC `SewingTableScreen`, `SewingTableContainer` | textured screen | Slots: yarn 0, tool 1, inputs 2 and 3, result 4 (`SewingTableContainer` 62-67). Two `LegacyImageButton` variants (silent false at `SewingTableScreen` 118, true at 134). Clickable 12-pixel grid cells (`SewingTableScreen` 224-313). | needed |
| `m6-deco-01-scribing-table` | Scribing table: combiner-style item slots, fluid tanks, one bar in two states. | `M6-DECO-01` · TFC `ScribingTableScreen` (`ItemCombinerScreen`), `ScribingTableContainer` (`ItemCombinerMenu`) | textured screen | Item slots from the `ItemCombinerMenu` layout. One tank display per fluid tank (`ScribingTableContainer` 49). Bar 110x16 at (59,20) in two states (`ScribingTableScreen` 150, 158). | needed |
| `m6-deco-03-powderkeg` | Powderkeg: twelve storage slots, seal and unseal. | `M6-DECO-03` · TFC `PowderkegScreen`, `PowderkegContainer` | textured screen | Twelve slots, 4 columns by 3 rows, at (25 + 18x, 19 + 18y) (`PowderkegContainer` 68-72). `PowderkegSealButton` (`PowderkegScreen` 37). | needed |
| `m3-pot-03-large-vessel` | Large vessel: nine storage slots, seal and unseal. | `M3-POT-03` · TFC `LargeVesselScreen`, `LargeVesselContainer` | textured screen | Nine slots at x 62, 80, 98 and y 19, 37, 55 (`LargeVesselContainer` 73-81). `VesselSealButton` at (guiLeft + 9, guiTop) (`LargeVesselScreen` 36). | needed |
| `m3-pot-02-small-vessel` | Small vessel inventory: four slots. | `M3-POT-02` · TFC `SmallVesselInventoryScreen`, `SmallVesselInventoryContainer` | textured screen | Four slots at (71,23), (89,23), (71,41), (89,41) (`SmallVesselInventoryContainer` 87-90). | needed |
| `m3-pot-02-mold-alloy` | Mold screen, opened from the held mold item: one slot. | `M3-POT-02` · TFC `MoldLikeAlloyScreen`, `MoldLikeAlloyContainer` (opened by `MoldItem`) | textured screen | One slot at (80,34) (`MoldLikeAlloyContainer` 115). | needed |
| `m5-liv-02-nest-box` | Nest box: four egg slots. Eggs hatch lazily. Pests can raid it. | `M5-LIV-02` · TFC `NestBoxScreen`, `NestBoxContainer` (`PestContainer`) | textured screen | Four slots at (71,23), (89,23), (71,41), (89,41) (`NestBoxContainer` 40-43). | needed |
| `m5-pet-01-pet-command` | Command screen for a tamed pet. | `M5-PET-01` · TFC `PetCommandScreen` (no container; `PetCommandPacket`) | interaction screen | One `Button` per command the pet accepts, from y = 72 down (`PetCommandScreen` 36-46). The plan replaces the packet with a vanilla trigger (sneak and right-click). | needed |
| `m3-gui-02-workbench` | Workbench crafting with TFC recipes (advanced shaped and shapeless). | `M3-GUI-02` · TFC `TFCWorkbenchContainer` (extends `CraftingMenu`; no TFC screen class) | interaction screen | 3x3 crafting grid and output, in the vanilla crafting layout. | needed |
| `m3-gui-01-player-tab-calendar` | Player tabs, calendar page. | `M3-GUI-01` (M2-CAL-01) · TFC `CalendarScreen` | inventory tab | Tab buttons from `PlayerInventoryTabButton`: INVENTORY, CALENDAR (active), NUTRITION, CLIMATE, and BOOK when Patchouli is installed (`CalendarScreen` 45-61). No slots. | needed |
| `m3-gui-01-player-tab-nutrition` | Player tabs, nutrition page with one bar per nutrient group. | `M3-GUI-01` (M3-SURV-02) · TFC `NutritionScreen` | inventory tab | Same tab buttons, NUTRITION active (`NutritionScreen` 44-60). Five nutrient bars as title glyph overlays (`fw-progress-bars`, `fw-title-glyphs`). | needed |
| `m3-gui-01-player-tab-climate` | Player tabs, climate page. | `M3-GUI-01` (M2-CLIM-03) · TFC `ClimateScreen` | inventory tab | Same tab buttons, CLIMATE active (`ClimateScreen` 46-62). Climate readout data from M2-CLIM-03. | needed |

## 2. Shared framework pieces

The plans name these pieces, and X-GUI-01 asks for them as shared helpers so each device GUI stays small.

How a screen uses them (PAR-INFRA-03): declare its images in `core/gui` (a `GuiImage` per background or overlay, at window
coordinates; add them to `GuiImages.all()`), give datagen `gui/GuiArt` a recipe for each (re-layout the TFC background
with `ChestRelayout`, cut sprites into frame grids), then build a `paper/gui/TfpGui` (inputs with a `SlotPredicate`,
outputs, buttons, overlay frames from the session state, a lock) and open it with `GuiService.open` on the player's
thread. The sample is `SampleScreen`/`SampleGui` (TFC's large vessel art in a 3-row chest) behind `/tfp debug gui sample`.

| id | piece | what it does | asked by | status |
|---|---|---|---|---|
| `fw-tab-button` | Outside-click tab button | Opens the player tab screens from a click outside the inventory window (slot -999). The pack draws the button (CraftEngine `container.crafting` lang glyph, see the skill). M0-GUI-01 is marked `[x]` in the plans; this catalogue does not re-check it. TFC reference: `PlayerInventoryTabButton` (five tabs), `network/SwitchInventoryTabPacket`, tabs added to the inventory screen in `client/ClientForgeEventHandler` 379-394. | M0-GUI-01 (trigger and 6-row alignment), M3-GUI-01 (outside-click trigger) | needed |
| `fw-title-glyphs` | Title glyph overlays | Glyphs in a container title (CraftEngine `<image>` and `<shift>` tags, or codepoints from the CE font manager) that draw bars and markers over the screen. Built once per CE reload. TFC draws the same markers with `blit` (`AnvilScreen` 75-150). | M4-ANV-02 (target and progress markers drawn as title glyph overlays), M3-GUI-01 (title glyph overlays for the bars), X-GUI-01 (title glyph builder via CE codepoints) | built (PAR-INFRA-03: `core/gui/TitleGlyphs`, `GuiImage`, `paper/gui/GuiTitles`; in-game check pending) |
| `fw-title-refresh` | Title refresh throttling | Changes a title with `InventoryView#setTitle`, rate-limited. Each change sends one OpenScreen packet. | X-GUI-01 (title refresh throttling) | built (PAR-INFRA-03: `core/gui/TitleRefreshThrottle`, 4 per second, latest state wins; `GuiSession.refresh`) |
| `fw-overlay-layers` | Overlay layers | A helper that stacks overlays (bars, markers) over a screen background in a fixed order. | X-GUI-01 (overlay layers), M4-ANV-02 (markers) | built (PAR-INFRA-03: `core/gui/GuiLayers`, z order then declaration order) |
| `fw-progress-bars` | Progress and temperature bars | Temperature bars (15x5), cooking and forging progress, tank frames and overlays. TFC draws them in `FirepitScreen` 39, `PotScreen` 112-161, `CrucibleScreen` 121-125, `BlastFurnaceScreen` 112-127, `BarrelScreen` 104. | M4-ANV-02 (target and progress markers), M3-GUI-01 (bars), M3-HEAT-03 (GUIs with TFC textures), M4-CRUC-01 (crucible GUI). M4-BLAST-01 and M4-FORGE-01 name no bar; their TFC screens draw one. | built (PAR-INFRA-03: `core/gui/ProgressFrames`; datagen thermometer strip `tfp:gui/thermometer`, 52 frames) |
| `fw-slot-layouts` | Slot layouts | Shared slot placement rules: the 6-row chest for knapping (5x5 in columns 2 to 6), the nine-slot large vessel, the workbench 3x3 with output, and the anvil step buttons as slot items. | M3-KNAP-01 (5x5 grid in columns 2 to 6 of a 6-row chest), M3-POT-03 (nine slots), M4-ANV-02 (step buttons as slot items), M3-GUI-02 (workbench) | built (PAR-INFRA-03: `core/gui/SlotLayout`, TFC slots moved by one `dy` onto the chest grid) |
| `fw-buttons` | Button art and hit areas | Buttons as slot items (empty model and tooltip) or as widgets with their own art: seal, next page, plan select, knapping grid, pet commands, sewing toggles. | M4-ANV-02 (step buttons as slot items), M3-KNAP-01 (knapping buttons), M3-PROC-03 (barrel sealing), M3-POT-03 (seal and unseal), M5-PET-01 (commands) | built (PAR-INFRA-03: `paper/gui/GuiButtons`, empty item model `tfp:gui/blank`, name = tooltip; state art as overlay frames) |
| `fw-alignment` | Pixel alignment | The alignment math for textured chest-style GUIs at GUI scales 1 to 4, and the per-GUI alignment checklist. | M0-GUI-01 (6-row GUI pixel-perfect at scales 1 to 4), M3-KNAP-01 (GUI aligned), M3-PROC-02 (GUIs aligned), M3-GUI-01 (alignment checklist), M4-ANV-02 (GUI checklist) | built (PAR-INFRA-03: `core/gui/ChestGeometry`, `Glyphs`; datagen `gui/ChestRelayout`; in-game check at scales 1 to 4 pending) |
| `fw-background-art` | Background art | Textured backgrounds as CE images with a font. TFC source art lives in `textures/gui/`. | X-ART-01 (GUI overlays in the art list), M3-HEAT-03 (TFC `gui/fire_pit*.png`), M3-GUI-01 (`gui/player_*.png`), M4-ANV-02 (`gui/anvil*.png`) | built (PAR-INFRA-03: datagen `gui/GuiArt` writes `configuration/gui.yml` and `textures/font/gui/*.png`, font `tfp:gui`) |

## 3. Unspecified in plan

Plan mentions of a GUI or a device with no specific screen. These get no row in section 1 until a plan item names a screen.

| plan item | what the plan says | TFC reference | open question |
|---|---|---|---|
| `M3-PROC-02` (loom) | "GUIs aligned" in Done when; the loom is named | `LoomBlockEntity` opens no menu | Does the loom have a screen at all? |
| `M3-PROC-02` (scraping) | "GUIs aligned" in Done when; scraping is named | `ScrapingBlockEntity` opens no menu | Does scraping have a screen at all? |
| `M3-PROC-01` (quern) | Visual checklist; no GUI word. The handstone is an animation (craftengine-gui-furniture 1.8). | `QuernBlockEntity` opens no menu | Confirm no screen is wanted. |
| `M3-PAN-01` (sluice) | No GUI word. | `SluiceBlockEntity` opens no menu | None unless a screen is wanted. |
| `M4-BLOOM-01` (bloomery) | No GUI word. | `BloomeryBlockEntity` opens no menu | None unless a screen is wanted. |
| `M4-CRUC-01` (mold table) | "mold table filling"; no GUI word. | `MoldTableBlockEntity` opens no menu | None unless a screen is wanted. |
| `M6-MECH-03` (trip hammer, power loom, pumps) | No GUI word. | `rotation/TripHammerBlockEntity`, `PowerLoomBlockEntity`, `PumpBlockEntity` open no menu | None unless a screen is wanted. |
| `M6-MECH-01` (status) | "the GUI and readout" for rotation (`RotationDisplayStyle`, `BlockEntityTooltips`). | No screen; readout and tooltip | Is the rotation readout a tooltip, a HUD element or a screen? |
| `M6-GLASS-01` (blowpipe) | "textured GUI or action bar steps (divergence if needed)". | No glass screen; the step text is HUD (`GlassblowingOverlays`) | Decide GUI or action bar. The HUD side is `m6-glass-01-step-indicator`. |
| `M6-GUIDE-01` (field guide) | "decide the form (textured GUI book, written book, or web guide)". | Patchouli book tab only (`PlayerInventoryTabButton.Tab.BOOK`) | Form undecided (PRD open question). |
| `M5-MNT-01` (mounts) | "chests on donkeys/mules". | Vanilla horse inventory; no TFC screen | Is the vanilla horse screen enough? |
| `M6-DECO-01`, `-02`, `-03`, `-04` (status) | Each status line lists "the GUI tables" as open. No screen is named. | Not stated in the plan | Which screens are meant? Probably the scribing and sewing tables. Confirm. |
| `M6-DECO-01` (chests) | Chests and trapped chests are named as blocks; no GUI word. | `RestrictedChestContainer` extends vanilla `ChestMenu`; no TFC screen | Vanilla chest screen, or a textured one? |

TFC screens that no plan item names (not in section 1):
- `FireboxScreen`, `FireboxContainer` (opened by `FireboxBlock`).
- `SaladScreen`, `SaladContainer`.
- `CreateTFCWorldScreen`: world creation, not gameplay. Excluded from the counts.

## 4. Counts

Method: `/tmp/claude-0/-home-user-TerraFirmaPlugin/7076116e-0f7a-5b74-a249-6fd913ce4602/scratchpad/gui-count.py` parses the section 1, 2 and 3 tables of this file (rows whose first cell is a backtick id; section 3 rows whose first cell starts with a plan id). It reads the kind from the fourth cell and the milestone from the first `M<n>-` in the source cell. It also counts the TFC classes in the pinned clone (`client/screen` and `common/container`, top-level class declarations, `package-info` and interfaces excluded). The block below is rewritten by that script.

<!-- counts:begin -->
Counts generated by `gui-count.py` from this file's tables and the pinned TFC clone.

| measure | count |
|---|---|
| screens in section 1 | 22 |
| framework pieces in section 2 | 9 |
| rows in section 3 (unspecified plan items; the DECO status row covers four plan items) | 13 |
| TFC screen and menu classes named as unplanned (section 3) | 4 |

Screens by kind:

| kind | count |
|---|---|
| textured screen | 17 |
| interaction screen | 2 |
| button set | 0 |
| progress overlay | 0 |
| inventory tab | 3 |
| **total** | **22** |

Screens by milestone (first plan id in the source cell):

| milestone | count |
|---|---|
| M3 | 13 |
| M4 | 5 |
| M5 | 2 |
| M6 | 2 |
| **total** | **22** |

TFC reference (pinned clone, `client/screen` and `common/container`):

| measure | count |
|---|---|
| gameplay screen classes (`client/screen`) | 23 |
| of which in section 1 | 21 |
| of which unplanned (section 3) | 2 |
| menu classes (`common/container`, concrete) | 21 |
| of which in section 1 (rows whose source names them) | 18 |
| of which unplanned (section 3) | 3 |
| widget button classes (`client/screen/button`) | 11 |
| excluded: base screens, particle, world creation | 4 |
<!-- counts:end -->
