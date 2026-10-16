# M6: Parity & release

Goal: everything else TFC has, plus polish to release quality. Exit: every ledger row is `visual-complete` or a documented divergence; v1.0 is released.

Paths are relative to `src/main/java/net/dries007/tfc/` unless noted.

## Mechanical power

- [ ] **M6-MECH-01 Rotation network solver** · deps: M1-THR-03 · skills: paper-folia-threading, tfc-feature-porting
  - TFC: `util/rotation/` (`RotationNetwork`, `RotationNetworkManager`, nodes); test `RotationNetworkTest`
  - Do: port to `core`; recompute networks via the async decision pipeline on topology changes; split networks per region safely on Folia.
  - Done when: `RotationNetworkTest` ported and green; Folia stress self-test.

- [ ] **M6-MECH-02 Power sources & transmission** · deps: M6-MECH-01 · skills: craftengine-gui-furniture
  - TFC: `common/blocks/rotation/`, `common/blockentities/rotation/` (axle, encased axle, clutch, gearbox, water wheel, windmill, crankshaft)
  - Do: blocks with `entity_renderer` visuals (decided); spinning via interpolated custom elements or `better_model` elements; water wheel/windmill BetterModel models (TFC `waterwheel.bbmodel`, `WindmillBlade*.bbmodel`, with the large cubes fixed).
  - Done when: power flows; visual checklist.

- [ ] **M6-MECH-03 Machines** · deps: M6-MECH-02, M3-PROC-01, M3-PROC-02
  - TFC: `common/blockentities/rotation/{TripHammer,PowerLoom,Pump}BlockEntity.java`, `common/blocks/rotation/FluidPumpBlock.java`, `FluidPipeBlock`
  - Do: automated quern, trip hammer on anvil, power loom, pumps/pipes.
  - Done when: each machine is self-tested.

## Glassworking

- [ ] **M6-GLASS-01 Glassworking** · deps: M4-FORGE-01 · skills: tfc-feature-porting
  - TFC: `common/component/glass/`, `common/items/{Blowpipe,GlassBlowpipe,Glassworking}Item.java`, `common/blockentities/{GlassBasin,HotPouredGlass}BlockEntity.java`, recipes `glassworking/`, `network/` (glassblowing overlays → our GUI/HUD)
  - Do: blowpipe operations via a textured GUI or action bar steps (divergence if needed), basins, poured glass.
  - Done when: all 60 glassworking recipes.

## Decoration & building families

- [ ] **M6-DECO-01 Wood families** · deps: M1-DGN-04 · skills: craftengine-blocks, craftengine-config-generation
  - TFC: `common/blocks/wood/` (stairs, slabs, fences, gates, doors, trapdoors, buttons, plates, signs, hanging signs, chests, trapped chests, bookshelves, lecterns, tool racks, shelves, barrel racks, scribing/sewing tables, looms, sluices)
  - Do: our own `tfp:block_state/slab|stairs|wall` templates (CE defaults only allow 5 slabs and 4 stairs), renderer-based fences/walls, signs via vanilla sign entities where possible.
  - Done when: all wood blocks are placeable with correct shapes; showcase.

- [ ] **M6-DECO-02 Rock & mud families** · deps: M6-DECO-01
  - TFC: `common/blocks/rock/` (stairs/slabs/walls of every rock variant, aqueducts, spikes, rope anchors), mud bricks, alabaster, wattle (+ stained), thatch
  - Done when: showcase; visual budget still within the profile reserves.

- [ ] **M6-DECO-03 Metal decoration & misc** · deps: M4-MET-01
  - TFC: metal bars/chains/trapdoors/lamps/grates, candles, cakes, buoy, bells, jack-o'-lanterns, crates, powderkeg
  - Done when: showcase.

- [ ] **M6-DECO-04 Fruit trees** · deps: M3-FARM-03, M2-GEN-12
  - TFC: `common/blocks/plant/fruit/` (growing branches, seasonal leaves)
  - Do: branch growth decided async from the calendar; seasonal leaf states.
  - Done when: lifecycle golden tests.

## Content completion

- [ ] **M6-FLUID-01 Fluids in containers & in-world divergence** · deps: M3-PROC-03
  - TFC: `common/fluids/`
  - Do: all fluids as container contents; in-world placement policy (vanilla visuals or disallowed, per the ledger); river-water flow push server-side.
  - Done when: ledger divergence rows final.

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
