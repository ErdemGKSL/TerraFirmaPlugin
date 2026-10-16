# Image backlog

Images the project needs that don't exist yet. Spec fields as in `SKILL.md` section 2. When one is generated, move its spec into `art/raw/<id>/notes.md` and delete it here.

### ui-inventory-tab-button
- Target path: content/art/gui/inventory_tab_button.png
- Size: 48x24 px sheet, two 24x24 cells: normal (left) | hovered/highlighted (right)
- Used by: resource-pack sprite drawn just outside the player inventory window. Clicking there opens the TFC player screen (calendar/nutrition/climate). Drawn as a font glyph next to the player-inventory title through a CraftEngine `lang` override.
- Description: a small square button with a stone/wood frame, matching TFC GUI style, with a tiny calendar/leaf glyph in the centre suggesting "TFC info".
- References: TFC `assets/tfc/textures/gui/player_calendar.png`, `player_nutrition.png`, `player_climate.png` (tab icons/frames); vanilla inventory frame colours.
- Constraints: opaque frame, transparent outside the frame, no text. Keep it ≤ 32 px tall and readable at GUI scale 1. Exact placement/size may change after the inventory-button spike (ARCHITECTURE.md §14 item 6; see craftengine-gui-furniture skill).
- Needed by: tfc-feature-porting / player tabs decision, 2026-10-16

### pack-icon
- Target path: content/art/pack/pack.png
- Size: 128x128 px (draw at 32x32, upscale 4x nearest-neighbour: `--size 32x32 --upscale 4`)
- Used by: resource pack `pack.png` (CraftEngine generated pack)
- Description: square icon showing a layered rock cross-section (strata) with a small copper ingot and a sapling on top, evoking geology + primitive metallurgy. Must not use the TerraFirmaCraft logo or name.
- References: TFC rock textures `assets/tfc/textures/block/rock/raw/*.png`; `assets/tfc/textures/item/metal/ingot/copper.png`
- Constraints: pixel art, no text, opaque background allowed.
- Needed by: dependency-bootstrap / resource pack, 2026-10-16
