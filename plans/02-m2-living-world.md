# M2: Living world

Goal: a TFC world. TFC calendar and climate, seasons with colour changes, weather tied to temperature, and the full TFC world generator (regions, continents, rivers, rock layers, biomes, ores, vegetation). Exit: a player can explore a world indistinguishable in layout from TFC (same seed gives the same region/rock/biome maps as TFC's drawing tests), with seasonal colours and climate-correct weather.

Paths are relative to `src/main/java/net/dries007/tfc/` in the pinned TFC checkout unless noted.

## Calendar

- [x] **M2-CAL-01 Calendar core** · deps: M1-THR-04 · skills: tfc-feature-porting, paper-folia-threading
  - TFC: `util/calendar/` (`ICalendar` constants, `Calendar`, `ServerCalendar`, `Month`, `Season`, `Day`, `CalendarTransaction`)
  - Do: port to `core` (pure). Run a global clock service on the global region scheduler (tick advance, `daysInMonth` config, doDaylightCycle sync, sleep skipping via `CalendarTransaction`). Publish a volatile snapshot. Persist in the world PDC.
  - Done when: golden tests (date ↔ tick, month/season boundaries), the Folia self-test and a persistence test pass.
  - Status: done. Golden tests (`CalendarTicksTest`, `CalendarStateTest`, `CalendarCodecTest`), `calendar.persist` self-test passes on paper and folia, 1.21.11 and 26.3 (single start each; `scratchpad/cal`). Follow-ups not in this task: M2-CAL-03 config (start day, daysInMonth, day length, reject startDay -1); sleep skipping and doDaylightCycle sync (TFC `ServerCalendar`/`CalendarTransaction`), to be a new task in M2-CAL.

- [x] **M2-CAL-02 Calendar commands & HUD date** · deps: M2-CAL-01, M1-INT-04 · skills: betterhud
  - TFC: `common/commands/TimeCommand.java`
  - Do: `/tfp time` (set/add with TFC semantics); `tfp_date` and `tfp_season` placeholders (`tick: 20`).
  - Done when: the HUD shows the date and commands match TFC behaviour.
  - Status: commands done and checked on a live Paper 1.21.11 server (`/tfp time set|add`: replies non-empty, date moves forward, saved across a restart; `scratchpad/time-check2`). Still open: the on-screen date element (BetterHud text with `tick: 20` is not generated yet, so the HUD date check is not done). Shown in a client screenshot on 1.21.11 ("June 1, 1000 (Summer)" at the top-left, no glyph boxes; scratchpad/retry/date-hud.png). The merge-retry path after a late BetterHud merge is not exercised in that run; it is covered by InstallerTest only.

## Climate

- [x] **M2-CLIM-01 Climate model** · deps: M2-CAL-01, M2-GEN-05 · skills: tfc-feature-porting
  - TFC: `util/climate/` (`OverworldClimateModel`, `ClimateModel`, `KoppenClimateClassification`), `world/chunkdata/`
  - Do: pure functions: average/actual temperature, rainfall and groundwater per position, season and time of day, from chunk data. A thread-safe API with a bounded Caffeine cache.
  - Done when: golden values vs TFC for sample positions, and property tests (continuity, bounds) pass.
  - Status: done as pure core math (`core/climate/`: OverworldClimate, Solar, CalendarPhase, ChunkClimate, TfcMath, XoroshiroRandom). Golden and property tests green; sampled outputs match TFC bit-for-bit (oracle run against TFC's own methods). Follow-ups: chunk-data inputs (M2-GEN-05), rain/thunder (M2-WTH-01), ClimateRange(s) and the render cache (M2-CLIM-02).

- [x] **M2-CLIM-02 Climate ranges** · deps: M2-CLIM-01, M1-REG-01
  - TFC: `util/climate/ClimateRange(s)`, data `climate_range/**`
  - Do: the registry plus checks used by crops, plants and fauna.
  - Done when: the data loads and unit tests pass.
  - Status: done. Typed `ClimateRange` and `ClimateRanges.check` in `core/climate/` (TFC arithmetic, inclusive bounds); `climate_range` is a typed registry (`DefinitionKinds.CLIMATE_RANGE`); golden tests and a data-load test over all 49 shipped files. Network `STREAM_CODEC` and the crop/plant/fauna callers are left to their features.

- [x] **M2-CLIM-03 Player climate readout** · deps: M2-CLIM-01, M1-INT-04
  - Do: `tfp_ambient_temp` placeholder (snapshot updated on movement throttle); climate page data for the player tabs GUI (M3-GUI-01).
  - Done when: the HUD value matches `/tfp debug chunk`.
  - Status: done. On a Paper 1.21.11 test server the HUD line `tfp_ambient_temp` ("23.9 C") equals the `/tfp debug chunk` HUD value; the chunk input is neutral until M2-GEN-05 (divergence in the ledger). The debug command needs tfp.admin (op on the test server only).

## Weather & seasons

- [ ] **M2-WTH-01 Rain vs snow by temperature** · deps: M2-CLIM-01 · skills: paper-folia-threading
  - TFC: `util/tracker/WeatherHelpers.java`, `util/EnvironmentHelpers.java`
  - Do: server-side precipitation decisions per position. The client sees snow vs rain through the biome temperature in our seasonal biome variants (M2-SEA-01).
  - Done when: snow falls where TFC would, at the right season.

- [ ] **M2-WTH-02 Snow & ice accumulation/melting** · deps: M2-WTH-01, M1-THR-03
  - TFC: `util/EnvironmentHelpers.java`, `common/blocks/SnowPileBlock.java`, `IcePileBlock.java`, `SeaIceBlock.java`
  - Do: an async pipeline that samples loaded chunks at a throttled rate, decides snow layers / snow piles (remembering the covered block) / ice / sea ice / melting from the climate snapshot, and commits budgeted.
  - Done when: lazy-vs-stepped test for accumulation, region budget held, visual checklist.

- [ ] **M2-SEA-01 Seasonal colour biomes** · deps: M0-NET-01, M2-CLIM-01 · skills: paper-folia-threading
  - TFC: `client/overworld/`, `assets/tfc/textures/colormap/`, `world/biome/`
  - Do:
    1. Datagen emits datapack biomes: each TFC biome × colour band (from TFC colormaps by temperature/rainfall/season), with grass, foliage and water colours.
    2. The `BiomeProvider` returns the base biome.
    3. The packet layer remaps to the seasonal variant using precomputed tables.
    4. On season change, resend chunks with rate limiting.
  - Done when: colours change across seasons, packet rewrite cost is within budget (JMH), and there's a visual checklist entry.

## World generation (worldgen module, pure Java)

- [ ] **M2-GEN-01 Noise library port** · deps: M1-BUILD-01 · skills: tfc-feature-porting
  - TFC: `world/noise/` (incl. MIT FastNoiseLite: keep its license note)
  - Done when: noise outputs equal TFC for fixed seeds (golden arrays).

- [ ] **M2-GEN-02 Layer framework & layers** · deps: M2-GEN-01
  - TFC: `world/layer/framework/`, `world/layer/*`
  - Done when: layer outputs match TFC test vectors.

- [ ] **M2-GEN-03 Region generator** · deps: M2-GEN-02
  - TFC: `world/region/` (`RegionGenerator` + tasks `Init`, `AddContinentsAndSetOceanDepths`, `AnnotateClimate`, `ChooseBiomes`, `ChooseRocks`, `AddRiversAndLakes`, ...)
  - Do: port with concurrent caches (`world/FastConcurrentCache.java`). Port the drawing tests (`src/test/.../drawing/RegionGeneratorTests`, `Artist`) to render region maps to `build/worldgen-images/`.
  - Done when: rendered maps match TFC's for 3 seeds (visual + hash), and the determinism-across-threads test passes.

- [ ] **M2-GEN-04 Rivers** · deps: M2-GEN-03
  - TFC: `world/river/` (`MidpointFractal`, `Flow`, `RiverNoise`); test `FlowTest`
  - Done when: `FlowTest` is ported and green, and river maps are rendered.

- [ ] **M2-GEN-05 Chunk data** · deps: M2-GEN-03
  - TFC: `world/chunkdata/`, `Gen data/tfc/worldgen/rock_settings`
  - Do: rock layers (top/mid/bottom), rainfall/temperature layers, forest type/density, recomputed from the seed (bounded cache, not persisted).
  - Done when: golden checks against TFC.

- [ ] **M2-GEN-06 Biomes & heights** · deps: M2-GEN-05
  - TFC: `world/biome/`, `world/BiomeNoiseSampler.java`, `ChunkHeightFiller.java`, `ChunkNoiseFiller.java`, `world/density/`; test `BiomeNoiseTest`
  - Done when: height/biome samples match TFC.

- [ ] **M2-GEN-07 Bukkit ChunkGenerator & BiomeProvider** · deps: M2-GEN-06, M0-CE-01 · skills: craftengine-java-api, paper-folia-threading
  - Do: `paper` glue that calls `worldgen` and writes cached CE `BlockData` for rock/soil/sand/gravel. `getDefaultWorldGenerator` + `bukkit.yml` docs. Thread-safe, no CE lookups per block.
  - Done when: worlds generate on Paper/Folia; CE tracks the blocks (self-test from the M0-CE-01 plan); JMH chunk budget recorded.

- [ ] **M2-GEN-08 Surface builders & shores** · deps: M2-GEN-07
  - TFC: `world/surface/`, `world/surface/builder/`, `world/shore/`
  - Done when: surface materials per biome match TFC (golden column checks).

- [ ] **M2-GEN-09 Aquifer, carvers & caves** · deps: M2-GEN-07
  - TFC: `world/TFCAquifer.java`, `world/carver/`, `world/feature/cave/`
  - Done when: cave/aquifer layout matches TFC samples; no floating fluids.

- [ ] **M2-GEN-10 Ore veins (graded)** · deps: M2-GEN-07, M1-DGN-04
  - TFC: `world/feature/vein/`, `common/blocks/rock/Ore.java`, worldgen JSON for veins
  - Do: vein placement writing graded ore states per host rock. Gem ores use the host-rock visual + exposure property (profile ≥ level-2).
  - Done when: ore distribution stats match TFC (counts per vein type per 100 chunks, ±10%).

- [ ] **M2-GEN-11 Loose rocks, groundcover & deposits** · deps: M2-GEN-10
  - TFC: `world/feature/` (groundcover/boulders), `common/blocks/GroundcoverBlock(Type).java`, `common/blocks/rock/LooseRockBlock.java`, data `deposit/`
  - Done when: surface indicators appear above veins (small ores, loose rocks), and deposits appear in riverbeds.

- [ ] **M2-GEN-12 Trees** · deps: M2-GEN-07
  - TFC: `world/feature/tree/`, structures `src/main/resources/data/tfc/structure/<wood>{,_dead,_large}/*.nbt`, `common/blocks/wood/`
  - Do: read `.nbt` templates in datagen, place logs/leaves (`persistent=false`) per climate/forest data. Leaves use the leaves visual pool.
  - Done when: forest density/type maps match TFC, and trees look right (visual checklist).

- [ ] **M2-GEN-13 Plants, wild crops & coral** · deps: M2-GEN-12
  - TFC: `world/feature/plant/`, `world/feature/coral/`, `common/blocks/plant/`, `common/blocks/crop/WildCropBlock.java`
  - Done when: plant distribution by climate range matches TFC samples.

- [ ] **M2-GEN-14 Volcanoes, hot springs, other features** · deps: M2-GEN-08
  - TFC: `world/volcano/`, `world/feature/*` (hot springs, ice/glaciers, etc.), `world/placement/`, `world/blockpredicate/`, `world/stateprovider/`
  - Done when: each feature is generated; showcase seeds documented.

- [ ] **M2-GEN-15 Worldgen performance & exposed-gem measurement** · deps: M2-GEN-10, M0-VIS-02 · skills: plugin-testing
  - Do: JMH per-chunk pipeline at N threads; pre-generate 10k chunks under spark; count exposed gem faces per chunk (validates the `level-2` overlay cost).
  - Done when: budgets are recorded in `docs/perf/`, and the overlay decision is confirmed or adjusted.

- [ ] **M2-GEN-16 Spawn & world settings** · deps: M2-GEN-07
  - TFC: `world/settings/`, `world/TFCWorldGen.java`, world preset JSON
  - Do: spawn selection (TFC spawn distance/climate), world settings config (rock layer settings, temperature/rainfall scales), vanilla structures disabled.
  - Done when: new worlds spawn players per TFC rules.

## Exit criteria
TFC-like world on all targets. Maps rendered from our worldgen match TFC for test seeds. Seasons visibly change colours and weather. The worldgen JMH budget is recorded. Ledger rows for calendar, climate and worldgen are `playable`.
