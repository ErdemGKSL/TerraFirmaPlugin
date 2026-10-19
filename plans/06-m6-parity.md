# M6: Parity & release

Goal: everything else TFC has, plus polish to release quality. Exit: every ledger row is `visual-complete` or a documented divergence; v1.0 is released.

Paths are relative to `src/main/java/net/dries007/tfc/` unless noted.

## Mechanical power

- [~] **M6-MECH-01 Rotation network solver** · deps: M1-THR-03 · skills: paper-folia-threading, tfc-feature-porting
  - TFC: `util/rotation/` (`RotationNetwork`, `RotationNetworkManager`, nodes); test `RotationNetworkTest`
  - Do: port to `core`; recompute networks via the async decision pipeline on topology changes; split networks per region safely on Folia.
  - Done when: `RotationNetworkTest` ported and green; Folia stress self-test.
  - Status: ported to `core/rotation` (`NodeKind`, `Rotation`, `RotationNode`, `RotationNetworks`: load, addSource, add, update, remove, setSourceSpeed and the queries; `SourceSpeed.ramp`). Verified against TFC at c3b4d9536: shaft BFS and axle speed/hand (`RotationNetwork.java` 140-185, `AxleNode.java` 29-37), gearbox same-axis and perpendicular (`GearBoxBlockEntity.java` 60-70), gear chain, axle limit of five (`AxleNode.java` 21, 42-46), two sources and the conflict (`RotationNetworkManager.java` 69-96), stalled network (`Node.java` 82-86), disconnected node (`RotationNetworkManager.java` 143-151), cycle handedness check (`RotationNetwork.java` 90-101), water wheel ramp (`WaterWheelBlockEntity.java` 77-79). TFC `RotationNetworkTest`: 24 straight-line and manager cases ported with a passthrough axle. Tests (green): `RotationGoldenTest` 10, `RotationNetworkManagerPortTest` 24, `RotationDeterminismTest` 1, `SourceSpeedTest` 1. Open: the four cycle cases of `RotationNetworkTest` (need a five-face passthrough and a perpendicular inverter, not a production kind); the block and block-entity glue (`RotatingBlockEntity`, `NetworkAction`, axle, gearbox, clutch, sources, sinks); the Paper network update trigger and region threading (decide async, apply sync, split per region on Folia); CraftEngine shaft and gear visuals and models; the GUI and readout (`RotationDisplayStyle`, `BlockEntityTooltips`). TFC has no torque, stall or gear ratio at this commit, so none is ported.

- [~] **M6-MECH-02 Power sources & transmission** · deps: M6-MECH-01 · skills: craftengine-gui-furniture
  - TFC: `common/blocks/rotation/`, `common/blockentities/rotation/` (axle, encased axle, clutch, gearbox, water wheel, windmill, crankshaft)
  - Do: blocks with `entity_renderer` visuals (decided); spinning via interpolated custom elements or `better_model` elements; water wheel/windmill BetterModel models (TFC `waterwheel.bbmodel`, `WindmillBlade*.bbmodel`, with the large cubes fixed).
  - Done when: power flows; visual checklist.
  - Status: pure logic ported in `core/power/` (`WaterWheel`, `Windmill`, `WindSpeed`, `Transmission`, `ShaftAxis`) on top of `core/rotation` (`SourceSpeed.ramp`, `RotationNode.source`). Verified against TFC @ c3b4d9536: water wheel MAX_SPEED 2pi/80, LERP_SPEED MAX/400, MAX_FLOW 10, 40-tick sample (WaterWheelBlockEntity 33-36, 44), flow scan and sign rule (127-163), ramp (77-79), obstruction (69-70, 151); windmill MIN 2pi/400, MAX 2pi/160-MIN, LERP MIN/100 (34-37), targetBeforeWind with one blade = 0 (73), wind cap min(wind,0.5)*4 (77), disk radius 7 (93-114); wind intensity draws, sea level minus 6, threshold 0.23, altitude 128, predictable floor 0.08 (OverworldClimateModel 311-368); clutch connections (ClutchBlockEntity 45-57), axis faces (Node.ofAxis). Tests: `WaterWheelGoldenTest` (10), `WindmillGoldenTest` (7), `WindSpeedGoldenTest` (8), `TransmissionTest` (5, incl. clutch cutting power in the solver), 30 passed. Open: the source and block-entity glue (40-tick scheduling, reading the flow and solid window and blade inventory from the world, feeding the wind draws from the climate seeded random); the Paper weather and climate facts (rain state, wind at a position); animal power is not a source at this TFC commit (no harness or animal rotation code), so nothing to port; the CraftEngine visuals and BetterModel models (spin and blade count states); the GUI or readout.

- [ ] **M6-MECH-03 Machines** · deps: M6-MECH-02, M3-PROC-01, M3-PROC-02
  - TFC: `common/blockentities/rotation/{TripHammer,PowerLoom,Pump}BlockEntity.java`, `common/blocks/rotation/FluidPumpBlock.java`, `FluidPipeBlock`
  - Do: automated quern, trip hammer on anvil, power loom, pumps/pipes.
  - Done when: each machine is self-tested.

## Glassworking

- [~] **M6-GLASS-01 Glassworking** · deps: M4-FORGE-01 · skills: tfc-feature-porting
  - TFC: `common/component/glass/`, `common/items/{Blowpipe,GlassBlowpipe,Glassworking}Item.java`, `common/blockentities/{GlassBasin,HotPouredGlass}BlockEntity.java`, recipes `glassworking/`, `network/` (glassblowing overlays → our GUI/HUD)
  - Do: blowpipe operations via a textured GUI or action bar steps (divergence if needed), basins, poured glass.
  - Done when: all 60 glassworking recipes.
  - Status: pure core ported in `dev.tfp.core.glass` (`GlassOperation`, `GlassBatches`, `GlassRecipe(s)`, `GlassState`, `GlassRules`) at c3b4d9536. Counts verified: 60 of 60 glassworking recipe files, 60 golden rows, 0 overlapping recipes, batch tags expanded (4 tags). Tests: `GlassGoldenTest` (4) and `GlassRulesTest` (12), all passing. Open: the glass basin and hot-poured glass block-entity glue (the 100-tick settle and the TFC block states), the blowpipe use/finish interaction and its GUI or action-bar steps, blow breakage chance, the CraftEngine glass items and visuals, the item glue, and any Paper tick or lazy trigger.

## Decoration & building families

- [~] **M6-DECO-01 Wood families** · deps: M1-DGN-04 · skills: craftengine-blocks, craftengine-config-generation
  - TFC: `common/blocks/wood/` (stairs, slabs, fences, gates, doors, trapdoors, buttons, plates, signs, hanging signs, chests, trapped chests, bookshelves, lecterns, tool racks, shelves, barrel racks, scribing/sewing tables, looms, sluices)
  - Do: our own `tfp:block_state/slab|stairs|wall` templates (CE defaults only allow 5 slabs and 4 stairs), renderer-based fences/walls, signs via vanilla sign entities where possible.
  - Done when: all wood blocks are placeable with correct shapes; showcase.
  - Status: `tools/datagen` `WoodFamilies` writes `craftengine/tfp/families/wood.yml` (outside `configuration/`, so CraftEngine does not load it). Verified on c3b4d9536: 20 woods from `Wood.java`, 39 blocks each (21 kinds once per wood, plus hanging and wall hanging signs per 9 metals) = 780 blocks, every blockstate checked against the checkout. Tests: `WoodFamiliesTest` (`realCheckoutHasTwentyWoodsWithTheSameBlockKinds`, `fixtureFamiliesHaveEveryKindAndMetalAndTheYamlMatches`, `missingBlockstateOfAFamilyIsRefused`); all 41 datagen tests pass. Open: renderer-based fences and walls, the sign entities, the block logic, the CE visuals check (`tfp:block_state` slab/stairs templates), the GUI tables; the generator skips silently when a checkout has no wood blockstates (kept for the existing DatagenTest fixture, to be made loud once that fixture gains the BlockType data); the barrel rack is one block, not per wood, so it is not in the families.

- [~] **M6-DECO-02 Rock & mud families** · deps: M6-DECO-01
  - TFC: `common/blocks/rock/` (stairs/slabs/walls of every rock variant, aqueducts, spikes, rope anchors), mud bricks, alabaster, wattle (+ stained), thatch
  - Done when: showcase; visual budget still within the profile reserves.
  - Status: data slice in datagen: `RockFamilies` writes `craftengine/tfp/families/rock.yml` (21 rocks; 17 `Rock.BlockType` kinds, the 7 with variants also slab/stairs/wall, plus anvil and magma on the 7 igneous rocks: 14x38 + 7x40 = 812 blocks) and `MudFamilies` writes `mud.yml` (8 soils x mud_bricks full/slab/stairs/wall = 32 blocks). Verified against the pinned checkout by `RockMudFamiliesTest.realCheckoutHasTwentyOneRocksAndEightMudBrickSoils` (incl. folder file counts), with fixture tests `fixtureFamiliesHaveTheRightKindsShapesAndYaml`, `missingRockSourceIsRefused`, `missingMudSourceAndMudBricksConstantAreRefused`, `missingRockBlockstateIsRefused`, `missingMudBlockstateIsRefused`, `unknownRockBlockstateFolderIsRefused`, `blockKindMissingFromTheEnumIsRefused`. Open: the visuals and the block logic, the CE block-entry generator, the stair/slab/wall template check, the GUI tables, the raw mud and smooth mud bricks (not per soil; left out), and the visual budget.

- [~] **M6-DECO-03 Metal decoration & misc** · deps: M4-MET-01
  - TFC: metal bars/chains/trapdoors/lamps/grates, candles, cakes, buoy, bells, jack-o'-lanterns, crates, powderkeg
  - Done when: showcase.
  - Status: `tools/datagen` `MetalFamilies` writes `craftengine/tfp/families/metal.yml` (data only): 19 metals with the `Metal.BlockType` kinds that TFC's `has` rule allows: block (full, slab, stairs) for all 19; exposed, weathered and oxidized block (each with slab and stairs) for the 7 weathering metals; grate for the 9 tiered metals, with its 3 weathered ages for the 4 that weather; bars, chain, lamp and trapdoor for the 9 tiered metals. 177 blocks, checked against the 13 blockstate folders (186 files = 177 + 9 anvils). The anvil is left out (M4-ANV-02) and the 9 ingot-only metals have no block. TFC has no metal wall or sheet block. Tests: `MetalFamiliesTest` (10, incl. the real checkout and the missing-data refusals), green. Open: CraftEngine visuals (appearances, `entity_renderer` for lamp and chain), block logic (lamp lit state and light, trapdoor, chain light), template checks, GUI tables, and the misc items not generated (candles, cakes, buoy, bells, jack-o'-lanterns, crates, powderkeg).

- [~] **M6-DECO-04 Fruit trees** · deps: M3-FARM-03, M2-GEN-12
  - TFC: `common/blocks/plant/fruit/` (growing branches, seasonal leaves)
  - Do: branch growth decided async from the calendar; seasonal leaf states.
  - Done when: lifecycle golden tests.
  - Status: datagen `tools/datagen/.../FruitFamilies.java` writes `craftengine/tfp/families/fruit_tree.yml` (checked-in content verified with `verifyContent`): 8 `FruitBlocks.Tree` species × 5 kinds = 40 blocks (`leaves`, `branch`, `growing_branch`, `sapling`, `potted_sapling`; 16 with a block item: leaves and saplings; banana is a separate plant, left out). Kinds and paths are parsed from `TFCBlocks` and every blockstate is checked. Tests: `FruitFamiliesTest` (7: real checkout species, kind order, item flags, 40 paths, YAML; refused on missing source, blocks source, registration, empty enum, blockstate). Open: the CE block entries and visuals, the block logic and growth glue (lifecycle golden tests), the fruit food items, the GUI tables.

## Content completion

- [~] **M6-FLUID-01 Fluids in containers & in-world divergence** · deps: M3-PROC-03
  - TFC: `common/fluids/`
  - Do: all fluids as container contents; in-world placement policy (vanilla visuals or disallowed, per the ledger); river-water flow push server-side.
  - Done when: ledger divergence rows final.
  - Status: `core/fluid/` ports the container table (`FluidContainerKind`: 12 kinds, default capacities from `ServerConfig`, usable tag or code predicate), the single-tank fill and drain (`FluidContainerRules`, the over-capacity fill is a ledger divergence), river water (`RiverWaterRules`: family, amount 8, height, source flow, flow vector) and the item-to-world/block decision table (`FluidTransferRules`). Verified: 12 capacities and the 11 `usable_in_*` tags (`usable_in_tags.tsv`, 11 files = 11 rows). Tests: `FluidContainerKindTest` (5), `FluidContainerRulesTest` (15, incl. seeded conservation), `RiverWaterRulesTest` (11), `FluidTransferRulesTest` (6): 37 passing. Open: the container block and block-entity glue, Paper fluid handling (buckets, `FluidHelpers` on the server), the in-world river flow divergence on the server, the CraftEngine visuals, the GUI.

- [ ] **M6-ADV-01 Advancements** · deps: most features
  - TFC: `src/main/resources/data/tfc/advancement/`, `util/advancements/`
  - Do: shipped via our datapack; custom triggers fired from our systems.
  - Done when: TFC's advancement tree is obtainable.

- [ ] **M6-CMD-01 Remaining commands** · deps: —
  - TFC: `common/commands/` (`AddTrimCommand`, `ClearWorldCommand`, `CountBlockCommand`, `PlayerCommand`, `WeatherCommand`)
  - Done when: parity with TFC commands (namespaced under `/tfp`).

- [ ] **M6-GUIDE-01 Field guide** · deps: most features · skills: craftengine-gui-furniture, tfc-asset-migration
  - TFC: Patchouli templates `src/main/templates/data/tfc/patchouli_books/field_guide`, `resources/generate_book.py`, generated `src/generated/resources/assets/tfc/field_guide`
  - Do: decide the form (textured GUI book, written book, or web guide; PRD open question). Convert the content via datagen, with all locales.
  - Done when: the guide covers every playable feature.

## Release

- [ ] **M6-PERF-01 Performance audit** · deps: all · skills: plugin-testing
  - Do: scripted load test (50 players/bots, pre-generated world, animals), spark profiles on Paper and Folia; verify the PRD budgets (≤ 2 ms/tick plugin share target); fix regressions.
  - Done when: the report is in `docs/perf/`; budgets are met or documented.

- [ ] **M6-QA-01 Full playthrough & visual QA** · deps: all
  - Do: human playthrough stone → steel → husbandry on 1.21.11 and 26.3 clients; visual checklist for every family; Bedrock/Geyser decision applied.
  - Done when: all blockers are fixed.

- [ ] **M6-REL-01 Release packaging** · deps: M6-QA-01
  - Do: final product name/namespace/package (PRD open question), NOTICE review, legal review of EUPL vs GPL dependencies, docs for server owners (install, config, visual profiles), Modrinth/Hangar listing, version 1.0.0.
  - Done when: released.
