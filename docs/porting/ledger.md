# Porting ledger

Pinned TFC source: https://github.com/TerraFirmaCraft/TerraFirmaCraft @ `c3b4d9536b82e5a9746dd793c586720a74108057` (MC 1.21.1, NeoForge).

Status values: `not started` → `rules` (core logic + tests) → `playable` (works in-game) → `visual-complete` (all assets/GUI/HUD done).

## Features

| Area | Feature | Status | TFC sources | Tests | Notes |
|---|---|---|---|---|---|
| Data & registries | Registry framework, strict JSON codecs, recipe/definition schemas, tags | rules | `common/recipes/TFCRecipeSerializers.java` + each recipe `CODEC`, `util/data/DataManager*.java` + definition `CODEC`s, `world/settings/RockSettings.java`, `data/tfc/tags/**` | `core/src/test/java/dev/tfp/core/{json,tag,registry}/**` | Recipe types and definition kinds decode to `SchemaRecord`s until their feature ports a typed record. `TfcDataCoverageTest` (env `TFC_ROOT`) loads all upstream TFC data |
| Data & registries | Ingredients & output modifiers | rules | `common/recipes/ingredients/`, `common/recipes/outputs/` | `core/src/test/java/dev/tfp/core/recipe/**`, `SampleDataTest` | Item-data effects go through `recipe/hooks/*` interfaces, implemented by food/heat/forging (M3/M4) |
| Calendar & climate | Calendar | not started | `util/calendar/` | | |
| Calendar & climate | Climate model | not started | `util/climate/` | | |
| World generation | Region / layers / rivers | not started | `world/region/`, `world/layer/`, `world/river/` | | |
| Geology | Rocks, ores, soils | not started | `common/blocks/rock/`, `common/blocks/soil/` | `tools/datagen` DatagenTest, PinnedCheckoutTest | Assets + CE YAML for full-cube rock/ore/dirt/clay/sand blocks generated (M1-DGN-03). Grass, slabs/stairs/walls and other shapes pending. |
| Geology | Collapse & landslide | not started | `util/tracker/Collapse.java` | | |
| Flora | Crops | not started | `common/blocks/crop/` | | |
| Fauna | Livestock | not started | `common/entities/livestock/` | | |
| Survival | Food & nutrition | not started | `common/component/food/` | | |
| Heat | Item heat | not started | `common/component/heat/` | | |
| Primitive tech | Knapping | not started | `util/data/KnappingPattern.java` | | |
| Metallurgy | Anvil & forging | not started | `common/component/forge/` | | |
| Mechanical | Rotation network | not started | `util/rotation/` | | |

(Add one row per feature as work starts. Split big rows when they get too coarse.)

## Divergences

| TFC behaviour | Our behaviour | Reason | Revisit? |
|---|---|---|---|
| Player inventory has calendar/nutrition/climate tabs | Clicking outside the inventory window (fake button drawn by the resource pack) opens our TFC GUI | The player inventory screen can't be modified without a client mod | No |
| Client colormaps tint grass/foliage by climate + season | Custom biomes (datapack colours) + async biome-id rewrite in chunk packets per season | Tint is computed client-side | Maybe, if a better technique appears |
| Custom HUD overlays | BetterHud | No client mod | No |
| Ambient particles (steam, smoke, etc.) | Particles only on explicit player actions | Client/server performance | No |
| Custom fluids placed in the world (salt water, molten metal, river flow visuals) | Vanilla water/lava visuals + server-side behaviour, or placement disabled | Clients can't receive new fluid types | When a feasible technique appears |
| Ore blocks: 3 grades × ore × rock each with its own texture | Profile-dependent (ARCHITECTURE §9.1). Default 1.21.x `level-3a`: poor/normal share a visual, rich distinct. Default 26.x `level-2`: all grades distinct | Vanilla-client visual state budget, leaving room for other CraftEngine packs | Owner-configurable |
| Gem ores (amethyst, diamond, emerald, lapis, opal, pyrite, ruby, sapphire, topaz) look like ore in rock everywhere | From `level-2`: look like their host rock until exposed to air, then show an overlay (display entity) | Visual budget; bonus anti-xray | Owner-configurable |
| Deposits (gravel per mineral × rock) | From `level-2`: one gravel+specks visual per mineral, rock-agnostic | Visual budget | Owner-configurable |
| Wood/stripped wood blocks have per-axis models | From `level-1`: all axes share one visual (bark on all faces) | Visual budget | Owner-configurable |
| Metal blocks, mechanical blocks, crates, workbenches are block models | Drawn with `entity_renderer` display entities | Visual budget (approved) | No |
| Raw/hardened rock drops the raw block when mined while isolated (`tfc:is_isolated` loot condition) | Always drops 1-4 loose rocks | The condition needs our own CE loot condition; not ported yet | Yes, with rock collapse work |
| Mojang/NeoForge codecs ignore unknown JSON keys; Gson keeps the last of duplicate keys; JSON parsing is lenient | Our data loader rejects unknown keys, duplicate keys and non-strict JSON, and fails the whole load (all errors listed) | Catch datagen and hand-override mistakes at build time (M1-REG-04 test) instead of silently ignoring data | No |
| Ingredient JSON arrays hold vanilla values only | Arrays may hold any ingredient type | Superset; all TFC data parses unchanged | No |
| `PreciseIngredient.modifyStackForDisplay` (heat/rot/trait shown on JEI stacks) | Not in core; display stacks are a recipe-viewer/GUI concern | No JEI on vanilla clients; our GUIs add it when needed | When a recipe viewer GUI is built |
| `RemoveDyeModifier` clears the dye on the input stack and returns it | Hook returns a dye-less copy; inputs are never mutated | Inputs are snapshots under our threading model | No |
| Custom sky / star map | Not implemented (optional core-shader work later) | Needs core shaders | Optional |
| NeoForge ships the `c:` common tags (`c:cobblestones`, `c:feathers`, ...) and `minecraft:milk`; TFC's data references them and only adds members to some `c:` tags | Datagen exports every `c:`/`minecraft:` tag file TFC has (TFC's additions only, not NeoForge's or vanilla's members). The 16 `c:` tags (and 12 vanilla tags) TFC references but never defines are listed in `data/external_tags.json`, generated from the references core's loader finds; the loader accepts exactly those undefined tags. Vanilla/NeoForge ids used by the data are listed in `id_map.json` `external_ids` the same way. Their members are not invented | The checkout has no NeoForge or vanilla tag/registry data. Member resolution of external tags (on a vanilla Paper server) belongs to the runtime tag bridge | Yes, when tags are wired to Paper's tag registries |
