# World settings (PAR-WORLD-16, PAR-WORLD-22)

TFC refs: `ForgeEventHandler#onWorldLoad` (forced game rules), `#onCreateNetherPortal`, `config/ServerConfig` (`enableForcedTFCGameRules`, `enableNetherPortals`), `world/settings/Settings` (the generator settings), `client/screen/CreateTFCWorldScreen` (replaced by the config file, NP-CLIENT-05).

## The file

`plugins/TerraFirmaPlugin/worldgen-settings.yml`, written from the jar on first start (module `world_settings`, `WorldSettingsConfig`):

| Key | TFC | Default |
|---|---|---|
| `forced-gamerules` | `enableForcedTFCGameRules` | true; `-Dtfp.world.forced-gamerules=true\|false` overrides |
| `nether-portals` | `enableNetherPortals` | false; `-Dtfp.world.nether-portals=true\|false` overrides |
| `defaults.<field>` | `Settings` codec fields: `flat_bedrock`, `spawn_distance`, `spawn_center_x`, `spawn_center_z`, `temperature_scale`, `temperature_constant`, `rainfall_scale`, `rainfall_constant`, `rock_layer_settings` (codec form), `continentalness`, `grass_density`, `finite_continents` | TFC's overworld preset |
| `worlds.<world name>.<field>` | per world | over `defaults` |

Unknown or badly typed fields are refused at load (the file is then ignored and TFC's defaults apply, with an error in the log).

## Per world, fixed at creation

TFC keeps `Settings` in level.dat. Here `TfcChunkGenerator` resolves them when it builds the terrain (`WorldSettingsStore`): `<world folder>/tfp/world-settings.yml` if it exists, else the config's values for that world name, written there. Later edits of `worldgen-settings.yml` affect new worlds only. A stored file that cannot be read stops the terrain build instead of being replaced.

## Who reads them

| Field | Consumer |
|---|---|
| continentalness, finite_continents, temperature/rainfall scale and constant | `RegionGenerator` via `WorldSettings.regionSettings()` in `TfcTerrain` (region map and chunk data of the generator) |
| flat_bedrock | `BedrockLayer` (TFC's `makeBedrock`: one layer, or natural `nextInt(6) < 6 - y` for y 0..5) in `TfcTerrain.fill` |
| grass_density | `Plants` / `PlantPlacer.dynamicTries` |
| spawn_distance, spawn_center_x/z | `SpawnSearch.find` in `TfcChunkGenerator.spawn` |
| rock_layer_settings | data only (`RockLayerConfig`); the layer walk is PAR-WORLD-23 |

Not yet: the climate readers outside the generator (HUD, snow, rain, weather, seasonal remap, plant effects, `ChunkClimateSource`) use TFC's preset; PAR-WORLD-51.

## Other world rules (PAR-WORLD-16)

| Setting | TFC | Here |
|---|---|---|
| Forced game rules | naturalRegeneration, doInsomnia, doPatrolSpawning, doTraderSpawning all false | `TfcWorldRules`: applied on `WorldLoadEvent` and to the loaded worlds at enable. Rule keys resolve by 1.21.11 key or legacy name |
| Daylight cycle | forced off (calendar) | `calendar/DaylightSync` (not this module) |
| Nether portals | `PortalSpawnEvent` cancelled | `PortalCreateEvent` with reason FIRE cancelled |
| First-join offset | none | `TfcFirstJoinSpawn` puts a new player on the cached TFC spawn exactly (`AsyncPlayerSpawnLocationEvent`) |
| Vanilla structures | none in a TFC world | `shouldGenerateStructures` false |
| Dimensions | nether/end exist but portals off | nether and end are untouched vanilla worlds |
