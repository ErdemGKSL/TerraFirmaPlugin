# M0-GUI-01: Outside-inventory trigger and textured 6-row GUI
**Status:** done for the trigger (both versions), the 6-row alignment (GUI scales 1-4, both versions) and the caveats. Placeholder art only. 2026-10-16.

## Setup
- Plugin `spikes/gui-01` (`Gui01Plugin`, throwaway). Logs `[gui01]` lines: PacketEvents `CLICK_WINDOW` (every click packet), Bukkit `InventoryClickEvent` for raw slot -999, `PlayerRecipeBookSettingsChangeEvent`. Trigger: empty-cursor outside click while the player's own view (`InventoryType.CRAFTING`) is open opens a chest-type GUI (`createInventory(null, 54, title)`, title `<shift:-8><image:tfpgui:gui/chest6>`). `/gui01 chest [items]`, `/gui01 crafting` for tests.
- Pack `spikes/gui-01/build_pack.py` (namespace `tfpgui`, Pillow art):
  - `gui/chest6`: 176x222 opaque 6-row background (slot frames at (8+18c, 18+18r), player inventory and hotbar frames, orientation pixels at (1,1) red and (174,220) green). Font `tfpgui:gui`, height 222, ascent 13.
  - `gui/tab_button`: 24x24 placeholder tab with a gem icon. Font `minecraft:default` (needed for lang), height 24, ascent 9.
  - `lang: {all: {container.crafting: "<white><shift:-123><image:tfpgui:gui/tab_button>"}}`.
  - The art is a placeholder. The `image-generation` skill would make the final one.
- Servers: Paper 1.21.11 (`c5eb079`) and Paper 26.3 (`7ec11b2`), port 26200 (RCON 26210), Java 25, CE 26.9.2, PacketEvents 2.14.0, BetterHud/BetterModel loaded (unused). Both: CE `Found N issue` none, `tfpgui` pack loaded and uploaded.
- Client: `spikes/gui-01/launch_client.py` (wraps the shared `client/mcclient.py` with a 1920x1080 window on display :103). `mcclient.py` hard-codes 1280x720, which is too short for a 222 px GUI at scale 4 (888 px). Scales 1-4 with `guiScale` option.
- Scripts: `spikes/gui-01/run.sh` (prepare, client, chest, stop), `spikes/gui-01/check_align.py` (pixel check).

## Results
### Trigger (empty cursor, left click outside the inventory window)
| Check | 1.21.11 | 26.3 |
|---|---|---|
| Client sends `CLICK_WINDOW` with slot -999, button 0, carried empty | yes (PacketEvents: type THROW) | yes |
| Bukkit `InventoryClickEvent` rawSlot -999, slotType OUTSIDE, view CRAFTING, click LEFT | yes | yes |
| Trigger opens the 6-row GUI | yes (screenshot `1.21.11-gui2-after-outside-click.png`) | yes (`26.3-gui2-after-outside-click.png`) |
| Creative inventory outside click sends a click packet | no (1 click, screen stayed open) | no (count 1 before, 1 after) |
| Recipe-book toggle event (`PlayerRecipeBookSettingsChangeEvent`) | fires: type CRAFTING, open=true | fires: same |

The recipe-book event fires only for the in-window recipe-book button. It cannot see clicks outside the window, so it is not a trigger for outside clicks. Its only use is a re-skinned in-window button (not tested).

### 6-row chest alignment (empty GUI, mouse outside, pixel check)
The check samples every opaque art pixel at the centre of its scale x scale block. Expected origin: `leftPos = (W/s - 176)//2`, `topPos = (H/s - 222)//2`. The vanilla "Inventory" label is excluded. Tolerance 6 per channel. "1-px shift" = one GUI pixel in each direction.

| Version | Scale | Window origin (px) | Match | Max channel error | 1-GUI-px shift match |
|---|---|---|---|---|---|
| 1.21.11 | 1 | (872,429) | 100% | 3 | 84-85% |
| 1.21.11 | 2 | (784,318) | 100% | 3 | 84-85% |
| 1.21.11 | 3 | (696,207) | 100% | 3 | 84-85% |
| 1.21.11 | 4 | (608,96) | 100% | 3 | 84-85% |
| 26.3 | 1 | (872,429) | 100% | 0 | 84-85% |
| 26.3 | 2 | (784,318) | 100% | 0 | 84-85% |
| 26.3 | 3 | (696,207) | 100% | 0 | 84-85% |
| 26.3 | 4 | (608,96) | 100% | 0 | 84-85% |

The 1-px-shift column is the worst of the four one-GUI-pixel shifts from the checker output (about 32.3k-32.6k of 38,344 pixels match). A 1 GUI px shift loses about 15% of pixels, so the zero-shift position is pinned. Items in all 54 slots at scale 4 (1.21.11, `1.21.11-gui4-chest-items.png`) sit inside their frames. The pixel check does not cover item rendering. Screenshots: `/tmp/claude-0/gui01-shots/`. Montages of scales 1-4: `montage-1.21.11-scales.png`, `montage-26.3-scales.png`.

### Caveats (measured)
- **Crafting table:** the `container.crafting` override replaces the "Crafting" title on the crafting table too (screenshot `1.21.11-gui4-crafting-table.png`, 26.3 `26.3-gui2-crafting-table-2.png`). The table's label starts at titleLabelX 29, not 97, so the glyph lands about 94 GUI px left of the window (on 1.21.11 at scale 4, screen x about 232). The tab position differs per screen, so each screen needs its own shift. On 26.3 the recipe-book panel covers that spot, so the 26.3 position is UNVERIFIED.
- **Creative inventory:** no trigger (no click packet), so creative players need another way in.
- **Recipe book open:** the recipe-book panel covers the left side of the window, and the tab with it (the shift of 77 GUI px is from the research report, not measured here; on 26.3 the panel hid the tab in the crafting screenshot).
- **GUI height:** a 222 px GUI needs a window at least 888 px tall at GUI scale 4. On 1280x720, scale 4 clips the bottom (arithmetic only, not run).
- The label "Crafting" in the inventory is gone, so the inventory has no title text.
- Right-button outside clicks, shift-clicks and multi-player use were not tested.

## Decision / recommendation
- **Trigger:** the empty-cursor outside click (`CLICK_WINDOW` slot -999 seen in Bukkit as `InventoryClickEvent`, rawSlot -999, `SlotType.OUTSIDE`, view CRAFTING). Works on 1.21.11 and 26.3. It opens our GUI from anywhere outside the window, since the server cannot see where the click was. Keep the empty-cursor check, so a player holding an item still drops it normally.
- **Do not use** `PlayerRecipeBookSettingsChangeEvent` as the trigger (in-window only).
- **Textured GUI:** the CE image approach with the `<shift:-8><image:...>` title and ascent 13 places a 176x222 background at the window origin, exactly, on both versions at scales 1-4. Vanilla labels draw over it.
- Production: guard the tab against the crafting table and recipe-book states (per-screen shift, or skip the tab there). Treat creative as a separate entry point.

## Open / UNVERIFIED
- Final tab art (image-generation skill). Placeholder art only.
- Crafting-table tab position on 26.3 (covered by the recipe-book panel in every screenshot).
- The skill's example pen math: after `<shift:-8>`, an image of advance 177 and `<shift:-177>`, the pen ends at x=0, not x=8. Our arithmetic says a label after the glyph needs `<shift:-169>`. Not tested; text after a glyph was not used in this spike.
- Right-button outside clicks, double clicks and clicks with an item on the cursor (throw) were not checked.
- Recipe-book button re-skin as an alternative trigger was not tested.
- PacketEvents' update check fails (no GitHub access from this sandbox). It is harmless. Not related to this spike.
