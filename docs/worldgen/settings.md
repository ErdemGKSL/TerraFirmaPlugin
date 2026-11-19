# World settings (PAR-WORLD-16)

TFC refs: `ForgeEventHandler#onWorldLoad` (forced game rules), `#onCreateNetherPortal`, `config/ServerConfig` (`enableForcedTFCGameRules`, `enableNetherPortals`), `world/settings/Settings` (spawn distance and centre).

| Setting | TFC | Here |
|---|---|---|
| Forced game rules | `enableForcedTFCGameRules` (default true): naturalRegeneration, doInsomnia, doPatrolSpawning, doTraderSpawning all false | `TfcWorldRules` (module `world_settings`): applied to every world on `WorldLoadEvent` and to the loaded worlds at enable. Off with `-Dtfp.world.forced-gamerules=false`. Rule keys resolve by 1.21.11 key or legacy name |
| Daylight cycle | forced off (calendar) | `calendar/DaylightSync` (not this module) |
| Nether portals | `enableNetherPortals` default false: `PortalSpawnEvent` cancelled | `PortalCreateEvent` with reason FIRE cancelled. On with `-Dtfp.world.nether-portals=true` |
| Spawn | land search with spawn distance 4000, centre 0,0 | `TfcSpawn` + `TfcChunkGenerator.getFixedSpawnLocation` (the world spawn) |
| First-join offset | none (TFC sets the exact spawn on world creation; vanilla's 2..9 block radius around spawn applies to new players) | `TfcFirstJoinSpawn` puts a new player on the cached TFC spawn exactly (`AsyncPlayerSpawnLocationEvent`) |
| Vanilla structures | no vanilla structures in a TFC world | our `ChunkGenerator` generates none (`shouldGenerateStructures` false) |
| Dimensions | nether/end exist but portals off | nether and end are untouched vanilla worlds; the policy is the portal switch above |

Config keys (YAML) for these come with PAR-WORLD-22; until then the JVM properties above are the switches.
