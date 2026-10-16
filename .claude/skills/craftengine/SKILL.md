---
name: craftengine
description: How this plugin uses CraftEngine (Xiao-MoMi) for every custom block, item, furniture piece, GUI texture and the merged resource pack. Covers the real-server-block + visual-vanilla-state model, block_state_mappings / auto_state pools / explicit repurposed vanilla states, hitboxes inherited from the visual state (button for small ore pieces, carpet, tripwire, barrier...), entity_renderer, config_factory/templates for TFC's huge block matrices, custom Java block/item behaviours and block-entity controllers, worldgen placement of CE blocks, startup timing, the Java API, Premium-only traps and performance. Use this whenever a task adds or changes a block, item, tool, furniture, block appearance/texture/model/hitbox, GUI background, resource-pack content, or touches CraftEngine YAML or its API, even if CraftEngine isn't named (e.g. "add granite ore blocks", "make loose rocks", "anvil texture").
---

# CraftEngine in this project

This is the **hub**: the mental model and decision order. The details live in focused sub-skills. Load the one for your task:

| Task | Sub-skill |
|---|---|
| Block YAML, properties, variants, settings, built-in behaviours, TFC family → built-in vs custom | `craftengine-blocks` |
| Appearances, pools, `block_state_mappings`, repurposed vanilla states, item model definitions, resource pack build/merge/hosting | `craftengine-visuals-pack` |
| Items, tools/tiers, recipes, loot, events/functions/conditions, item packets vs our tooltips | `craftengine-items` |
| entity_renderer contents, furniture, images/fonts, textured GUIs, the outside-inventory button | `craftengine-gui-furniture` |
| Java API, custom behaviours, block-entity controllers, storage, worldgen placement, startup order, threads | `craftengine-java-api` |
| How datagen should emit CE config (templates, YAML extensions, what never to emit) | `craftengine-config-generation` |

Facts here come from `references/research-report.md` (CE 26.9.2 @ `b3079c5`, researched 2026-10) and the sub-skills' reports. Read the relevant report section before doing anything unusual; it has the exact source paths.

## Mental model (get this right first)

```
server chunk:  real NMS block "craftengine:custom_N"  (one per CE state; pool size = block.serverside-blocks)
CE storage:    {id: "tfp:rock/raw/granite", properties: {...}}   (<world>/craftengine/, keyed by CE id)
network:       custom_N  ──mapped──▶  a VISUAL vanilla state  (note_block[...], minecraft:diorite, oak_button[...])
resource pack: that vanilla state's blockstate variant → our model
```

- **Server-side states are effectively unlimited.** Raise `block.serverside-blocks` (restart required, steps of ~1000) to cover all our states. Estimate the count from the datagen output.
- **The hitbox is the visual vanilla state's shape**, on both server and client: outline, collision, suffocation and occlusion. There are no custom VoxelShapes; only `support_shape` can be overridden (by naming another vanilla state). **Choosing a TFC block's shape = choosing its visual vanilla state.** Client-predicted behaviours (climbing, cobweb slowdown, slime bounce, note-block/tripwire interaction) also come from that state.
- **Sounds:** for every visual state in use, CE silences that vanilla SoundType client-side and replays correct sounds server-side.

## Choosing an appearance: decision order

1. **Full cubes that occur in bulk (terrain: rock, soil, sand, ore, planks, logs):** a real model on a freed vanilla state. No entities.
   - `auto_state: solid` takes from note block (~1100, +200 on 26.1+) + mushroom blocks (189).
   - **Explicit `state: minecraft:<vanilla>` + `model`** on vanilla blocks that never exist in a TFC world: stone family, deepslate, vanilla ores, wool ×16, concrete ×16, terracotta ×17, glazed terracotta (×4 facings), concrete powder, vanilla planks/logs (each axis is a state). This **rewrites that vanilla state for everyone**, so also add `block_state_mappings` sending real instances elsewhere if they could ever appear (other worlds, creative), and remove them from recipes/loot.
   - Ore grades (poor/normal/rich) × 21 rocks get **separate textures**. The budget allows it with the two pools above. Keep a running count with `/ce debug auto-state-usage solid` and the datagen allocation report.
2. **Small or odd shapes:** pick the vanilla state with the right hitbox (table in `references/appearance-strategy.md`).
   - Button (tiny, no collision): small ore pieces, loose rocks, twigs, shells
   - Carpet (1/16): fallen leaves, thin layers
   - lower/higher tripwire (no collision): groundcover, low plants
   - Snow layers: snow and ice piles
   - Ladder/vine (climbable): ropes
   - Cactus, sugar cane, saplings, crop-like pools: plants
   - Barrier (full, invisible) + renderer: devices that need a full hitbox
   - Bed foot (9/16): low devices
   Buttons/fences/walls are **multipart or need many states**. CE drops `multipart` when it overrides a blockstate file, so for those use the template pattern: real vanilla state as hitbox + `entity_renderer` drawing the look (`default:block_state/button`, `default:block_state/fence`).
3. **Rare, decorative or dynamic blocks** (anvil top with items, ingot/sheet piles, tool racks, placed items, barrel/pot contents): `entity_renderer` (`item_display`, `text_display`, `block_display`). Use `transparent: true` with a shared `auto_state: {type: sugar_cane, id: transparent}` when the vanilla look must vanish. Prefer `translation`/`rotation` over `position`/`yaw`. Every element is a packet entity per viewer, and entity culling is Premium-only, so keep counts per chunk low.

Two custom blocks may share one explicit state only with the **identical** model; otherwise CE raises a load error.

## Writing configs (generated by `tools/datagen`, never hand-written in bulk)
- Pack lives in `plugins/CraftEngine/resources/tfp/` (`pack.yml`, `configuration/**`, `resourcepack/assets/tfp/**`). The folder name is the default namespace. Folders starting with `.` are ignored.
- **Datagen expands matrices** into literal per-id entries that call **our own `tfp:` templates**. No `config_factory`, and no runtime dependency on `default:` templates. Rules and pitfalls are in `craftengine-config-generation`.
- Version conditionals: `$$>=26.2:`, `$$1.21.4~1.21.11:`, `$$fallback:`.
- **Namespace every behaviour `type`** (`tfp:...`). An un-namespaced type silently means `craftengine:`.
- Models: TFC's two-layer ore model (`tfc:block/ore`, `all` + `overlay`) can be copied as a parent model and referenced via `generation: {parent: tfp:block/ore, textures: {all: ..., overlay: ...}}`. See the `tfc-asset-migration` skill.
- **Premium-only and silently ignored in the free edition:** `client_bound_data`, `client_bound_model`, `client_bound_material`, pack obfuscation/protection, entity culling. Never depend on them. Item lore for heat/decay is done by **our** packet rewriting (see `paper-folia-threading`).

## Java side (through `integration/craftengine` only)
- Dependency: `compileOnly("net.momirealms:craft-engine-core:<v>")` and `craft-engine-bukkit:<v>` from `https://repo.momirealms.net/releases/`. Check `maven-metadata.xml`; Maven can lag Modrinth. CE's API jars **relocate Adventure and NBT** (`net.momirealms.craftengine.libraries.*`), so never pass Paper `Component`s into CE APIs.
- `paper-plugin.yml`: depend on CraftEngine with `load: BEFORE` (CE loads first).
- **Register our behaviours in `onLoad()`**: `BlockBehaviors.register(Key.of("tfp","crop"), factory)`, `ItemBehaviors.register`, `BlockEntityElementConfigs.register`, `CommonFunctions/CommonConditions.register`. The registration must exist before CE parses configs.
- **Per-block data and tickers:** a behaviour can implement `EntityBlock` and provide a `BlockEntityController` (`save/loadCustomData`, `onLoad/onUnload`, sync ticker on the region thread, async ticker that must not mutate the world). Sleeping tickers exist only on CE's dev branch, not in 26.9.x. Use controllers for devices and player-planted crops, with no ticker and lazy calendar catch-up. Wild worldgen blocks are property-only. Details: `craftengine-java-api`.
- Published API version: **26.9.1** (26.9.2 isn't on the Maven repo yet).
- Lookups: `CraftEngineBlocks.getCustomBlockState(block)`, `byId(key)`, `place(...)`/`remove(...)`, which run **on the owning region thread only**. `CraftEngineItems.byId(...).buildBukkitItem()`, `getCustomItemId(stack)`. `Block#getType()` returns a fake material (`deceive-bukkit-material`), so never identify blocks by Material.
- `loadedBlocks()` etc. are empty until the first `CraftEngineReloadEvent`, and definitions are recreated on each reload. Cache `ImmutableBlockState`s and refresh them on every `CraftEngineReloadEvent`.
- Events: `CustomBlockPlaceEvent`, `CustomBlockBreakEvent`, `CustomBlockInteractEvent`, furniture events, `AsyncResourcePackCacheEvent` (`registerExternalResourcePack(Path)`), `AsyncResourcePackGenerateEvent`.
- Never `/reload` or hot-unload CE: it shuts the server down.

## Worldgen
CE wraps the world's chunk generator (Paper/Folia), including Bukkit custom generators, and injects chunk sections before each stage. So our `ChunkGenerator`/`BlockPopulator` **writes `CraftEngineBlocks.getBukkitBlockData(state)`** (never `place(...)`), and CE records it. This is high-confidence inference, not yet runtime-tested; the first worldgen spike must verify it with `/ce debug is-section-injected` and `/ce debug target-block`. Requirements:
- **`misc.delay-configuration-load: false`** in CE's `config.yml`, because otherwise spawn chunks generate before CE has loaded configs or hooked worldgen. The `dependency-bootstrap` installer sets it.
- Our CE pack files are written in our `onLoad()`, before CE's `onEnable` parses them.
- Cached states must be refreshed on `CraftEngineReloadEvent`, and generator code must be thread-safe.

## Resource pack
CE builds and hosts the single merged pack (workflows: generate → validate → zip → upload → send). BetterModel's `BetterModel/build.zip` is merged by default. BetterHud merges itself into CE's pack through its built-in hook. After changing our pack at runtime, run `/ce reload all`. `/ce reload` alone doesn't rebuild the pack.

## Debug commands
`/ce debug auto-state-usage <group>`, `visual-state-usage`, `real-state-usage`, `target-block`, `is-section-injected`, `export-block-state-mappings`; `/ce reload [config|recipe|pack|all]`.
