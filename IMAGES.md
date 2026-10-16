# Image requests

The queue of 2D images this project needs. **Claude** appends requests (see `.claude/skills/image-requests/`). **Codex/ChatGPT** generates them (see `AGENTS.md`, command `/generate-required-images`). Humans approve.

Reuse TFC's own textures (`assets/tfc/textures/**` in the pinned TFC checkout) whenever one fits. Request an image here only when nothing suitable exists.

## Status values
`requested` → `in-progress` → `generated` → `approved` (or `rejected` with a reason, which goes back to `requested` after the spec is fixed)

## Global style guide (applies to every request)
- **Minecraft pixel art** that matches TFC's look: flat shading, 1-pixel outlines only where TFC uses them, limited palette, **no anti-aliasing, no gradients/blur, no text** unless the request says so.
- **Exact pixel size** as specified. Texture art is drawn on a 16×16 grid (or a multiple, as given). Draw at the target resolution, or generate larger and downscale with nearest-neighbour through `tools/images/postprocess.py`.
- **Transparency:** PNG RGBA. Background fully transparent unless stated. Alpha is only 0 or 255 unless the request allows translucency.
- **Palette reference:** sample colours from the reference files listed in the request.
- Output goes to the exact `Target path`. Raw generations are kept under `art/raw/<id>/` for traceability.

## Entry template
```markdown
### <id>
- Status: requested
- Target path: content/art/<...>.png
- Size: <W>x<H> px (<grid notes>)
- Used by: <CraftEngine image / BetterHud element / item model / ...>
- Description: <what it depicts, composition, states>
- References: <TFC texture paths or existing project art to match>
- Constraints: <palette, transparency, tiling, frames for animation, safe areas>
- Requested by: <skill/feature, date>
- Notes: <filled in by the generator / reviewer>
```

---

## Requests

### ui-inventory-tab-button
- Status: requested
- Target path: content/art/gui/inventory_tab_button.png
- Size: 24x24 px
- Used by: resource-pack sprite drawn just outside the player inventory window. Clicking there opens the TFC player screen (calendar/nutrition/climate).
- Description: a small square button with a stone/wood frame, matching TFC GUI style, with a tiny calendar/leaf glyph in the centre suggesting "TFC info". Provide two variants side by side in one 48x24 sheet: normal (left) and hovered/highlighted (right).
- References: TFC `assets/tfc/textures/gui/player_calendar.png`, `player_nutrition.png`, `player_climate.png` (tab icons/frames); vanilla inventory frame colours.
- Constraints: opaque frame, transparent outside the frame, no text. Exact placement/size may change after the inventory-button spike (ARCHITECTURE.md §14 item 6).
- Requested by: tfc-feature-porting / player tabs decision, 2026-10-16
- Notes:

### pack-icon
- Status: requested
- Target path: content/art/pack/pack.png
- Size: 128x128 px
- Used by: resource pack `pack.png` (CraftEngine generated pack)
- Description: square icon showing a layered rock cross-section (strata) with a small copper ingot and a sapling on top, evoking geology + primitive metallurgy. Must not use the TerraFirmaCraft logo or name.
- References: TFC rock textures `assets/tfc/textures/block/rock/raw/*.png`; `assets/tfc/textures/item/metal/ingot/copper.png`
- Constraints: pixel-art style upscaled cleanly (draw at 32x32, upscale 4x nearest-neighbour), no text, opaque background allowed.
- Requested by: dependency-bootstrap / resource pack, 2026-10-16
- Notes:
