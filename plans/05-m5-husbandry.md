# M5: Husbandry

Goal: TFC fauna on vanilla clients. Each animal is a vanilla carrier mob + a BetterModel model + our AI and lazy calendar state. Exit: livestock can be raised and bred for products; wildlife spawns by climate; predators, prey and fish behave per TFC; mounts work.

Paths are relative to `src/main/java/net/dries007/tfc/` unless noted. Models: the `model-authoring` skill (generators, headless render, the TFC Java animation converter).

## Entity framework

- [ ] **M5-ENT-01 Animal framework** · deps: M1-INT-03, M2-CAL-01, M0-BM-01 · skills: bettermodel, paper-folia-threading
  - TFC: `common/entities/livestock/TFCAnimal.java`, `CommonAnimalData.java`, `Age.java`, `Gender.java`, `common/entities/EntityHelpers.java`
  - Do:
    - a carrier choice table per family; `setSilent(true)` with our sounds;
    - entity PDC state: birth day, gender, familiarity, last fed, pregnancy, variant;
    - lazy age/stage from the calendar;
    - the model attached on the entity scheduler; visual state re-applied in `CreateEntityTrackerEvent`; baby scale from age;
    - throttled, staggered Mob Goals.
  - Done when: golden aging tests; self-test persistence across chunk reload, `/bm reload` and restart (variant/tint/parts kept).

- [ ] **M5-ENT-02 Model import & conversion pipeline** · deps: M5-ENT-01 · skills: model-authoring, tfc-asset-migration
  - TFC: `resources/entity_models/*.bbmodel` (44), `client/model/entity/*Model.java` and `client/animation/*.java` (128 animations)
  - Do: a datagen step:
    1. import TFC bbmodels;
    2. convert the Java animations (verified axis rule) and rename them to BetterModel names;
    3. drop stale channels;
    4. validate with `--lenient`;
    5. emit variant models per texture.
    Generator species files for animals without bbmodels (TFC Java-only models → parameters from `createBodyLayer`). Render review for each.
  - Done when: all fauna models validate; contact-sheet renders reviewed; every animal has `idle` + `walk` at least.

- [~] **M5-ENT-03 Spawning by climate** · deps: M5-ENT-01, M2-CLIM-02
  - TFC: data `fauna/*.json`, `common/entities/Faunas.java`, `Fauna.java`
  - Do: spawn rules by climate/months/groundwater/rivers, decided async per chunk generation/load with budgeted spawning.
  - Status: pure core ported in `core/.../spawn/` (`SpawnRules` for the weighted species pick from a biome category list, the group size, and the TFC `Fauna` gate in TFC's draw order: chance, depth below sea level, the `ClimatePlacement` window with the fuzzy draw, months, brightness; plus `FaunaRule`, `SpawnEntry`, `SpawnSite`, `SpawnPick`, `SpawnCategory`). Verified against TFC c3b4d9536: 125 biome files, 5664 spawner entries (5039 tfc, 625 minecraft), 77 fauna files of which 76 are spawned (`tfc:mule` is bred only); golden TSV `core/src/test/resources/dev/tfp/core/spawn/spawn_rules.tsv` (`SpawnDataGoldenTest`: 4 tests on counts and parsing), 15 fixed-draw picks checked against an independent scratch reference (`SpawnPickGoldenTest`), 13 targeted rule tests (`SpawnRulesTest`); 32 tests pass. Open: the Minecraft spawn registration glue (`RegisterSpawnPlacementsEvent` predicate calling core), the Paper-side per-chunk cap (vanilla `NaturalSpawner` caps, not TFC code), the BetterModel entity types, and spawn placement (position search, solid ground, aquatic fluid checks, per-mob predicate in a group). Not in the pure port because TFC's data does not use them: elevation window, rain variance `absolute`, forest types.
  - Done when: spawn distribution tests on fixed worlds.

## Livestock

- [~] **M5-LIV-01 Familiarity, feeding & breeding** · deps: M5-ENT-01
  - TFC: `common/entities/livestock/{Mammal,OviparousAnimal,ProducingAnimal,ProducingMammal}.java`, `config/animals/`
  - Status: pure core ported in `core/.../livestock/` (`AnimalState`, `LivestockRules` for familiarity decay, feeding, hunger and age, `BreedingRules` for mating, fertilization, gestation, birth and eggs, `LivestockSpecies` with the 18 ServerConfig species defaults). Lazy decay is closed-form over the 20-tick check grid. Verified: species table and rule constants (`SpeciesGoldenTest`), decay and feeding and hunger (`FamiliarityGoldenTest`), age and old age (`AgeGoldenTest`), pregnancy, gestation, litter, offspring familiarity, mate readiness, eggs (`BreedingGoldenTest`), lazy-equals-stepping over seeded decay and birth states (`LazyEqualsSteppingTest`); 46 tests pass. Open: the entity framework glue (carrier state in PDC), BetterModel visuals, the Paper tick or lazy trigger, the feeding and breeding interactions, the calendar catch-up on chunk load, the old-tick write timing at the uses event, and the produce fields (M5-LIV-02).
  - Done when: golden familiarity/pregnancy/gestation tests.

- [~] **M5-LIV-02 Products: milk, wool, eggs, nest box** · deps: M5-LIV-01, M3-PROC-03
  - TFC: `common/entities/livestock/{DairyAnimal,WoolyAnimal}.java`, `common/blockentities/NestBoxBlockEntity.java`, `common/component/EggComponent.java`
  - Do: product cooldowns lazy; sheared state as a BetterModel part toggle; egg hatching lazy in the nest box.
  - Status: pure core ported in `core/.../livestock/products/` (`ProductKind`, `ProductSpec` with the ServerConfig produce defaults for the 10 producing species, `ProductState`, `ProductRules` for milk, shearing and the cooldown, `EggRules` for `makeEgg` and the hatch rule, `NestRules` for the lazy nest lay on the 30-tick check). Verified: produce defaults (`ProductSpecGoldenTest`); milk cooldown 28800 with the fresh-cow and float-threshold cases, old and male gating (`ProductCooldownGoldenTest`); wool regrowth at 96h (115200), the 2-wool rule at 0.99f, never-sheared wool, lamb shearing refused until adult (`ProductCooldownGoldenTest`); lay timing on the check grid with the 1-in-7 roll, cooldown blocking the next lay, plain eggs dropped, full nest refused, hatch day 108 for a lay on day 100 with 8 hatch days (`NestLayGoldenTest`); lazy-equals-stepping for readiness and nest lays over seeded states (`ProductLazyEqualsSteppingTest`). 26 new tests; the livestock package passes 72 of 72. Open: the entity and block glue (the carrier's product tick in PDC, calling milk, shear and lay at the uses event, `markElderlyIfDue` after uses change), the BetterModel sheared part toggle and its visual check, the nest box block and its CE visuals, the item glue for eggs and wool, the lazy trigger (nest read and chunk-load catch-up), and feathers from Pluckable (not in this slice).
  - Done when: golden cooldowns; visual check of sheared/unsheared.

- [ ] **M5-LIV-03 Livestock roster** · deps: M5-LIV-02, M5-ENT-02
  - TFC: `common/entities/TFCEntities.java` (cow, yak, goat, alpaca, sheep, musk ox, pig, chicken, duck, quail, turkey, peafowl, ...)
  - Done when: each species is spawnable, breedable and produces as TFC; showcase pen.

## Wildlife

- [ ] **M5-WILD-01 Prey AI** · deps: M5-ENT-01
  - TFC: `common/entities/prey/`, `common/entities/ai/prey/`
  - Done when: flee/graze behaviour self-tests; roster spawns.

- [ ] **M5-WILD-02 Predator AI** · deps: M5-ENT-01
  - TFC: `common/entities/predator/`, `common/entities/ai/predator/`
  - Do: hunting, home range, sleeping by day/night per TFC, attack animations.
  - Done when: predator behaviour self-tests.

- [ ] **M5-WILD-03 Aquatic** · deps: M5-ENT-01
  - TFC: `common/entities/aquatic/`, `common/entities/ai/amphibian/`
  - Do: fish (fresh/salt), jellyfish, squid, seals, turtles, penguins; carrier water pathing.
  - Done when: spawn and swim checks.

- [ ] **M5-WILD-04 Pests & critters** · deps: M5-ENT-01
  - TFC: `common/entities/prey/Pest.java`, `common/container/PestContainer.java`, `common/entities/aquatic/AquaticCritter.java`
  - Done when: pests raid containers per TFC.

## Pets & mounts

- [ ] **M5-PET-01 Dogs & cats with commands** · deps: M5-LIV-01
  - TFC: `common/entities/livestock/pet/`, `common/entities/ai/pet/`, `network/PetCommandPacket.java`
  - Do: commands via a vanilla trigger (sneak + right-click opens a small GUI) replacing the packet.
  - Done when: command self-tests.

- [ ] **M5-MNT-01 Horses, donkeys, mules, camels** · deps: M5-LIV-01, M0-BM-01
  - TFC: `common/entities/livestock/horse/`, `camel/`, `client/animation/BactrianCamelAnimation.java`
  - Do: vanilla mount carriers (no BetterModel seats), TFC breeding/familiarity, chests on donkeys/mules, seat height tuned.
  - Done when: ride/breed self-tests; visual check of rider alignment.

## Hunting & fishing

- [ ] **M5-HUNT-01 Javelins, hides & meat yields** · deps: M5-WILD-01, M4-TOOL-02
  - TFC: `common/entities/misc/ThrownJavelin.java`, `common/items/HideItemType.java`, `util/loot/AnimalYieldProvider.java`
  - Done when: golden yield tests (age/familiarity based).

- [ ] **M5-FISH-01 Fishing & lobster traps** · deps: M5-WILD-03
  - TFC: `common/entities/misc/TFCFishingHook.java`, `common/items/TFCFishingRodItem.java`, `common/component/Bait*.java`, `common/blockentities/LobsterTrapBlockEntity.java`
  - Done when: bait/catch golden tests; lazy trap catch.

## Exit criteria
The "husbandry run" self-test passes (tame → breed → collect products → slaughter for yields). All fauna models are reviewed. Spawn tables match TFC. No region-thread spikes from AI under a 200-animal load test (spark profile in `docs/perf/`).
