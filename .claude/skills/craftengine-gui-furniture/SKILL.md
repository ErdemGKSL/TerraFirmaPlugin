---
name: craftengine-gui-furniture
description: Visible dynamic content and textured screens with CraftEngine. Block entity_renderer elements (constant vs dynamic, morphing on state change, tint_source component-copy trick, our own element types, no interpolation, packet costs, Netty-thread show/hide), animating parts (quern handstone), furniture vs blocks, CE images/fonts/<image>/<shift> tags, how CE rewrites any plugin's container titles, exact pixel-alignment math for textured chest/dispenser/hopper GUIs, drawing art outside the window, and the player-inventory "outside button" technique. Use this whenever building a TFC device's visible contents (anvil items, ingot piles, tool racks, barrel fluid, firepit), any textured GUI (knapping, anvil forging, barrel, firepit, player tabs), GUI buttons/progress bars, furniture, or debugging misaligned GUI art, even if the user just says "make the anvil screen".
---

# Renderers, furniture and GUIs with CraftEngine

Source: `references/research-report.md` (CE @ `b3079c5`, read from source; vanilla screen constants are marked unverified there). §-numbers refer to it.

## Block renderers (§1)
- An `entity_renderer` is a set of packet-only display entities attached to an **appearance**, so each block state has a fixed element list. Element types: `item_display`, `text_display`, `block_display`, `item`, `armor_stand`, plus `better_model` and `model_engine`. Use `translation`/`rotation` rather than `position`/`yaw` (no teleport packets). Write `rotation` as the string `"x,y,z"`; a single number means −yaw. The `offset` random key is not in our CE version.
- **State change morphs elements.** One element → one element of the same type keeps its entity id and sends one metadata packet per viewer. So changing a property that only swaps `item`/`scale`/`translation`/`rotation` is cheap.
- **No interpolation**: transforms snap.
- `show`/`hide` run on **Netty threads**. Pre-build packets on the region thread. Never touch the world there.
- **Community edition has no culling**: every renderer in every sent chunk spawns for every player (~2 packets per display element). At view distance 10, 20 renderer blocks per chunk is ≈17.6k packets on login. Use renderers only for blocks whose contents vary. Static looks → real block models.
- Animated textures (`.mcmeta`) work in `item_display` at zero packet cost. Use them for flames and molten metal glow.

### Driving contents from our Java (pick the lowest level that works)
| Level | How | Good for |
|---|---|---|
| A: properties | Block properties (`count`, `fill`, `lit`, `logs`) → one appearance per combination | ≤ ~50 states per block. Firepit logs/lit, simple fill levels |
| B: `tint_source` | Our `BlockEntityController` implements `TintSource` and returns a synthetic item. The element copies the listed components (`custom_model_data`, `dyed_color`, probably `item_model`) onto its item. Call `blockEntity.updateConstantRenderers()` after changes | Ingot/sheet piles (CMD floats = count, strings = metal → `range_dispatch`/`select` item model), barrel fluid (colour + level), crucible contents |
| C: our own elements | Dynamic: controller `hasElement()`/`gatherElements()`. Or register a YAML element type with `BlockEntityElementConfigs.register(Key, factory)` in `onLoad` | Anvil inputs, tool racks, placed items (N item_displays), interpolated rotation |

Level C needs NMS packet objects (CE's internal proxies or our own NMS module), so it's version-sensitive; isolate it. Constant custom elements are **recreated on every state change**, so keep long-lived state in the controller. Controllers must drop their contents on removal (built-ins do).

### Quern handstone and other animations (§1.8)
Best option: a `grinding` property. The `grinding=true` appearance uses a `better_model` element whose bbmodel `idle` loops the spin, and `grinding=false` uses a static `item_display`. Option 2: our own element sending `transformation_interpolation_duration` with ≤ 90° steps.

## Furniture (§2)
Furniture is a real persisted `ItemDisplay` entity + packet elements + hitboxes (interaction/shulker/happy_ghast/custom; seats as `"x,y,z yaw force_player_rotation"`). **TFC devices and placed objects are blocks with block entities, not furniture**: blocks keep grid alignment, worldgen placement, per-block data, light and collision, and add no entity count. Use furniture only for off-grid decoration, seats, or large decorative models. It must be spawned/removed on the owning region thread.

## Textured GUIs (§3–§4)
- Images: `images: { tfp:gui/anvil: {file: tfp:font/gui/anvil.png, height: 222, ascent: 13, font: tfp:gui} }`. The `font` defaults to `<pack ns>:default`. `ascent ≤ height`, so to place art higher, pad the PNG bottom. Grid images (`grid_size`) give frames for progress bars.
- **Titles of any plugin are rewritten by CE** (`network.intercept-packets.container`, on by default): literal `<image:ns:id[:row:col]>`, `<shift:N>`, `<l10n:..>` inside the title text become glyphs.
  ```java
  Component title = Component.text()
      .append(Component.text("<shift:-8><image:tfp:gui/anvil><shift:-177>", NamedTextColor.WHITE)) // white, or the art is darkened
      .append(Component.text("Anvil", TextColor.color(0x404040)))
      .build();
  ```
  Only `image`/`shift`/`l10n`/`global`/`papi` tags are rewritten. `<white>` written as text stays literal, so colour with Adventure. Alternative that doesn't need interception: build glyph components from `fontManager().bitmapImageById(...)` codepoints, cached per `CraftEngineReloadEvent`. CE's own `Component` is relocated Adventure, so never mix it with Paper's.
- **Alignment math** (GUI px relative to the window's top-left; verified against CE's own GUI asset):
  - glyph top = `13 − ascent` (titleLabelY 6 + 7). To put PNG (0,0) at window (gx, gy): `ascent = 13 − gy`, leading shift = `gx − titleLabelX`.
  - The pen advances `width + 1` after a 1:1 image. Use a trailing `<shift:-(W+1)>` to return.
  - Chest 9×N: window 176×(114+18N), titleLabelX 8, container slot interiors at `(8+18c, 18+18r)`. Full 6-row background: `ascent 13, height 222, shift -8`.
  - Dispenser/furnace titles are **centred**: balance the total advance to 0. Hopper: titleLabelX 8, slots at `(44+18i, 20)`.
  - These vanilla constants are from client knowledge. Verify in-game on 1.21.11 and 26.x before mass-producing art.
- Buttons are items in slots (an empty/transparent model + tooltip). Overlays (progress, forging markers) are extra glyphs. Change them with `InventoryView#setTitle` (one `OpenScreen` packet; no more than a few per second).
- Knapping 5×5 fits a 6-row chest (columns 2–6, rows 0–4). Anvil and other screens likewise. Request the art via `image-requests` with exact slot coordinates.

## The "outside the inventory" button (§4.5–4.6)
- A click outside any window arrives as slot **−999** (`SlotType.OUTSIDE`). The server **can't tell where** outside the click was, so "anywhere outside opens TFC screens" (accepted in the design).
- The player inventory (E) screen gets no server-side title. The resource-pack way is CE `lang: { all: { container.crafting: "<white><shift:..><image:tfp:inv_tab_button>" } }`. CE converts it to a legacy `§` string, **so the glyph must use the `minecraft:default` font**. Caveats:
  - it also changes the crafting-table title;
  - it isn't shown in creative;
  - the art moves when the recipe book opens (+77 px);
  - player-typed default-font codepoints (keep CE's illegal-character filter on).
- Unverified, so test first (ARCHITECTURE spike #6): whether an empty-cursor click outside sends a packet on all supported clients, and whether `§f` whitens the glyph. Alternative trigger: Paper `PlayerRecipeBookSettingsChangeEvent` with a re-skinned recipe-book button sprite.

## Pitfalls (§5)
Elements are fixed per state; custom elements are recreated on state change; Netty-thread show/hide; no interpolation; conditions are evaluated only at show/morph; `block_display` position defaults to the block corner; no culling in Community; the image font default isn't `minecraft:default`; the title colour tints glyphs; centred titles on dispenser/furnace; relocated CE Adventure; codepoints are stable only while CE's `cache/font` survives.
