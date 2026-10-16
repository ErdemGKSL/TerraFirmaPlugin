# Porting ledger

Pinned TFC source: https://github.com/TerraFirmaCraft/TerraFirmaCraft @ `c3b4d9536b82e5a9746dd793c586720a74108057` (MC 1.21.1, NeoForge).

Status values: `not started` → `rules` (core logic + tests) → `playable` (works in-game) → `visual-complete` (all assets/GUI/HUD done).

## Features

| Area | Feature | Status | TFC sources | Tests | Notes |
|---|---|---|---|---|---|
| Calendar & climate | Calendar | not started | `util/calendar/` | | |
| Calendar & climate | Climate model | not started | `util/climate/` | | |
| World generation | Region / layers / rivers | not started | `world/region/`, `world/layer/`, `world/river/` | | |
| Geology | Rocks, ores, soils | not started | `common/blocks/rock/`, `common/blocks/soil/` | | |
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
| Custom sky / star map | Not implemented (optional core-shader work later) | Needs core shaders | Optional |
