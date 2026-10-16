# CraftEngine deep dive: entity renderers, furniture, images/fonts, GUI texturing

> Note for this repo: researched at CraftEngine commit b3079c5 (v26.9.2), 2026-10-16. Examples say `tfc:`; our provisional namespace is `tfp:` (see `CLAUDE.md`).

Researched 2026-10-16 from primary sources:
- **Source**: CraftEngine `github.com/Xiao-MoMi/craft-engine`, commit `b3079c5` (2026-09-29, v26.9.2). Local clone: `scratchpad/src/ce`. All paths below are relative to the repo root. I only read the code; I did not build or run it.
- **Wiki**: `https://xiao-momi.github.io/craft-engine-wiki/<page>/`. Pages fetched: `configuration/block/states/entity_renderer` (last updated 2026-10-10), `configuration/furniture`, `configuration/furniture/variants`, `configuration/furniture/settings`, `configuration/item/behaviors/furniture_item`, `configuration/image`, `configuration/font`, `configuration/emoji`, `getting_start/first_image`, `api/block_entity`.
- This report builds on the general report `scratchpad/research/craftengine.md`; read that one first for blocks, the API overview and storage.

Tags used in this report:
- **[SRC]**: read in source.
- **[CFG]**: a config file shipped with CE.
- **[WIKI]**: the wiki.
- **[VAN]**: vanilla client behavior from my knowledge of Minecraft's source. It is not in the CE repo, so treat it as needing an in-game test.
- **[INF]**: an inference I made from the source.
- **UNVERIFIED**: I could not confirm it.

The wiki is **newer than the commit** (entity_renderer page 2026-10-10 vs. commit 2026-09-29). Where they differ, this report says so.

---

## Table of contents
0. [TL;DR for TFC](#0-tldr-for-tfc)
1. [entity_renderer (block entity renderers)](#1-entity_renderer-block-entity-renderers)
   - 1.1 Architecture: constant vs dynamic
   - 1.2 Element types and every option
   - 1.3 Rotation/vector parsing details
   - 1.4 Lifecycle: spawn, despawn, state change, updates
   - 1.5 tint_source (and the "component copy" trick)
   - 1.6 Built-in dynamic renderers (display_item_block, drawer_block, item_frame_block)
   - 1.7 Driving renderers from our Java: three levels
   - 1.8 Animation (rotating quern handstone)
   - 1.9 Conditions, view_range, culling (Premium), packet cost, threads
   - 1.10 TFC device recipes
2. [Furniture](#2-furniture)
3. [Images, fonts, shift, emoji, packet interception](#3-images-fonts-shift-emoji-packet-interception)
4. [GUI texturing recipe](#4-gui-texturing-recipe)
5. [Pitfalls](#5-pitfalls)
6. [UNVERIFIED / open questions](#6-unverified--open-questions)

---

## 0. TL;DR for TFC

- **Block renderers** are packet-only display entities attached to a block-state *appearance* (`entity_renderer:`). They are **constant**: each block state has a fixed list of elements. To show changing contents (anvil items, ingot count, fluid), use one of these:
  1. **Block-state properties + per-appearance renderers.** Changing the state morphs the elements: CE reuses the entity ID and sends only metadata.
  2. **`tint_source`.** An element copies **any item component** (`dyed_color`, `custom_model_data`, probably `item_model`) from an item that our block-entity controller supplies (`TintSource` interface). Then call `blockEntity.updateConstantRenderers()`.
  3. **Our own `BlockEntityController` with dynamic elements** (`hasElement()` + `gatherElements()`), like the built-in display-item, drawer and item-frame controllers.
  4. **Our own element type** registered with `BlockEntityElementConfigs.register(Key, factory)` and used in YAML as `type: tfc:xyz`.
- **There is no interpolation or animation support** in the built-in elements. Metadata updates snap. A rotating quern stone needs one of these:
  - our own element type that sends `transformation_interpolation_duration`;
  - the `better_model` element type, whose BetterModel `idle` animation loops. To spin only while grinding, switch the block state between a static and a spinning appearance.
- **Furniture** is an entity, not a block: a real persisted `ItemDisplay` base entity plus packet-only elements and hitboxes. It fits off-grid or rotatable decorations, seats, and large multi-block visuals. It does not fit TFC devices, which need the grid, worldgen, block data, hoppers, pistons and per-block storage. Use **blocks with block entities** for TFC devices.
- **Container titles**: CE rewrites **literal text** `<image:ns:id>`, `<image:ns:id:row:col>`, `<shift:N>`, `<l10n:…>`, `<global:…>` and `<papi:…>` inside **any** plugin's `OpenScreen` packet when `network.intercept-packets.container: true` (the default). It scans every string in the component tree, so Paper `Component.text("<shift:-11><image:tfc:knapping>").color(WHITE)` works. Other MiniMessage tags (`<white>`) are **not** parsed there. Set the colour with Adventure styling. The alternative is to build the glyph component yourself from the codepoints in `CraftEngineImages`.
- **Alignment math** (checked against CE's shipped `item_browser.png`):
  - Image left = `titleLabelX + shiftBefore`.
  - Image top (relative to the GUI top) = `titleLabelY + 7 - ascent`, which is `13 - ascent` for most screens (`titleLabelY = 6`).
  - The pen advances `width + 1` after a 1:1 image.
- **Drawing outside the window** works: the title glyph is not clipped. The **player inventory (E) screen** cannot get a server title. The only resource-pack-only way is to override the `container.crafting` translation via CE `lang: all:`, which CE converts to legacy `§` text. That means the glyph must use the `minecraft:default` font. See §4.6 for the caveats.

---

## 1. entity_renderer (block entity renderers)

### 1.1 Architecture: constant vs dynamic [SRC]

| | Constant renderer | Dynamic renderer |
|---|---|---|
| Defined by | YAML `entity_renderer` / `entity_render` on a state or appearance (`AbstractBlockManager` L477, L740 `parseBlockEntityRender`, L897–903) | A `BlockEntityController` whose `hasElement()` returns true, with elements collected by `gatherElements(Consumer)` (`core/.../block/entity/BlockEntity.java` L27–38) |
| Class | `core/.../block/entity/render/ConstantBlockEntityRenderer.java` | `core/.../block/entity/render/DynamicBlockEntityRenderer.java` |
| Element interface | `ConstantBlockEntityElement` (adds `condition()`, `hasCondition()`, `showInternal`, `supportsTransform`) | plain `BlockEntityElement` (`show/hide/update/activate/deactivate/initialForceVisible`, all default no-ops, `@ApiStatus.Experimental`) |
| Stored | Per chunk: `CEChunk.constantBlockEntityRenderers` (`Map<BlockPos,…>`); positions serialized with the chunk (`CEChunk` L97–103) | Per chunk: `CEChunk.dynamicBlockEntityRenderers` (`CEChunk` L47, `createDynamicBlockEntityRenderer` L604–654) |
| Element count | One element per configured entry, fixed per state | **Fixed when the BlockEntity is constructed** (`gatherElements` is called once in the `BlockEntity` constructor). An element may still spawn 0..N entities internally. |
| Recreated on | Block state change (§1.4) | BlockEntity creation (place/load). A state change calls `controller.preBlockStateChange(newState)` and does not rebuild the renderer. |

`BlockEntityRenderer.java` (core) loops over its elements: `show/hide/update/cull/restore/activate/deactivate`. Culling (Premium) either sets view range to 0 on `ViewRangeCullable` elements or hides them (L31–63).

### 1.2 Element types and every option [SRC + WIKI]

Registered in `bukkit/.../block/entity/renderer/constant/BukkitBlockEntityElementConfigs.java` L8–12:
- `item_display`
- `text_display`
- `block_display`
- `item`
- `armor_stand`

The compatibility module adds `better_model` and `model_engine` when those plugins are present (`bukkit/compatibility/.../BukkitCompatibilityManager.java` L214, L221).

**Type inference**: when `type` is absent, `text` means text_display, `item` means item_display, and `block` means block_display (`BlockEntityElementConfigs.getOrGuessType`). The type key is passed through `Key.ce(...)`, so a bare name gets the `craftengine:` namespace. Custom types must be written `tfc:foo`.

#### `item_display` (`ItemDisplayBlockEntityElementConfig.java` factory L221–256)
| Key | Default | Notes |
|---|---|---|
| `item` | **required** | CE or vanilla item id. A missing item renders a barrier (L120–123). The item is built **per player** (`Item.byId(itemId, player)`). |
| `scale` | `1,1,1` | number or `x,y,z` |
| `position` | `0.5,0.5,0.5` | entity spawn position relative to the block origin. A change causes a position-sync packet. |
| `translation` | `0,0,0` | display transformation (metadata) |
| `pitch` / `yaw` | 0 | entity rotation (spawn packet). A change causes a position-sync packet. |
| `rotation` | identity | left_rotation quaternion. See §1.3. |
| `display_transform` \| `display_context` | `none` | none, third_person_left_hand, third_person_right_hand, first_person_left_hand, first_person_right_hand, head, gui, ground, fixed |
| `billboard` | `fixed` | fixed / vertical / horizontal / center |
| `shadow_radius` | 0 | |
| `shadow_strength` | 1 | |
| `glow_color` | none | colour. When set, the glowing flag 0x40 is set. |
| `brightness: {block_light, sky_light}` | -1 (none) | Both values are needed for the override to apply (L135). |
| `view_range` | 1.0 | multiplied by the per-player `displayEntityViewDistance` (PDC scale, default 1). Sent as metadata. |
| `tint_source` | none | §1.5 |
| `condition` \| `conditions` | none | list of CE conditions with **AND** logic, evaluated per player at show time |

There are **no** keys for `interpolation_duration`, `teleport_duration`, `width/height` or `item_model` override.

#### `text_display` (`TextDisplayBlockEntityElementConfig.java` L211–255)
- Shares `scale`, `position`, `translation`, `pitch`, `yaw`, `rotation`, `billboard`, `shadow_*`, `glow_color`, `brightness`, `view_range` and `condition(s)` with `item_display`.
- `text` (**required**). It is deserialized per player with `AdventureHelper.deserialize(text, NetworkTextReplaceContext.of(player))` (L167–168), so MiniMessage, `<image>`, `<shift>`, `<papi>` and `<l10n>` all work.
- `line_width` (200), `background_color` (ARGB, default `0x40000000`), `text_opacity` (-1), `has_shadow`, `is_see_through`, `use_default_background_color` (all default false), `alignment` (center/left/right).
- No `tint_source`.

#### `block_display` (`BlockDisplayBlockEntityElementConfig.java` L180–214)
- `block` (**required**): a vanilla block-state string, resolved lazily.
- The common display keys apply, **but `position` defaults to `0,0,0`** (the corner), not the centre.
- No `tint_source`.

#### `item` (dropped-item look, `ItemBlockEntityElementConfig.java` L110–124)
- Keys: `item` (required), `position` (default centre), `tint_source`, `condition(s)`.
- Implementation: an `item_display` vehicle with an `item` entity passenger (NoGravity). That is 2 entities and 4 packets per show (`ItemBlockEntityElement` L43–65).
- The item spins and bobs client-side. You cannot transform it.

#### `armor_stand` (`ArmorStandBlockEntityElementConfig.java` L163–183)
- Keys: `item` (required, worn as a **helmet**), `scale` (float, applied through the SCALE attribute on 1.20.5+), `position`, `pitch`, `yaw`, `small`, `glow_color`, `tint_source`, `condition(s)`.
- `glow_color` is a **legacy colour name**; it creates a team packet.
- Packets: spawn, metadata, equipment, plus attribute and team when needed (`ArmorStandBlockEntityElement` L78–90).

#### `better_model` (`bukkit/compatibility/.../model/bettermodel/BetterModelBlockEntityElementConfig.java`)
- Keys: `model` (required), `position` (centre), `yaw`, `pitch`, `sight_trace` (true), `condition(s)`.
- It creates one BetterModel **`DummyTracker`** per block. It calls `dummyTracker.spawn(player)` on show, `remove(player)` on hide, and `close()` on deactivate (`BetterModelBlockEntityElement.java`).
- BetterModel plays the `idle` animation in a loop from the tracker's first start (`research/bettermodel.md` L252).
- No tint options on block elements. The **furniture** variant has `tints:` (wiki variants page).

#### `model_engine`
- `model`, `position`, `yaw`, `pitch` [WIKI].

#### Random `offset` (wiki only)
The wiki (2026-10-10) documents `offset: {type: xz|xyz, horizontal, vertical}` for all 7 types. **It is not in commit b3079c5**: `grep '"horizontal"'` finds nothing. Treat it as a newer feature and check the installed CE version before using it. The general report §4.7 wrongly lists it as present.

#### Culling block (state-level, Premium)
`entity_culling: false | {aabb: x1,y1,z1,x2,y2,z2, view_distance, aabb_expansion (0.5), ray_tracing (true)}`.
- It is read from the **block's top-level section** (`AbstractBlockManager` L741 uses `section`, the block definition). The wiki places it next to `entity_renderer`.
- Default AABB is ±0.5 around the centre (L63). Default `view_distance` comes from `client-optimization.entity-culling.view-distance` (64).

### 1.3 Rotation/vector parsing details [SRC `core/.../plugin/config/ConfigValue.java` L302–343, L542–570]

`rotation` (`getAsQuaternion`) accepts:

| Input | Result |
|---|---|
| a number `N` | rotation about Y by **−N°**. The sign is flipped, which matches vanilla yaw. |
| a string `"x,y,z"` | Euler degrees, built as `toQuaternionf(rad z, rad y, rad x)` |
| a string `"a,b"` | 2-axis form |
| a string `"x,y,z,w"` | raw quaternion |
| a YAML list | accepted only with 4 elements (quaternion) or 1 element (Y). **A 3-element list is rejected.** Write Euler angles as a string. |

`getAsVector3f`: a number `n` gives `(n,n,n)`, `"x,y,z"` gives a vector, and a list of 3 or 1 elements also works. Underscores are stripped from strings.

### 1.4 Lifecycle: spawn, despawn, state change, updates [SRC]

**Spawn and despawn.**
- When a chunk packet is sent to a player, `LevelChunkWithLightListener` L243 calls `ceChunk.spawnBlockEntities(player)`. `ForgetLevelChunkListener` L29/37 despawns.
- Without culling, every constant and dynamic renderer's `show(player)` runs **on the Netty thread** (`CEChunk` L117–152). With culling (Premium), renderers go to the per-player tracked set and the culling thread shows or hides them.
- So `show`/`hide` of any element (ours included) **must be thread-safe, non-blocking, and must not touch world state**.

**item_display packets.**
- Show sends `AddEntity` and `SetEntityData`. The spawn packet is cached per element (`ItemDisplayBlockEntityElement` L64–66).
- Hide sends a cached `RemoveEntities`.
- `update(player)` re-sends the full metadata with `force=true`, and a position-sync packet if position, yaw or pitch changed (L69–75).

**State change (morphing)** [`CEChunk.addConstantBlockEntityRenderer` L209–420]. When a block changes from state A to state B, CE builds B's elements and tries to *transform* A's elements:
- **1 → 1 element** of the same class with `supportsTransform()` (true for item_display, text_display, block_display; check each class): the config's `create(chunk,pos,previous)` reuses the **same entity ID**. Each tracked player then gets `update()`: a metadata packet, plus a position packet if the position changed. Nothing is despawned or respawned. Conditions are re-evaluated (`updateBlockEntityVisibility` L183–197).
- **Many → many**: first `createExact` (same position, translation and rotation), then `create(…previous)`. Elements that cannot be transformed are spawned new; unmatched old ones are hidden.
- So **changing a block property that only changes `item`, `scale`, `translation` or `rotation` costs one metadata packet per viewer per element**.
- The rotation snaps, because no interpolation duration is ever set (`grep Interpolation` finds only `DisplayData` definitions, the projectile handler and `DamageHologram`).

**Refresh without a state change.**
- `blockEntity.updateConstantRenderers()` (`BlockEntity.java` L134–144) calls `renderer.update(player)` for every player tracking the chunk.
- It re-runs the metadata provider, which rebuilds the item **and re-applies the tint source**. This is how the tint_source behaviours push changes (`TintSourceBlockEntityController.setSourceItem`, `DisplayItemBlockEntityController.Tintable`).

### 1.5 `tint_source` and the "component copy" trick [SRC `core/.../block/entity/render/tint/*`]

```yaml
tint_source: minecraft:dyed_color          # shorthand: a list or string of component ids, index 0
tint_source: {type: default, components: [minecraft:dyed_color, minecraft:custom_model_data], index: 0}
```

How it works:
- At element creation, `DefaultBlockEntityTintSourceConfig.create` looks up `blockEntity.controller.get(TintSource.class, index)`. That is the *index-th* controller implementing `core/.../world/TintSource` (`Item tintSource()`).
- At metadata time, `DefaultBlockEntityTintSource.applyTint` copies each listed component **verbatim** from the source item onto the rendered item (`getComponentAsSparrowTag` → `setSparrowTagComponent`).
- On pre-1.20.5 servers only dyed_color, firework_explosion, potion_contents and map_color are mapped to legacy NBT.
- The wiki says `custom_model_data` is a common choice. Copying `minecraft:item_model` should work the same way (**UNVERIFIED**).

Built-in providers:
- `tint_source_block` (`TintSourceBlockEntityController`): stores the item used to place the block. Option `drop_item`, `data_key`.
- `display_item_block` with `tint_source: true` (the `Tintable` controller, which exposes the displayed item).

Custom types: `BlockEntityTintSources.register(Key, BlockEntityTintSourceConfigFactory)`.

**TFC trick.** Our controller implements `TintSource` and returns a synthetic item. For example, `custom_model_data` can carry `floats: [count]` and `strings: [metal]`. One `item_display` element uses an item whose 1.21.4+ item model definition does `range_dispatch` on the float and `select` on the string. The visible model then changes on `updateConstantRenderers()`, with no extra block states and one metadata packet per viewer [INF].

### 1.6 Built-in dynamic renderers [SRC `bukkit/.../block/entity/*Controller.java`, `renderer/dynamic/*`]

| Behaviour (block) | Controller | Element | Entities | Config keys |
|---|---|---|---|---|
| `display_item_block` | `DisplayItemBlockEntityController` (+`.Tintable`) | `DynamicDisplayItemBlockEntityElement` | item_display vehicle + item passenger (spinning dropped item) | `position` (centre, rotated by `facing`), `sounds{put,take}`, `has_signal` (true, comparator 15 when filled), `tint_source` (false), `data_key` (`craftengine:display_item`) — `DisplayItemBlockBehavior.java` L145–175 |
| `drawer_block` | `DrawerBlockEntityController` (834 lines) | `DynamicDrawerBlockEntityElement` | item_display (`FIXED` transform) + text_display (count) | `item_position`, `text_position`, `item_scale`, `text_scale`, `max_stacks`, `allow_input`, `allow_output`, `compatible_mode`, `has_signal`, `data_key`, `sounds` — `DrawerBlockBehavior.java` L275–295; CFG `blocks/drawer_block.yml` |
| `item_frame_block` | `ItemFrameBlockEntityController` | `DynamicItemFrameBlockEntityElement` | item-frame packet entity | `render_map_item`, `glow`, `invisible`, `data_key`, `sounds` — `ItemFrameBlockBehavior.java` L196–214 |

The pattern every dynamic controller follows (`DisplayItemBlockEntityController.putDisplayItem` L62–74):

```java
this.displayItem = inputItem;
this.element.refreshChangeDisplayItemPacket(item.minecraftItem());     // rebuild cached packets
CEChunk chunk = blockEntity.world.getChunkAtIfLoaded(pos.x >> 4, pos.z >> 4);
if (chunk != null) for (Player p : chunk.getTrackedBy())
    if (p.isDynamicBlockEntityVisible(blockEntity.pos)) element.showDisplayItem(p); // re-send
blockEntity.world.blockEntityChanged(blockEntity.pos);                   // mark dirty for saving
```

`Player.isDynamicBlockEntityVisible(pos)` is always true without culling. With culling, it checks the culling holder (`core/.../entity/player/Player.java` L306–312).

### 1.7 Driving renderers from our Java: three levels

**Level A: config only.** Use block properties (`count`, `fill`, `lit`, `spinning`), one appearance per combination, and `entity_renderer` per appearance. Change the property with `CraftEngineBlocks.place(...)` or `state.with(prop, v)` on the region thread, and CE morphs the elements (§1.4). Use `config_factory` or templates to generate the combinations. This is cheap and good for ≤ ~50 states per block.

**Level B: controller + `TintSource` + constant renderer.** A custom block behaviour implements `EntityBlock`. Its controller implements `TintSource` and holds TFC data (NBT through `saveCustomData`/`loadCustomData`). YAML elements use `tint_source: {components: [minecraft:custom_model_data]}`. After changing the data, call `blockEntity.updateConstantRenderers()` and `world.blockEntityChanged(pos)`.

**Level C: our own elements.**
- *Dynamic*: the controller returns `hasElement() = true` and supplies elements in `gatherElements`. These are plain `BlockEntityElement`s that we write; we send packets ourselves.
- *Config-defined custom element type*: register a factory so YAML can say `type: tfc:anvil_contents`.

The interfaces to implement, verbatim:

```java
// core/.../block/entity/render/element/BlockEntityElementConfigs.java L16–21
public static <E extends ConstantBlockEntityElement> BlockEntityElementConfigType<E> register(Key key, BlockEntityElementConfigFactory<E> factory)

// BlockEntityElementConfigFactory.java
@FunctionalInterface interface BlockEntityElementConfigFactory<E extends ConstantBlockEntityElement> {
    BlockEntityElementConfig<E> create(ConfigSection section);
}
// BlockEntityElementConfig.java
interface BlockEntityElementConfig<E extends ConstantBlockEntityElement> {
    E create(CEChunk chunk, BlockPos pos);
    default E create(CEChunk chunk, BlockPos pos, E previous) { return null; }      // morph (same entity id) — optional
    default E createExact(CEChunk chunk, BlockPos pos, E previous) { return null; } // morph when identical transform — optional
    Class<E> elementClass();
}
// ConstantBlockEntityElement.java (extends BlockEntityElement)
//   condition()/hasCondition() — or extend AbstractConstantBlockEntityElement(predicate, hasCondition)
//   void showInternal(Player player);     // called by show() after condition check
//   default boolean supportsTransform()   // return true only if create(…,previous) is implemented
// BlockEntityElement.java (@ApiStatus.Experimental): initialForceVisible, show, hide, update, deactivate, activate
```

Skeleton (TFC side):

```java
// onLoad(), CE loaded first (paper-plugin.yml dependency), before CE parses configs:
BlockEntityElementConfigs.register(Key.of("tfc", "quern_stone"), QuernStoneConfig::new);

final class QuernStoneConfig implements BlockEntityElementConfig<QuernStoneElement> {
    final Key item; final Vector3f pos;
    QuernStoneConfig(ConfigSection s) {
        item = s.getNonNullIdentifier("item");
        pos = s.getVector3f("position", ConfigConstants.CENTER_VECTOR3);
    }
    public QuernStoneElement create(CEChunk chunk, BlockPos p) {
        // Bind to our controller, as DefaultBlockEntityTintSourceConfig does:
        BlockEntity be = chunk.getBlockEntity(p, false);
        QuernController c = be == null ? null : be.controller.get(QuernController.class, 0);
        return new QuernStoneElement(this, p, c);
    }
    public Class<QuernStoneElement> elementClass() { return QuernStoneElement.class; }
}
final class QuernStoneElement extends AbstractConstantBlockEntityElement {
    final int entityId = Bukkit.getUnsafe().nextEntityId(); // CE uses the vanilla counter too (EntityUtils.ENTITY_COUNTER L57)
    QuernStoneElement(...) { super(ctx -> true, false); ... }
    @Override public void showInternal(Player p) { p.sendPackets(List.of(addEntity, metadata), false); } // NMS packet objects
    @Override public void hide(Player p) { p.sendPacket(removeEntity, false); }
    @Override public void update(Player p) { p.sendPacket(metadataWithInterpolation(), false); }
}
```

Notes for Level C:
- `Player` here is CE's `core.entity.player.Player`. `sendPacket(Object nmsPacket, boolean immediately)` and `sendPackets(List, boolean)` take **NMS packet objects**.
- To build them across MC 1.21.x and 26.x you need one of:
  - CE's version-agnostic proxies (`craft-engine-bukkit-proxy`: `ClientboundAddEntityPacketProxy`, `ClientboundSetEntityDataPacketProxy`, … plus `bukkit/.../entity/data/DisplayData` with `TransformationInterpolationDuration`, `TransformationInterpolationDelay`, `LeftRotation`). These are internal and unstable, as the general report §8.1 warns.
  - Our own NMS layer.
- Remember that `show`/`hide` run on the Netty or culling thread.
- **Registration timing**: YAML parsing happens after CE's onLoad. Register in our `onLoad`. Registries are not frozen (general report §13.5).
- A constant element of a custom type is **recreated on every block-state change** of that block. Keep long-lived animation state in the **controller**, not the element.

### 1.8 Animation: rotating quern handstone

Options, best first:
1. **`better_model` element with an `idle` animation (config only).**
   - The quern has a `grinding` boolean property.
   - `grinding=false` appearance: `entity_renderer: {item: tfc:quern_handstone, ...}`.
   - `grinding=true` appearance: `entity_renderer: {type: better_model, model: tfc_quern_spin}`, where the bbmodel's `idle` animation spins the stone.
   - On a state change the classes differ, so CE hides the old element and shows the new one (§1.4).
   - BetterModel streams bone updates itself (dirty bones only, see bettermodel.md L456).
   - **UNVERIFIED**: whether a dummy tracker's `idle` runs per viewer from the moment it spawns; test it.
2. **Our own element type with interpolation** (Level C). On `update`, send a metadata packet with `transformation_interpolation_delay = 0`, `transformation_interpolation_duration = N` and a new `left_rotation` advanced by ≤ 90° per step. Display entities interpolate the shortest arc [VAN], so 180° steps are ambiguous.
   - A ticker on the controller (`createBlockEntityTicker`, region thread) or a scheduler every N ticks calls `element.update(p)` for each tracked player.
   - Cost: 1 small metadata packet per viewer every N ticks (for example N=10, 90° per 10 ticks gives 1.5 rpm per 0.5 s step… tune it).
3. **Block-state stepping** (`rotation=0..7` property + 8 appearances). The angle snaps every step with no interpolation. This is choppy; avoid it.

Animated textures (`.mcmeta` on the item model's texture) work in item_display with zero packets [VAN]. Use them for firepit flames and the glow of molten metal.

### 1.9 Conditions, view_range, culling, packet costs, threads

- **Conditions** (`condition(s)`): any CE condition (`permission`, `world`, `expression`, …) with AND logic. They are evaluated at `show()` with `PlayerOptionalContext.of(player)` (`ConstantBlockEntityElement.show`), **not continuously**. They are re-checked on a block-state morph (`CEChunk.updateBlockEntityVisibility`) and, with culling, on restore (`BlockEntityRenderer.restore` L52–58). Per-player visibility is therefore static per chunk send.
- **view_range**: the vanilla display-entity view-range multiplier times the per-player scale (admin command `set_entity_culling_distance_scale`, stored in player PDC — `BukkitServerPlayer` L299). It does not reduce packets, only client rendering.
- **Culling** is Premium only: `Config` L851 `VersionHelper.PREMIUM && client-optimization.entity-culling.enable`. With it, block entities beyond `view-distance` (64) or occluded (ray tracing on 1 thread) are culled, display types by setting view_range 0, and spawns are rate-limited (`bucket-size 1000`). The **Community edition shows every renderer of every chunk sent to the player.**
- **Packets per element on chunk send**:

  | Element | Packets |
  |---|---|
  | item_display / text_display / block_display | 2 |
  | item | 4 |
  | armor_stand | 3–5 |
  | display_item_block | 4 |
  | drawer | 4 |

  At view distance 10 (441 chunks), 20 renderer blocks per chunk is about 17.6k packets on login. Budget accordingly and prefer real block models when contents never change.
- **Threads**: show/hide run on Netty or culling threads. Controller mutations happen on the region thread. `CEChunk.renderLock` (`ReentrantReadWriteLock`) guards the maps.

### 1.10 TFC device recipes (suggested, [INF])

| Device | Recommended approach |
|---|---|
| Anvil with items on top | Vanilla anvil visual state or a CE model as the block. Our `tfc:anvil` behaviour plus controller holding input/hammer/flux items. **Level C dynamic elements**: 2–3 item_displays with `FIXED` transform lying flat (rotation `-90,0,0`), rebuilt on change like `DynamicDisplayItemBlockEntityElement`. |
| Ingot / sheet pile (grows) | Controller + `TintSource` (count, metal in `custom_model_data`) + one item_display whose item-model definition selects the geometry. Collision via a `layers` property mapped to e.g. snow-layer or slab visual states, if needed. |
| Tool rack | 4-slot controller, dynamic elements (4 item_displays, `display_transform: fixed` or `third_person_right_hand`). Facing comes from the state and is applied in `preBlockStateChange`, like `DisplayItemBlockEntityController.calculateDisplayItemPosition`. |
| Placed items (4 items on ground) | Same as the tool rack. `transparent: true` appearance with a shared `auto_state` id, or a carpet-like visual state for a thin hitbox. |
| Barrel fluid | Constant element `item: tfc:render/fluid_plane` + `tint_source: {components:[minecraft:dyed_color, minecraft:custom_model_data]}`. The controller supplies a fluid colour plus a fill level (range_dispatch on the float translating the plane). Alternatively a `fill=0..N` property for the height and `dyed_color` for the colour. |
| Pot / crucible contents | Like the barrel; the crucible's molten metal uses an animated emissive texture with `brightness: {block_light: 15, sky_light: 15}`. |
| Quern handstone | §1.8 option 1 or 2. |
| Firepit logs/fire | Properties `logs=0..4`, `lit` → appearances with item_display models. The flame is an animated texture. CE's `simple_particle_block` behaviour handles smoke (CFG `amethyst_torch.yml`). |

---

## 2. Furniture

### 2.1 What it is [SRC `bukkit/.../entity/furniture/BukkitFurnitureManager.java` L92–118]

`place()` spawns a **real `ItemDisplay` entity** (the "meta/base entity"). Its PDC keys:
- `craftengine:furniture_id` (string)
- `craftengine:furniture_extra_data` (byte[] of a `FurniturePersistentData` NBT: `item`, `variant`, `data`)

The base entity is saved with the chunk's entities like any other entity. On disable it is *not* removed (L166–167). Everything else is **packet-only per viewer**:
- elements (`*FurnitureElement`)
- hitbox entities (interaction or shulker packets: `InteractionFurnitureHitbox` L40–45, `ShulkerFurnitureHitbox` L57–68)
- seats

Server-side physics colliders are created by the closed nms-helper (`BukkitCollider`, `FastNMS.createCollisionInteraction` / `createCollisionBoat`). Choose the type with `furniture.collision-entity-type: interaction|boat` (CFG config.yml L422–440), which needs a restart. `hide-base-entity: true` hides the base entity. The wiki says CE does not add these entities to the server ticker list, which makes them cheaper than vanilla displays.

### 2.2 Schema [SRC `core/.../entity/furniture/AbstractFurnitureManager.java` L211–296; WIKI furniture pages]

```yaml
furniture:                # or inline under an item's `behavior: {type: furniture_item, furniture: {...}}`
  tfc:thing:
    settings:             # FurnitureSettingsModifiers.java L18–43
      item: tfc:thing                 # item for creative pick (1.21.4+) and default drop
      hit_times: 3                    # 0 = instant break; counter resets after 2 s idle [WIKI]
      sounds: {break: …, place: …, hit: …}  # string or {id, volume, pitch}
      adventure_mode_breaking: false
      correct_tools: ["#minecraft:axes", minecraft:diamond_pickaxe]
    entity_culling: false | {aabb, view_distance, aabb_expansion: 0.25, ray_tracing: true}   # Premium; read at FURNITURE top level (L262) though the wiki shows it per variant
    variants:             # key regex variant(s)|placement; at least one
      ground:             # names are free; furniture_item maps clicked face → ground/wall/ceiling
        loot_spawn_offset: 0,0.5,0
        blueprint: some_model           # alias better_model|model_engine: ONE external model bound to the real base entity
        elements: [ ... ]               # §2.3
        hitboxes: [ ... ]               # §2.4; if omitted → defaultHitBox()
    behaviors: [ ... ]    # §2.5
    loot: { template: default:loot_table/furniture, arguments: {item: tfc:thing} }  # entry type furniture_item drops the placing item with its data
    events: [ {on: right_click, functions: [...], conditions: [...]} ]   # triggers incl. furniture_break; functions spawn_furniture / remove_furniture / replace_furniture / rotate_furniture / set_furniture_variant; condition match_furniture_variant
```

### 2.3 Elements [SRC `bukkit/.../entity/furniture/element/*Config.java`]

The registry is `BukkitFurnitureElementConfigs`:
- `item_display`, `text_display`, `block_display`, `item`, `armor_stand`
- `better_model` and `model_engine` (compatibility module)

Type inference works as for blocks. Options match the block versions, with these differences:
- **`position` defaults to `0,0,0`** (the furniture anchor). It is rotated with the furniture yaw.
- **`copy_data` | `tint_source(s)`** (furniture) copies listed components **from the furniture's source item** (`SourceItemComponentsDataSourceConfig`, default `[minecraft:dyed_color]`). `apply_dyed_color: true` is the legacy form (dyed_color + firework_explosion).
- `better_model` furniture element: `tints: {source: minecraft:dyed_color, bones: [...], children: true}`. A player-profile component on the source item is applied to the model automatically [WIKI].
- Wiki warning: wall and ceiling elements at position `0,0,0` can render black. Offset `position` and cancel it with `translation`.

### 2.4 Hitboxes (all options) [SRC `bukkit/.../entity/furniture/hitbox/*Config.java`; registry `BukkitFurnitureHitboxTypes`]

These keys are shared by every type: `position` (0,0,0), `seats` (list), `blocks_building` (true), `can_use_item_on` (true), `can_be_hit_by_projectile` (true). The default `type` is `interaction`.

| type | Extra keys | Physics |
|---|---|---|
| `interaction` | `width`/`height` (1/1) or `scale: "w,h"` or `scale: n`; `interactive` (true); `invisible` (false) | Walk-through. Click detection only. |
| `shulker` | `scale` (1), `peek` (0–100, byte), `direction` (default **UP**), `interactive` (true), `interaction_entity` (true: an extra interaction entity for click accuracy), `invisible` | Hard collision |
| `happy_ghast` | `scale` (1.0, 4×4 at 1), `hard_collision` (true) | Hard collision (1.21.6+ entity) |
| `custom` | `entity_type` (required, any vanilla id; dimensions taken from the type), `scale` (1) | Collision per that entity (code default as listed; wiki says slime) |

**Seats** (`core/.../entity/seat/SeatConfig.java`): `"x,y,z [yaw] [force_player_rotation]"` or `{position, yaw, limit_player_rotation, force_player_rotation}`.
- Giving a yaw limits body rotation.
- The **third token is `force_player_rotation`** (`true` = use yaw, a number = that angle). It is *not* "limitPlayerRotation" as the general report says.
- Seats at the same position across hitboxes are shared [WIKI].
- `/ce debug furniture` shows hitboxes [WIKI].

### 2.5 Placement and behaviours

**`furniture_item`** (`bukkit/.../item/behavior/FurnitureItemBehavior.java` L273–360) has these keys:
- `furniture` (id or inline section)
- `rules: {<variant>: {rotation, alignment}}`
  - `rotation`: any, four, eight, sixteen, north, east, west, south
  - `alignment`: any, center, half, quarter, corner, center_quarter (`RotationRule`, `AlignmentRule`)
- `ignore_placer`, `ignore_entities`
- `against_blocks`, `against_block_tags`, `blacklist` (true)

There is also `liquid_collision_furniture_item` for placing on water or lava. Only `ground`, `wall` and `ceiling` are chosen automatically. Other variant names need the API or the `set_furniture_variant` function [WIKI].

**Furniture behaviours** (`BukkitFurnitureBehaviors`):
- `simple_storage_furniture`: `title` (default `<lang:container.chest>`), `rows` (1), `sounds{open,close}`, `data_key`, `variants.<v>.hitboxes`.
- `display_item_furniture`: `sounds{put,take}`, `variants.<v>.item_position`, `variants.<v>.hitboxes`, `data_key`.
- `glowing_furniture`: `lights: ["x,y,z [level]" | {position, level}]`, `variants.<v>: [...]`. Requires `furniture.light-system.enable: true`.

Custom behaviours: `FurnitureBehaviors.register(Key, FurnitureBehaviorFactory)`, where the factory signature is `T create(FurnitureDefinition, ConfigSection)` and `T extends FurnitureBehaviorTemplate { FurnitureController createController(Furniture) }`.

`FurnitureController` (`core/.../entity/furniture/behavior/FurnitureController.java`) has these hooks:
- `useOnFurniture(hitBox, ctx)`, `useWithoutItem`, `onPlayerHit`
- `onPlace`, `preRemove`, `postRemove`, `onLoad`, `onUnload`
- `onVariantChange`
- `gatherElements`, `gatherHitboxes` (dynamic elements)
- `onAsyncPlayerTrack/Untrack/VariantChange`, `onPlayerTrack/Untrack`
- `createFurnitureTicker` / `createAsyncFurnitureTicker`
- `save/loadCustomData(CompoundTag)`, `loadCustomDataFromItem`, `getItemToPickup`

### 2.6 Java API [SRC `bukkit/.../api/CraftEngineFurniture.java`, `api/event/Furniture*Event.java`; core `Furniture.java`]

```java
CraftEngineFurniture.loadedFurniture(); byId(Key);
BukkitFurniture f = CraftEngineFurniture.place(Location, Key id[, String variant[, boolean playSound]]);
place(Location, FurnitureDefinition, CompoundTag data | FurniturePersistentData, boolean playSound);
rayTrace(Player[, maxDistance]) / rayTrace(Location, maxDistance);
isFurniture(Entity); isCollisionEntity(Entity); isSeat(Entity);
getLoadedFurnitureByMetaEntity(Entity) / BySeat / ByCollider;
remove(Entity|Furniture[, Player][, dropLoot, playSound]);
// Furniture instance:
f.setVariant(String) / setVariant(String, force); f.moveTo(WorldPosition) (CompletableFuture);
f.refresh(); f.refreshElements(); f.setSourceItem(Item) (+refreshElements() to recolor);
f.persistentData.addTag/getTag (custom NBT, thread-safe RW lock); f.currentVariant(); f.hitboxes(); f.elements();
f.metaDataEntity (core Entity), f.uuid(), f.position(), f.controller;
```

Events: `FurnitureAttemptPlaceEvent` (player, hand, clickedBlock, variant, location, furniture), `FurniturePlaceEvent`, `FurnitureInteractEvent` (hitBox, interactionPoint, hand), `FurnitureHitEvent` (hitBox, hitPoint), `FurnitureBreakEvent` (`setDropItems`). All are cancellable player events.

`place()` spawns a real entity, so on Folia call it on the region thread that owns the location [INF].

### 2.7 Furniture vs. block for TFC [INF]

Prefer **blocks** (plus block entities and renderers) for every TFC device and placed object:
- they sit on the grid;
- worldgen can place them and they use chunk-section storage;
- pistons, hoppers, redstone, light and collision come from the visual state;
- per-block data lives in CE's block-entity NBT;
- there is no entity-count growth.

Use **furniture** for:
- purely decorative items that need arbitrary yaw or off-grid alignment;
- seats;
- large (>1 block) models with simple interaction (e.g. a display rack, a "tfc:workbench" decoration).

Each furniture adds a persisted entity plus packet entities, and players can push or collide with collider entities.

---

## 3. Images, fonts, shift, emoji, packet interception

### 3.1 `images:` schema [SRC `core/.../font/AbstractFontManager.java` ImageParser L463–770; WIKI image]

```yaml
images:
  tfc:gui/knapping:
    file: tfc:font/gui/knapping.png     # under assets/<ns>/textures/; .png auto-appended
    font: tfc:gui                       # DEFAULT = "<pack namespace>:default" (NOT minecraft:default)
    height: 222                         # alias scale|scale_ratio; default = PNG height / rows (auto-read)
    ascent: 13                          # alias y_position; default height-1; must be <= height (CE error), negative allowed [VAN]
    # one of (else auto-assign 1 codepoint):
    char: ''                      # alias chars|unicode; string, \u escape, or number
    chars: ['', '']   # grid rows; equal length; '\u0000' = empty cell
    grid_size: 2,3                      # rows,cols auto-assigned
  tfc:icon_alias:
    ref: tfc:icons:0:2                  # reference image (no file); or ref: tfc:icons + row/col (wiki says "column", code reads "col")
```

- **Codepoint allocation**: one `IdAllocator` per font. It starts at `image.codepoint-starting-value.default: 19968` (U+4E00) or the per-font override `minecraft:default: 57344` (U+E000), and goes up to U+10FFFF. Assignments are cached in `plugins/CraftEngine/cache/font/<ns>/<font>.json` (L516–527), so they stay stable across restarts while the cache exists. Conflicts and exhaustion are reported as config errors.
- One bitmap provider JSON is generated per image (`BitmapImage.get()`, `type: bitmap`). It is appended to `assets/<ns>/font/<font>.json`, **merged with any font JSON already in our pack's resourcepack folder** (`AbstractPackManager.generateFonts` L3696–3740). So we can ship our own `space` or `ttf` providers in the same font.
- `resource-pack.override-uniform-font: true` copies `minecraft:default` to `minecraft:uniform`, so default-font images still work with "Force Unicode Font" (L3742–3757; CFG L99–100).
- Glyph limit: 256×256 per cell [WIKI]. Do not put font PNGs under `textures/item` or `textures/block`, because they would join the atlas and get mipmapped [WIKI first_image].
- **Use a custom font**, not `minecraft:default`, so players cannot type the glyphs. The exception is when the glyph must go into a vanilla lang string (§4.6). The `image.illegal-characters-filter` covers default-font codepoints in anvil, book, chat, command and sign.

### 3.2 Tag syntax [SRC `core/.../plugin/text/minimessage/ImageTag.java`, `ShiftTag.java`]

| Tag | Meaning |
|---|---|
| `<image:ns:id>` | cell 0,0 |
| `<image:ns:id:row>` | cell row,0 |
| `<image:ns:id:row:col>` | cell |
| `<image:ns:id:row:col:'<format>'>` | MiniMessage prefix applied to the glyph, e.g. `'<!shadow><white>'` |
| `<image:ns:id:'<format>'>` | format for cell 0,0 (non-numeric 3rd argument) |
| `<image:id>` | value-only lookup (no namespace) |
| `<shift:N>` | N px of horizontal offset (negative = left), built from offset chars |

Where these tags are parsed:
- In CE MiniMessage contexts: item names and lore, GUI titles in CE's own GUIs, `text_display` text, messages, emoji content.
- Through **packet interception** (§3.4).

PlaceholderAPI expansions: `%image_mm_ns:id[:r:c]%`, `%image_md_…%`, `%image_raw_…%` [WIKI].

### 3.3 Offset (shift) characters [CFG `config.yml` L479–526, `internal/configuration/offset_chars.yml`; SRC `OffsetFont.java`]

- Shifts are 42 bitmap "images" in `minecraft:default` (``–`` negative, ``–`` positive). Each uses the 1×1 `font/offset/space_split.png` with a crafted (often negative) `height` and `ascent: -5000`.
- Advance = `trunc(0.5 + 1*height) + 1`. For example, neg_1 has height −3, giving trunc(−2.5)+1 = −1, and pos_16 has height 15, giving 16.
- `createOffset(n)` decomposes greedily into 256/128/64/48/32/16 chunks plus a remainder. It returns a `minecraft:default`-font component (or a raw string via `fontManager().createRawOffsets(n)`).
- The network layer pre-registers `<shift:-256>`…`<shift:256>` (`AbstractNetworkManager.registerShiftTags` L75–82). Larger values go through dynamic MiniMessage parsing.

### 3.4 Packet interception: exactly what is rewritten [SRC]

- `network.intercept-packets.*` (CFG L554–570), all default **true**: `system-chat`, `tab-list`, `player-info`, `set-score`, `actionbar`, `title`, `bossbar`, `container`, `team`, `scoreboard`, `entity-data`, `item`, `advancement`, `player-chat`, `combat-kill`, `dialog`.
- **Container titles**: `bukkit/.../plugin/network/listener/game/OpenScreenListener.java` L23–64 runs on every clientbound `OpenScreen`, from any plugin, vanilla or Paper `InventoryView#setTitle`.
  1. Read the title. On ≥1.20.3 this is NBT, otherwise JSON.
  2. `matchNetworkTags`: walk **every string value** in the component tree (`AbstractNetworkManager` L143–205) and tokenize it with Adventure's MiniMessage `TokenParser`.
  3. Collect **only** tags named `image`, `l10n`, `shift`, `global` or `papi` (`NETWORK_TAGS` L41–47).
  4. Each literal tag text maps to a pre-built component: images (`<image:ns:id>`, `<image:id>`, `<image:ns:id:r>`, `<image:ns:id:r:c>`, L84–103), shift ±256, l10n keys and globals. Anything else falls back to `ComponentProvider.miniMessage(tag)`, parsed with the viewer context.
  5. `AdventureHelper.replaceText(component, tokens, ctx)` replaces the matched substrings with those components using regex text replacement (L399–418). The replacement becomes a child, so it **inherits the surrounding text component's style (colour)** [INF from Adventure semantics].
- **Consequences for our titles**:
  - Write the tags as **plain literal text** in a Paper component: `Component.text("<shift:-8><image:tfc:gui/knapping>")`.
  - Style it with Adventure: `.color(NamedTextColor.WHITE)`. Container titles are drawn in 0x404040, and that colour multiplies glyph pixels, so without white the texture is darkened to ~25% [VAN]. CE's own titles start with `<white>` for this reason.
  - `<white>` written literally in the title text is **not** parsed by the interceptor; it stays literal. Do not pre-parse with Paper's MiniMessage unless it leaves the unknown `image`/`shift` tags as literal text (non-strict MiniMessage keeps unknown tags as text [INF], **UNVERIFIED**).
  - Tags can be split across several sibling text components, but each tag must be fully inside one string.
- **Direct (no-interception) alternative** [SRC `CraftEngineImages`, `FontManager`]. Read the data at `CraftEngineReloadEvent`:
  - `BitmapImage img = CraftEngine.instance().fontManager().bitmapImageById(Key.of("tfc:gui/knapping")).orElseThrow()`
  - `img.codepointAt(r,c)` and `img.font()`
  - offsets: `fontManager().createRawOffsets(n)` with font `fontManager().offsetFont().font()`

  Then build the **Paper** component: `Component.text(new String(Character.toChars(cp))).font(Key.key(f.namespace(), f.value())).color(WHITE)`.
  - CE's `Image.componentAt` returns **CE-relocated Adventure** (`net.momirealms.craftengine.libraries…`), so do not hand it to Paper.
  - Upside: works even if an admin disables `intercept-packets.container`, and costs no Netty work.
  - Downside: re-read after every CE reload. Codepoints are stable via the cache.
- **Other places**: `text_display` element text is parsed per player. Item names and lore with these tags are rewritten when `intercept-packets.item` is on (`ModernNetworkItemHandler` L409–454). The `entity-data` interception covers custom names and text displays of other plugins. `dialog` covers 1.21.6+ dialogs, which is an option for "player tabs" pages.

### 3.5 Fonts and emoji

- **Fonts** [WIKI font]: there is no CE config. Drop `assets/<ns>/font/*.json` (ttf, bitmap, space, unihex, reference) into the pack, and CE merges its bitmap providers into the same file.
- **Emoji** [SRC `AbstractFontManager.EmojiParser` L399–460; CFG `default_assets/configuration/emoji.yml`; config.yml L441–450]:

  ```yaml
  emoji:
    tfc:fire:
      image: tfc:icons:0:2          # ns:id or ns:id:row:col (must be a real image)
      keywords: [':fire:']          # required
      content: "<!shadow><white><arg:emoji></white></!shadow>"   # alias format; list → joined; <arg:emoji>, <arg:keyword>
      content_overrides: {chat: …, book: …, anvil: …, sign: …}
      permission: emoji.fire
      chat_completion: true
  ```

  Contexts are enabled in `emoji.contexts` (chat, book, anvil, sign) and `max-emojis-per-parse: 16`. Emoji are a chat feature and not needed for GUIs.

---

## 4. GUI texturing recipe

### 4.1 How CE's own GUIs do it [CFG `config.yml` L671–727, `internal/configuration/gui.yml`; SRC `ItemBrowserManagerImpl` L224, `BukkitGuiManager`]

```yaml
images:
  internal:item_browser: {height: 140, ascent: 18, font: minecraft:gui, file: minecraft:font/gui/custom/item_browser.png}  # PNG 182×140
gui.browser.main.title: "<white><shift:-11><image:internal:item_browser>"     # 6-row chest
gui.browser.recipe.smelting.title: "<white><shift:-11><image:internal:cooking_recipe><shift:-136><image:internal:smelting>"
```

- CE parses the title with its own MiniMessage on the server side.
- Buttons are ordinary items in slots (`internal:next_page_0` …, `custom_model_data` + 2D model).
- `updateInventoryTitle` re-sends `OpenScreen` with the same container id to change the title live (`BukkitGuiManager` L78–86). Paper's `InventoryView#setTitle` does the same.

**Measured alignment.** I scanned `item_browser.png` with PIL:
- The first slot interior is at PNG (11, 23).
- A vanilla 9×6 chest slot interior starts at GUI (8, 18).
- So the PNG origin is at GUI (−3, −5).
- Horizontal: `titleLabelX(8) + shift(−11) = −3` ✓.
- Vertical: `titleLabelY(6) + 7 − ascent(18) = −5` ✓.

This confirms the formula below for a 1:1 bitmap (height = PNG cell height).

### 4.2 Pixel math (all values in GUI pixels relative to the window's top-left `leftPos/topPos`)

```
glyphLeft  = titleLabelX + Σ(advances before the glyph)
glyphTop   = titleLabelY + 7 − ascent                     (titleLabelY = 6 on all screens below ⇒ top = 13 − ascent)
advance(image) = round(cellWidth × height/cellHeight) + 1 (1:1 ⇒ width + 1)
advance(<shift:N>) = N
constraint: ascent ≤ height  ⇒ to raise a glyph above (13 − height), pad transparent rows at the PNG bottom
```

The `+7` and `+1` come from vanilla bitmap glyph metrics [VAN], confirmed against the CE asset above. To place the background so that its PNG pixel (0,0) is GUI pixel (gx, gy):
- `ascent = 13 − gy`
- leading shift `= gx − titleLabelX`

| Screen (open via Paper) | Window size | titleLabelX | Slot item positions (interior, 16×16) | Notes [VAN, UNVERIFIED] |
|---|---|---|---|---|
| Chest `9×N` (`createInventory(h, 9N, title)` / `MenuType.GENERIC_9xN`) | 176 × (114 + 18N) | 8 | container `(8+18c, 18+18r)`; player inv `(8+18c, 31+18N+18r)`; hotbar `(8+18c, 89+18N)` | N=6 ⇒ 176×222, player inv y=139, hotbar y=197; inventory label y = height−94 |
| Dispenser / dropper `3×3` (`InventoryType.DISPENSER`) | 176 × 166 | **centered**: `(176 − titleWidth)/2` | `(62+18c, 17+18r)`; player inv `(8+18c, 84+18r)`; hotbar y=142 | Make the title's total advance 0 with a trailing shift, so titleLabelX = 88 |
| Hopper `5×1` (`InventoryType.HOPPER`) | 176 × 133 | 8 | `(44+18i, 20)`; player inv `(8+18c, 51+18r)`; hotbar y=109 | |
| Furnace-type | 176 × 166 | centered | — | Recipe-book button shifts leftPos when the book opens |
| Crafting table | 176 × 166 | 29 | — | |

**Examples.**

*Full replacement of a 6-row chest* with a 176×222 PNG whose (0,0) is the window's (0,0):
- `ascent = 13`, `height = 222`, leading shift `−8`.
- Title text: `"<shift:-8><image:tfc:gui/anvil>"`, followed by `<shift:-177>`. The image advance is 177, so the pen returns to x = 8 for a real text label after it.
- 222 ≤ 256 cell limit ✓.

*Knapping 5×5* in a 6-row chest: use container columns 2..6 and rows 0..4. Slot interiors are at x = 8+18·(2..6) = 44…116 and y = 18…90. Draw rock-texture cells there and use invisible "button" items for the knapped states.

*Dispenser-based small screen*:
- Title = `S1 + image + S2`, chosen so that S1 + (W+1) + S2 = 0. titleLabelX is then exactly 88.
- To put PNG x0 at gx: S1 = gx − 88.
- Integer division: `(176 − 0)/2 = 88` exactly.

### 4.3 Java (Paper) building blocks

```java
// 1) via interception (needs network.intercept-packets.container: true — default)
Component title = Component.text()
    .append(Component.text("<shift:-8><image:tfc:gui/anvil><shift:-177>", NamedTextColor.WHITE))
    .append(Component.text("Anvil", TextColor.color(0x404040)))   // normal label after the background
    .build();
Inventory inv = Bukkit.createInventory(holder, 54, title);
player.openInventory(inv);
// later: player.getOpenInventory().setTitle(...)  → re-sent OpenScreen, re-intercepted

// 2) without interception (cache on CraftEngineReloadEvent)
FontManager fm = CraftEngine.instance().fontManager();
BitmapImage bg = fm.bitmapImageById(net.momirealms.craftengine.core.util.Key.of("tfc:gui/anvil")).orElseThrow();
Key bgFont = Key.key(bg.font().namespace(), bg.font().value());
Key offFont = Key.key(fm.offsetFont().font().namespace(), fm.offsetFont().font().value());
Component shift(int n) { return Component.text(fm.createRawOffsets(n)).font(offFont); }
Component glyph = Component.text(new String(Character.toChars(bg.codepointAt(0, 0)))).font(bgFont).color(NamedTextColor.WHITE);
```

CE's `Key` is `net.momirealms.craftengine.core.util.Key`. CE's `Component`, if you touch it, is the relocated Adventure (general report §8.1).

### 4.4 Buttons and layered states
- Layer several glyphs: background, then `<shift:-(W+1)>`, then overlay glyphs (progress bars, a temperature indicator, the forging "hit" markers). Re-send the title to change them, with `setTitle`.
- Each title change is one `OpenScreen` packet. The client re-opens the screen and keeps the cursor and slots [VAN], but don't animate faster than a few times per second.
- Make progress bars from a grid image (`grid_size`) with one cell per frame.
- **Buttons** are items in slots. For an invisible button, use an item whose model is empty or transparent, with tooltip text. TFC forging buttons go in the slots under the drawn button art.

### 4.5 Drawing outside the window
- Title glyphs are not clipped to the window [VAN]. Negative shifts or large shifts (for x) and large or negative ascent (for y) place art anywhere relative to the window.
- The window is centred: `leftPos = (screenW − imageWidth)/2`, `topPos = (screenH − imageHeight)/2` in GUI units, where `screenW = windowPx / guiScale`.
- **GUI-scale caveat**: positions are scale-independent in GUI units, but the *available* space around the window shrinks at high GUI scale or small window sizes. Art placed more than ~(screenW−176)/2 to the side may go off-screen. At 1920×1080 with scale 4 the screen is 480×270, leaving 152 px on each side and 24 px above a 222-tall chest.
- Screens with a recipe book (player inventory, crafting, furnaces) shift `leftPos` by 77 when the book opens, and the art moves with the window.
- **Click detection**: a click outside the window is a container click with slot −999. Bukkit reports `SlotType.OUTSIDE` and `rawSlot −999` [VAN/INF]. The **server cannot know where** outside the window the click happened, so an outside "button" can only mean "clicked anywhere outside". Which client versions send the packet when the cursor is empty is **UNVERIFIED**; test it.

### 4.6 The player-inventory (E) screen
- The server gets no packet when the player inventory opens, and cannot set its title.
- Resource-pack option via CE: `lang: { all: { container.crafting: "<white><shift:-97><image:tfc:inv_tab_bar>…" } }` (`TranslationManagerImpl` L351–356, where `all` means every language).
  - CE's `LangParser` turns the MiniMessage into a **legacy `§` string** (L487–490: deserialize with ShiftTag and ImageTag, then `LegacyComponentSerializer`). **Fonts are lost** in that conversion, so the glyph must live in **`minecraft:default`**. CE's shift chars already do. The wiki's lang example also uses `font: minecraft:default`.
  - The `§f` produced by `<white>` should override the 0x404040 label colour [VAN, **UNVERIFIED**].
  - The inventory label sits at titleLabelX 97, y 6 [VAN].
  - Caveats: `container.crafting` is **also the crafting-table title**, so add a crafting-table layout or accept the art there. The creative inventory does not show it. Default-font codepoints can be typed by players (keep `illegal-characters-filter` on). The art moves with the recipe-book shift.
- Detecting the click: use slot −999 as above. Another option outside CE: Paper's `PlayerRecipeBookSettingsChangeEvent` fires when the recipe-book button is toggled. It could be re-skinned via the vanilla sprite `textures/gui/sprites/recipe_book/button*.png`. **UNVERIFIED.**

---

## 5. Pitfalls

1. **Renderer elements are fixed per state and per BlockEntity.** The dynamic element list is gathered once in the `BlockEntity` constructor. For a variable number of items, make one element that manages N entities internally.
2. **Custom constant elements are recreated on every state change.** Keep state in the controller.
3. **`show`/`hide` run on Netty or culling threads** (chunk-send listener). Never touch Bukkit world or entities there. Pre-build packets on the region thread, as CE's elements cache them.
4. **No interpolation** in built-in elements; transforms snap (§1.8).
5. Conditions are evaluated only at show, morph or restore time, not per tick.
6. `rotation:` as a 3-element YAML list fails. Use the string `"x,y,z"`. A single number means **−yaw**.
7. `block_display` position defaults to the block **corner** (0,0,0). Other block elements default to the centre. **Furniture** elements default to `0,0,0` (the anchor).
8. `position`, `yaw` and `pitch` changes cost a teleport packet. Prefer `translation` and `rotation` [WIKI tip, SRC `isSamePosition`].
9. Community edition: no culling. Every renderer in every sent chunk spawns for every player (§1.9 cost table).
10. Image `font` defaults to **`<pack namespace>:default`**, not `minecraft:default`.
11. `ascent ≤ height` (CE throws `resource.image.height_ascent_conflict`). Pad the PNG to go higher; a negative ascent goes lower.
12. A container title in 0x404040 tints glyphs. Always set **white** on the text component holding the tags.
13. The interceptor only resolves `image`, `shift`, `l10n`, `global` and `papi` tags written literally. `<white>`, `<font:…>` etc. in title text are left as literal text.
14. Dispenser and furnace titles are **centred** on the title width. Balance the total advance to 0.
15. CE's API `Component` is relocated Adventure. Do not mix it with Paper components; build Paper components from codepoints.
16. Codepoints are stable only while `plugins/CraftEngine/cache/font/*.json` survives. Use fixed `char:` if anything outside CE (a pre-built pack, docs) hardcodes them.
17. The general report's `offset` (random offset) key for renderers is **not in b3079c5** (wiki-only, newer).
18. Furniture `entity_culling` is read from the furniture top level in code, while the wiki shows it per variant.
19. Seat string third token = `force_player_rotation`.
20. Furniture is a real persisted ItemDisplay entity. Spawn and remove it on the owning region thread (Folia). Collider entities are server-side and affect mob pathing and physics.
21. `display_item_block` and similar built-ins drop their item on removal (`onRemove`). Our controllers must do the same if TFC drops contents.
22. Lang-file glyphs must use `minecraft:default`. Overriding `container.crafting` also changes the crafting-table title.

---

## 6. UNVERIFIED / open questions

1. Whether `tint_source` can copy `minecraft:item_model` (the code copies arbitrary component tags, so it probably works) and whether `custom_model_data` range_dispatch works through it. Test on 1.21.4+.
2. Whether BetterModel `DummyTracker`s created by the `better_model` block element auto-play `idle` for each newly spawned viewer, and their per-block cost.
3. Vanilla screen constants in §4.2 (titleLabelX/Y, slot coordinates, centred titles, hopper and dispenser sizes) come from my knowledge of client code and are **not** in the CE repo. Verify them in-game on 1.21.11 and 26.x. The vertical `13 − ascent` rule is validated against CE's own asset.
4. Draw order on newer clients (the 1.21.6+ GUI renderer rewrite): do title glyphs render under the slot items? CE relies on it for its browser on 26.x, which suggests yes. Not tested.
5. Whether `§f` from a CE `lang` override really whitens a glyph on the inventory label, and whether the creative inventory or the recipe-book state hides or moves it.
6. Whether the client sends a click packet (slot −999) when clicking outside the player inventory with an empty cursor, and how Bukkit reports it (`SlotType.OUTSIDE`, `ClickType.LEFT` vs `WINDOW_BORDER_*`).
7. How Paper's MiniMessage treats `<image:…>`/`<shift:…>` (unknown tags) when parsing titles. Safer: build with `Component.text`.
8. The thread `restore` and `cull` run on with Premium culling (assumed to be the culling thread pool).
9. The wiki's `offset` renderer key: which CE release introduced it.
10. Furniture collider type, packet cost per hitbox, and Folia threading of `CraftEngineFurniture.place` were not tested.
