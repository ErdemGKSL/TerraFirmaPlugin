# NeoForge (TFC) concept → our implementation

Use this table when you meet a TFC construct and need to know what it becomes here. The plugin-specific "how" lives in the `craftengine`, `bettermodel`, `betterhud` and `paper-folia-threading` skills.

| TFC / NeoForge construct | Our equivalent | Notes |
|---|---|---|
| `Block` subclass + `BlockState` properties | CraftEngine block (YAML: `properties`, `appearances`, `variants`) + our Java behaviour registered via CraftEngine's behaviour API | Real server-side block, so properties exist on the server. Each state picks a client appearance: an `auto_state` pool entry, an explicit repurposed vanilla state, or an `entity_renderer`. |
| Block shape / collision (`VoxelShape`) | Choose the client-visible vanilla state whose shape matches (button, carpet, slab, snow layer, fence, ladder...) | The client predicts collision, outline and some movement (climb, slow-down, slipperiness) from the vanilla state. Pick the base deliberately. See the `craftengine` skill. |
| `BlockEntity` (data) | Per-chunk block-data store keyed by packed block position, serialized as compact binary, (de)serialized off-thread | Never store block data in a static `Map<Location, ...>`. |
| `BlockEntity` ticking | Calendar timestamps + lazy evaluation on access. For truly active devices, our device ticker runs only loaded, active devices, at reduced frequency, with region-thread commits | e.g. a firepit only needs ticks while lit and loaded, and catches up from the calendar on load. |
| `BlockEntityRenderer` | CraftEngine `entity_renderer` (item_display/text) on the block, static or dynamic | Used for the anvil top, ingot piles, placed items, tool racks, barrel fluid level. Each element costs client entities, so keep the count low. |
| Item `DataComponent` / capability (heat, food, forging, fluid, mold, vessel) | Versioned PDC payload on the ItemStack, read and written through a `core` codec | Store base values + timestamps only. Derived values (current temperature, rotten?) are computed on read. |
| Item tooltip text | Lore rewritten in outgoing packets (async, netty thread), never written into the server-side stack | Keeps stacks stackable and avoids rewriting inventories every tick. |
| Item model predicates (heat glow, pan stages) | 1.21.4+ item model definitions (`select`/`range_dispatch` on custom model data) generated into the CraftEngine pack | |
| `Item` subclass behaviour | CraftEngine item + our item behaviour / Paper interaction events | |
| Recipe types and serializers | Our recipe registry in `core`, loaded from the original TFC JSON | Plain vanilla shaped/shapeless go through Paper's recipe API. Everything else is intercepted. |
| `Container` + `Screen` (GUI) | Chest-style inventory GUI with a CraftEngine font-image background (custom title) | Server-authoritative. Clicks are handled in inventory events on the player's region thread. |
| Player inventory tabs (calendar, nutrition, climate) | Click outside the inventory window, where the resource pack draws a fake button, to open our TFC GUI | Locked decision. Any outside click opens it; we accept that. |
| Client HUD overlays (health/thirst/food bars) | BetterHud HUD elements fed by our placeholders | Vanilla bars are hidden through the resource pack. |
| Custom packets (`network/*Packet`) | A server-side handler for the same action triggered by vanilla interactions/GUI clicks, or data exposed to BetterHud | No client mod, so every client→server packet needs a vanilla trigger. |
| Mixins (`mixin/*`) | Paper events → packet rewriting → isolated NMS, in that order of preference | Write down why NMS was needed. |
| `Entity` subclass | Vanilla carrier mob (invisible, its pathfinding/physics) + BetterModel model + our data in entity PDC + Paper Mob Goals | One model per animal. Baby/adult via model scale. Gender/variants via textures/bones. |
| `Brain` / goals | Paper Mob Goals API (`Bukkit.getMobGoals()`), lightweight goals | AI runs on the entity's region thread, so keep each goal tick O(1). |
| Particles | Vanilla particles or a short-lived display entity, **only on explicit actions** | No ambient/passive particles (locked decision). |
| Sounds (`sounds.json`) | CraftEngine sound definitions in the pack | |
| Biome colormaps / client tint | Custom biomes (datapack, `grass_color`/`foliage_color`/`water_color`) + async biome-id rewrite in chunk packets for seasons | Locked decision. Rewriting runs on netty threads, so precompute lookup tables and never allocate per block. |
| New fluids in the world | Not possible client-side. Divergence: vanilla water/lava + server-side behaviour, or disallow placement | Fluids inside containers are just item data. |
| Data managers / reload listeners | Our registry loaders: off-thread parse, immutable snapshot, atomic swap | |
| Tags | Our tag registry loaded from TFC tag JSON | Vanilla block tags don't include CraftEngine blocks. |
| `TFCConfig` | Our YAML config with the same option names | |
| Commands | Paper Brigadier command API | |
| Advancements, loot tables | Datapack shipped by the plugin (Paper lifecycle datapack discovery), plus our own handlers for TFC's custom loot conditions | |
| World generation (`TFCChunkGenerator`) | Bukkit `ChunkGenerator` + `BiomeProvider` (async worldgen threads) using our platform-free `worldgen` module | Placing CraftEngine blocks during generation: see the `craftengine` skill. |
| Capabilities on block (fluid/item handlers) | Our internal interfaces in `core` (e.g. `FluidContainer`, `HeatConsumer`), implemented by device data classes | |
| `Calendars.get().getTicks()` | Our `Calendar` service (global, thread-safe read via volatile/atomic) | |
