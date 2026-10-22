# HUD element catalogue

Plan item: X-HUD-01 (`plans/90-cross-cutting.md`). Pinned TFC reference: `c3b4d9536` (`docs/porting/tfc-pin.txt`).
Last checked against the plans: 2026-10-19.

## Scope

Every HUD element the plans name on the player's screen outside an inventory GUI: BetterHud elements, the placeholders they read, popups, and the vanilla bars the plans say to hide.

Not listed here (other tracks own them):
- GUI screens and their overlays: M0-GUI-01, M3-GUI-01, M4-ANV-02 (glyph markers), M4-CRUC-01, M4-INGOT-01.
- Item tooltips: M3-HEAT-01 (heat line), M4-MET-01 (heat tooltips), M3-SURV-01 (freshness line), M3-SURV-05 (size line).
- World-space visuals: `entity_renderer` blocks, item displays, pack textures.

Ids are the lower-case plan item id plus an element name (`m3-surv-06-health-bar`). Placeholders use `ph-` (`m1-int-04-ph-health`). Status is `needed` for every row: no element is built in production. Notes say where a spike or a partial check already exists.

## 1. Elements

| id | shows | source | kind | data | milestone | status | notes |
|---|---|---|---|---|---|---|---|
| `m1-int-04-ph-health` | value behind the health bar | M1-INT-04 | placeholder | `tfp_health` (snapshot) | M1 | needed | Also a listener `tfp_health` (M1-INT-04). Glue is `integration/betterhud`. |
| `m1-int-04-ph-max-health` | max value behind the "45/50" text | M1-INT-04 | placeholder | `tfp_max_health` (snapshot) | M1 | needed | Display scale is open (M3-SURV-04: pinned display gives 850 max at average nutrition, not 50). |
| `m1-int-04-ph-thirst` | value behind the thirst bar | M1-INT-04 | placeholder | `tfp_thirst` (snapshot, TFC max 100) | M1 | needed | Also a listener `tfp_thirst` (0..1). Rules in M3-SURV-03. |
| `m1-int-04-ph-hunger` | value behind the hunger bar | M1-INT-04 | placeholder | `tfp_hunger` (snapshot) | M1 | needed | Also a listener `tfp_hunger`. Source is the TFC hunger model (M3-SURV-02). |
| `m3-surv-06-ph-air` | breath left behind the air bar | M3-SURV-06 | placeholder | `tfp_air` (snapshot, vanilla breath 0..300) | M3 | needed | Also a listener `tfp_air` (0..1). Source is `Player.getRemainingAir`. |
| `m3-surv-06-ph-armor` | armour points behind the armour bar | M3-SURV-06 | placeholder | `tfp_armor` (snapshot, vanilla armour attribute 0..30) | M3 | needed | Also a listener `tfp_armor` (0..1 on vanilla's 20-point scale; TFC's best full set, blue or red steel, is 3+6+8+3 = 20, `TFCArmorMaterials`). Source is `Attribute.ARMOR`. |
| `m1-int-04-ph-held-temp` | held item temperature for the popup | M1-INT-04 | placeholder | `tfp_held_temp` (snapshot, item heat from M3-HEAT-01) | M1 | needed | Popup condition `tfp_held_temp > 0` (betterhud skill). |
| `m2-cal-02-ph-date` | calendar date text | M2-CAL-02 | placeholder | `tfp_date` (snapshot of the published calendar) | M2 | needed | `tick: 20` on the element that reads it. |
| `m2-cal-02-ph-season` | season name | M2-CAL-02 | placeholder | `tfp_season` (snapshot of the published calendar) | M2 | needed | `tick: 20` on the element that reads it. |
| `m2-clim-03-ph-ambient-temp` | ambient temperature at the player | M2-CLIM-03 | placeholder | `tfp_ambient_temp` (snapshot, movement throttle) | M2 | needed | Also a listener `tfp_ambient_temp` (-20..40 °C as 0..1, `HudValues.thermometer`) for the thermometer bar. Chunk climate input is neutral until the M2-GEN-05 wiring (ledger divergence). |
| `m3-surv-06-health-bar` | health bar with "45/50" text | M3-SURV-06 | bar | `tfp_health`, `tfp_max_health` | M3 | needed | Spike M0-BH-01 is `[x]` for health and thirst on 1.21.11 and 26.3. TFC reference: `IngameOverlays.HEALTH`. TFC's 81x9 sprite; the text sits inside the bar with a black shadow (TFC's dark-red one looked muddy over the empty channel; section 5, ledger). |
| `m3-surv-06-thirst-bar` | thirst bar | M3-SURV-06 | bar | `tfp_thirst` (listener, 0..1) | M3 | needed | Spike M0-BH-01 `[x]`. TFC reference: `IngameOverlays.THIRST`. TFC's 81x5 sprite, one 6 px step above hunger (section 5). |
| `m3-surv-06-hunger-bar` | hunger bar | M3-SURV-06 | bar | `tfp_hunger` (listener) | M3 | needed | The BetterHud built-in `food` class only works if the vanilla food level is synced (M3-SURV-02 open item). TFC reference: `IngameOverlays.FOOD`. TFC's 81x5 food sprite recoloured orange (divergence, section 5). |
| `m3-surv-06-air-bar` | air bar (breath left), plain light-blue fill | M3-SURV-06 | bar | `tfp_air` (listener, 0..1) | M3 | needed | Replaces the vanilla air bubbles (hidden by `m3-surv-06-hide-air`). Compact 81x7 bar directly above thirst, only while `tfp_air < 300`; red fill below 60 (section 5). |
| `m3-surv-06-armor-bar` | armour bar (armour points), plain steel-grey fill | M3-SURV-06 | bar | `tfp_armor` (listener, 0..1) | M3 | needed | Replaces the vanilla armour row (hidden by `m3-surv-06-hide-armor`). Thin 81x7 bar directly above health with one empty row between, only while `tfp_armor > 0` (section 5). |
| `m3-surv-06-held-temp-popup` | held item temperature, shown while above 0 | M3-SURV-06 (via M3-HEAT-01) | popup | `tfp_held_temp` | M3 | needed | Item tooltip line is separate and not HUD. Text `[tfp_held_temp]°C`, whole degrees, black shadow. |
| `m2-clim-03-ambient-temp-bar` | thermometer bar beside the temperature line, top-left | M2-CLIM-03 | bar | `tfp_ambient_temp` (listener, -20..40 °C) | M2 | needed | Icy blue to hot red; on the info plate (section 5). |
| `m2-cal-02-info-plate` | stone plate behind the date and temperature lines | M2-CAL-02 | overlay | `tfp_date` (backs the date readout) | M2 | needed | 124x22 at 1,1. |
| `m2-cal-02-date-readout` | on-screen date and season, top-left | M2-CAL-02 | readout | `tfp_date`, `tfp_season`; text `tick: 20` | M2 | needed | M2-CAL-02 is `[x]` for commands only. Generated by `BetterHudPack` (`tfp_date`), black text shadow (`outline: true`). |
| `m2-clim-03-ambient-temp-readout` | ambient temperature line | M2-CLIM-03 | readout | `tfp_ambient_temp`; text `tick: 20` (betterhud skill: climate readouts) | M2 | needed | M2-CLIM-03 is `[x]`: its HUD line matched `/tfp debug chunk` on a 1.21.11 test server. Generated by `BetterHudPack` (`tfp_ambient_temp`), text `8.3°C` (TFC's unit), black text shadow. |
| `m3-surv-02-nutrition-readout` | nutrition values (5 groups) | M3-SURV-02 | readout | unspecified in plan (skill example: `tfp_nutrient` with a nutrient argument) | M3 | needed | unspecified in plan. M3-SURV-02 says "nutrient HUD/GUI" and cites M3-SURV-06 and M3-GUI-01, which do not list it. The betterhud skill says nutrition is in the inventory GUI, not the HUD. Decide. |
| `m6-mech-01-rotation-readout` | rotation speed readout | M6-MECH-01 | readout | unspecified in plan (rotation network speed, `core/rotation`) | M6 | needed | unspecified in plan. M6-MECH-01 and M6-MECH-02 say "GUI and readout" (`RotationDisplayStyle`, `BlockEntityTooltips`). This may be a tooltip, not a HUD element. Decide. |
| `m4-pros-01-result-message` | propick scan result | M4-PROS-01 | popup | unspecified in plan (`ProspectResult`, `core/prospect`) | M4 | needed | unspecified in plan. M4-PROS-01 says "result message and the actionbar/chat choice". Action bar, chat or BetterHud popup is not chosen. |
| `m6-glass-01-step-indicator` | blowpipe step text or progress | M6-GLASS-01 | indicator | unspecified in plan (glassworking state, `GlassState`) | M6 | needed | unspecified in plan. M6-GLASS-01 says "textured GUI or action bar steps". TFC reference: `GlassblowingOverlays` (crosshair text while holding a blowpipe). |
| `m3-surv-06-hide-hearts` | vanilla hearts row | M3-SURV-06 | hidden-vanilla | transparent `gui/sprites/hud/heart/*.png` (47 files) | M3 | needed | Spike M0-BH-01 `[x]`. The set also hides the vanilla mount-health row (`heart/vehicle_*`). See section 2. |
| `m3-surv-06-hide-food` | vanilla food row | M3-SURV-06 | hidden-vanilla | transparent `gui/sprites/hud/food_*.png` (6 files) | M3 | needed | Spike M0-BH-01 `[x]`. |
| `m3-surv-06-hide-air` | vanilla air bubbles | M3-SURV-06 | hidden-vanilla | transparent `air.png`, `air_bursting.png`, `air_empty.png` | M3 | needed | Spike M0-BH-01 `[x]`. |
| `m3-surv-06-hide-armor` | vanilla armour row | M3-SURV-06 | hidden-vanilla | transparent `armor_empty.png`, `armor_full.png`, `armor_half.png` | M3 | needed | Replaced by `m3-surv-06-armor-bar`, so nothing duplicates. |

## 2. Vanilla bars to hide

The plans name four bars to hide (the armour row since the armour bar, 2026-10-22). The hotbar and crosshair stay (M0-BH-01 done-when: "the hotbar is kept"). The sprite list is from the betterhud skill (section 6.2 of its research report) and must be rechecked per MC version (X-HUD-01).

| vanilla bar | plan item that hides it | sprites to ship as transparent | notes |
|---|---|---|---|
| hearts (health row) | M3-SURV-06 "Hide vanilla hearts/food/air"; spike M0-BH-01 | `gui/sprites/hud/heart/*.png`, all 47 in 1.21.11 (incl. `container*`, `vehicle_*`, `*_blinking`, `hardcore_*`) | Also hides the mount-health row (`vehicle_*`). See "not named" below. |
| food (hunger row) | M3-SURV-06; spike M0-BH-01 | `gui/sprites/hud/food_*.png` (6) | |
| air (bubbles) | M3-SURV-06; spike M0-BH-01 | `gui/sprites/hud/air.png`, `air_bursting.png`, `air_empty.png` | |
| armour row | M3-SURV-06 armour bar (owner request 2026-10-22) | `gui/sprites/hud/armor_empty.png`, `armor_full.png`, `armor_half.png` | Replaced by the armour bar above health. |

Not named by any plan item (decide in X-HUD-01 or add to M3-SURV-06):
- **Experience bar and XP level.** No plan item names it. TFC hides the vanilla XP bar under a config switch (`IngameOverlays.EXPERIENCE`). The research report keeps XP.
- **Mount health and jump meter.** No plan item names them. The heart set hides the mount-health row unless `vehicle_*` is excluded (the report flags this as "decide"). TFC draws its own mount-health row (`IngameOverlays.MOUNT_HEALTH`) and jump bar (`IngameOverlays.JUMP_BAR`).

## 3. Counts

Method: a Python script (`/tmp/claude-0/-home-user-TerraFirmaPlugin/7076116e-0f7a-5b74-a249-6fd913ce4602/scratchpad/hud_counts.py`, not checked in) reads section 1. It takes each row whose first cell is a backticked id, reads the kind (column 4), the milestone (column 6) and the `unspecified in plan` text in the source and data columns, and counts them. Section 2 rows are not counted.

Total elements: **28**. Of these, 4 are unspecified in plan (rows marked above).

| kind | count | of which unspecified in plan |
|---|---|---|
| bar | 6 | 0 |
| overlay | 1 | 0 |
| readout | 4 | 2 |
| popup | 2 | 1 |
| indicator | 1 | 1 |
| placeholder | 10 | 0 |
| hidden-vanilla | 4 | 0 |
| **total** | **28** | **4** |

| milestone | elements | of which placeholder | of which unspecified in plan |
|---|---|---|---|
| M1 | 5 | 5 | 0 |
| M2 | 7 | 3 | 0 |
| M3 | 13 | 2 | 1 |
| M4 | 1 | 0 | 1 |
| M6 | 2 | 0 | 2 |
| **total** | **28** | **10** | **4** |

## 4. TFC overlay reference (not counted)

From `src/main/java/net/dries007/tfc/client/` at `c3b4d9536`. `IngameOverlays` is the enum of layers registered in `registerOverlays`. Reference only: a row here is not an element unless a plan item asks for it.

| TFC class or enum | what it draws (from the code) | plan coverage |
|---|---|---|
| `IngameOverlays.HEALTH` (`renderHealth`) | TFC health row | `m3-surv-06-health-bar` |
| `IngameOverlays.MOUNT_HEALTH` (`renderMountHealth`) | mount health row while riding | none (section 2) |
| `IngameOverlays.FOOD` (`renderFood`) | hunger row | `m3-surv-06-hunger-bar` |
| `IngameOverlays.THIRST` (`renderThirst`) | thirst row | `m3-surv-06-thirst-bar` |
| `IngameOverlays.EXPERIENCE` (`renderExperience`) | XP bar replacement, with fishing state | none (section 2) |
| `IngameOverlays.JUMP_BAR` (`renderJumpBar`) | jump meter | none (section 2) |
| `IngameOverlays.HUD_MOVER` (`moveLeftAndRightHeights`) | nudges the vanilla left and right heights for the XP style; layout only | none |
| `IngameOverlays.CHISEL` (`renderChiselMode`) | chisel mode | none (M4-CHIS-01 cycles modes, names no display) |
| `IngameOverlays.INK` (`renderInk`) | ink splatter in first person | none |
| `IngameOverlays.FAMILIARITY` (`renderFamiliarity`) | animal familiarity while sneaking | none (M5-LIV-01 has familiarity, no HUD) |
| `GlassblowingOverlays.render` | step text under the crosshair while holding a blowpipe | `m6-glass-01-step-indicator` |
| `HoeOverlays.render` | hoe info text under the crosshair | none |
| `CrateOverlays.render` | crate contents text under the crosshair | none |

## 5. Layout and layers (two-layer redesign, 2026-10-22)

Plan and rules: `plans/hud-layered-redesign.md`. Source of truth: `tools/datagen` `HudLayouts.BAR_SPECS` / `OVERLAYS` (placement), `HudArt` and the art resources (`tools/datagen/src/main/resources/dev/tfp/datagen/hud/`). Style: plain, close to TFC's own bars (1 px dark frame, flat fill with one lighter and one darker row, 3-4 colours per bar, no ornaments). Every bar is two layers derived from one generated master (`docs/art/hud/<name>_master.png`, raws and prompts in `art/raw/hud-*/`) by `tools/images/hud_layers.py`: `<name>_bg.png` (frame + darkened empty interior) behind and `<name>_fill.png` (interior only) in front, same canvas; BetterHud clips the fill left-to-right (`listener`, `split-type: left`, one frame per interior column). All layouts use `offset: left` (x = the canvas's left edge) and each layer's y is the bar's y plus the layer's first opaque row (BetterHud trims transparent rows without moving the image down).

GUI pixels from the bottom-centre (`H` = GUI height) unless noted. Columns are given twice: the **layout** x that the datagen writes (`HudLayouts.BAR_SPECS`) and the **screen** columns measured in the GUI scale 2 and 3 screenshots (2026-10-22), where BetterHud's bottom-centre anchor lands 1 px left of the layout x (`HudLayouts.SCREEN_SHIFT_X`). On screen the left column is vanilla's hearts (centre-91 .. -11) and the right column vanilla's food row (centre+10 .. +90): mirror images about the hotbar's centre line (hotbar centre-91 .. +90). (Before 2026-10-22 this table gave the right column as centre+11 .. +91, which mixed the layout x with screen pixels; the layout did not change.)

| element | canvas | interior (fill mask) | layout x | screen columns | rows | z (layer) | shown |
|---|---|---|---|---|---|---|---|
| armour | 81x7 | x 1..79, y 1..5 | -90 | centre-91 .. centre-11 | H-48 .. H-42 (one empty row, H-41, above health) | bg 1, fill 2 | `tfp_armor > 0` (plain steel-grey fill, 0..20 points) |
| health + "850/850" | 81x10 | x 1..79, y 1..8 | -90 | centre-91 .. centre-11 | H-40 .. H-31 | bg 1, fill 2, text 3 (shadow `FF000000`, black) | always |
| hunger | 81x7 | x 1..79, y 1..5 | +11 | centre+10 .. centre+90 | H-37 .. H-31 | bg 1, fill 2 | always |
| thirst | 81x7 | x 1..79, y 1..5 | +11 | same | H-45 .. H-39 | bg 1, fill 2 | always |
| air (plain fill) | 81x7 | x 1..79, y 1..5 | +11 | same | H-53 .. H-47 | bg 1, fill / fill_critical 2 | `tfp_air < 300`; red fill below 60 (one condition on the value, the same at every GUI scale; `HudLayoutsTest.airRedThresholdIsOneScaleIndependentConditionAt60`) |
| info plate | 124x22 | single layer | 1 | screen x 1..124 (gui 0/0) | screen y 1..22 | 0 | always |
| thermometer | 64x8 | tube x 1..62 y 2..5, bulb x 1..3 y 1 and 6 | 6 | screen x 6..69 | screen y 13..20 | bg 1, fill 2 | always |
| date / temperature text | text | | | x 6 / x 73 | y 5 / y 13 | 3 | always |

Vanilla for comparison: XP bar H-29 .. H-25, hotbar H-22 .. H-1, armour row H-49 .. H-41 (left, hidden and replaced by the armour bar), XP number centred around H-35 .. H-28. No bar overlaps the XP bar, its number or the hotbar; both columns have one empty row between bars (`HudLayoutsTest.barsStayClearOfTheXpRowHotbarAndXpNumber`, `leftColumnIsArmourAboveHealthWithOneEmptyRow`, `columnsMirrorEachOtherOnScreen`). Status effect icons are drawn top-right, far from both columns.

Art pipeline (2026-10-22): every PNG is indexed colour (4-bit for the bars, 8-bit for the thermometer ramp), index 0 the only transparent entry, one fixed palette per bar pinned in its `layers.json` (outline black first, the same index order in master, bg and fills), losslessly compressed (`tools/images/hud_palette.py`). The frame is pure black (`"ring": "outline"`): the earlier `"shade"` turned the fill-coloured end columns of the downscaled masters into a dark red (orange, blue) fringe at the bar ends. Before/after crops: `art/raw/hud-defringe/`.

Divergences from TFC (ledger): new plain art instead of TFC's overlay sheet; an armour bar instead of vanilla's armour icons (TFC keeps the vanilla row); hunger is orange; black health-text shadow instead of TFC's dark red; air is a plain light-blue bar above thirst instead of the vanilla bubbles (same visibility rule: only below full breath); a thermometer bar and plate on the HUD (TFC shows climate in the inventory's climate screen). The BetterHud demo compass is switched off.

Not verified in game yet: `layer` order and the `offset: left` reading (from the BetterHud 2.0 bytecode, `LayoutComponentContainer`).
