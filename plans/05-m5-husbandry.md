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

- [~] **M5-LIV-03 Livestock roster** · deps: M5-LIV-02, M5-ENT-02
  - TFC: `common/entities/TFCEntities.java` (cow, yak, goat, alpaca, sheep, musk ox, pig, chicken, duck, quail, turkey, peafowl, ...)
  - Done when: each species is spawnable, breedable and produces as TFC; showcase pen.
  - Status (@claude): roster is 18 domestic species (the `ServerConfig` animal rows, lines 718-769); turkey and peafowl are wild winged prey, outside the roster. `RosterConsistencyTest` (5 cases, `core/src/test/java/dev/tfp/core/livestock/`) checks the species row, the age rule, the gestation or hatch rule, the product cooldown against the `ServerConfig` produce hours (10 producers) and the spawn row in the golden. Gaps: none in the species table or the product rules. Breeding: mule has no breeding rule by design (`TFCMule.canMate` is false). Spawn: mule, cat and dog have no biome spawner row in TFC, so the test asserts they stay unspawned. Open: the Paper spawn registration, the breeding and produce interactions on the server, the BetterModel models for each species, the showcase pen.

## Wildlife

- [~] **M5-WILD-01 Prey AI** · deps: M5-ENT-01
  - TFC: `common/entities/prey/`, `common/entities/ai/prey/`
  - Done when: flee/graze behaviour self-tests; roster spawns.
  - Status (@claude): pure flee rules done in `core/prey/`: `PreyFlee` (trigger: nearest avoided sighting while not fleeing, with no distance check in TFC; the stop rule: memory expired or squared distance strictly above 400; retreat 140 to 440 player ticks; hurt switches the threat only while fleeing from another type; same-type friends take the nearer threat; flee target 15 blocks away with a drawn scale and angle), `FleeState`, `Sighting`, `Position`. Cases verified against TFC lines in `PreyFleeGoldenTest` (18: the constants, the boar and bear triggers at near and far distances, the creative player and the cow not triggering, the stop at exactly 20 blocks, the expiry at 1140, the hurt and broadcast cases) and `PreyFleeTest` (5 property checks). 23 pass. No ServerConfig number applies (the flee constants are literals in TFC). Open: the goal AI and navigation (the walk away target from vanilla `DefaultRandomPos`, the 1.1 speed, graze and idle movement), the Paper entity events (hurt glue, the friend list from the sensor, tag lookups), the BetterModel flee animation, the sound.

- [~] **M5-WILD-02 Predator AI** · deps: M5-ENT-01
  - TFC: `common/entities/predator/`, `common/entities/ai/predator/`
  - Do: hunting, home range, sleeping by day/night per TFC, attack animations.
  - Done when: predator behaviour self-tests.
  - Status (@claude): pure rules done in `core/predator/`: `PredatorSchedule` (DIURNAL: hunt from 0, rest from 11000; NOCTURNAL the reverse, day time mod 24000), `HuntedPrey` (the 26 types of `hunted_by_land_predators`), `PreyCandidate`, `PredatorMemory` (expiring memories, sleep, attack, unreachable since), `PredatorRules` (prey choice: rest gate, baby and pacified eligibility, attackable player first, then the nearest huntable prey not in water; low health below 5 pacifies 200 ticks; give-up at more than 80 blocks from home, unreachable above 200 ticks, dead target sets hunted-recently 12000; sleep and wake at 5 blocks, new home beyond 100 blocks, hurt by a living attacker, nearby attacker under 16 blocks). Cases verified against TFC lines in `PredatorScheduleGoldenTest` (4), `HuntedPreyGoldenTest` (6), `PredatorRulesGoldenTest` (16) and `PredatorGiveUpGoldenTest` (19): 45 pass. Config: no predator numbers come from `ServerConfig`. Open: the goal AI and navigation (home wander, stroll, sleep pose), the pack alpha and respect (`PackPredatorAi`, `PackLeaderSensor`), the feline and crocodile attack reach (`FelinePredator.attackDistanceSquared` is stored but unused at c3b4d9536), the Paper entity events and the sensing range (vanilla follow range, not in the TFC tree), the BetterModel attack and sleep animations, the attack and sleep sounds.

- [ ] **M5-WILD-03 Aquatic** · deps: M5-ENT-01
  - TFC: `common/entities/aquatic/`, `common/entities/ai/amphibian/`
  - Do: fish (fresh/salt), jellyfish, squid, seals, turtles, penguins; carrier water pathing.
  - Done when: spawn and swim checks.

- [ ] **M5-WILD-04 Pests & critters** · deps: M5-ENT-01
  - TFC: `common/entities/prey/Pest.java`, `common/container/PestContainer.java`, `common/entities/aquatic/AquaticCritter.java`
  - Done when: pests raid containers per TFC.

## Pets & mounts

- [~] **M5-PET-01 Dogs & cats with commands** · deps: M5-LIV-01
  - TFC: `common/entities/livestock/pet/`, `common/entities/ai/pet/`, `network/PetCommandPacket.java`
  - Do: commands via a vanilla trigger (sneak + right-click opens a small GUI) replacing the packet.
  - Done when: command self-tests.
  - Status (@claude): pure rules done in `core/pet/`: `PetCommand` (RELAX, HOME, SIT, FOLLOW, HUNT in TFC order, saved ordinal), `PetActivity`, `PetSpecies` (cat refuses a SIT with roll below 0.1), `PetState` (command, sitting, sleeping, SIT tick, home), `PetRules` (server gate then owner check then the command transition, the menu, SIT timeout after more than 2000 ticks or a hurt pet, stop-sitting to idle at home or idle, taming when familiarity + 0.06 > 0.15 with no owner, food and hunger, the sneak-open menu gesture, the petFlags byte). Cases verified against TFC lines in `PetCommandGoldenTest` (7) and `PetRulesGoldenTest` (17): 24 pass. Config: no pet numbers come from `ServerConfig` (its cat and dog rows only set the familiarity cap, not used here). Open: the goal AI and navigation (follow, sit and stay pose, home wander), the flee and owner-lost transitions, the mount and interaction handlers on Paper, the BetterModel visuals and sit pose parts, the command item and key glue, the purr and collar dye.

- [~] **M5-MNT-01 Horses, donkeys, mules, camels** · deps: M5-LIV-01, M0-BM-01
  - TFC: `common/entities/livestock/horse/`, `camel/`, `client/animation/BactrianCamelAnimation.java`
  - Do: vanilla mount carriers (no BetterModel seats), TFC breeding/familiarity, chests on donkeys/mules, seat height tuned.
  - Done when: ride/breed self-tests; visual check of rider alignment.
  - Status (@claude): pure rules done in `core/mount/`: `MountStats` (health 15 to 30, jump 0.4 to 1.0, speed 0.1125 to 0.3375 as floats, spawn draws, TFC's offspring attribute reflection), `MountGenes` (foal species: horse+donkey gives a mule either way, the stats inherit, the colour inherits for a horse foal), `HorseColour` (variant and markings draws in TFC order), `MountRules` (taming above 0.15, old mounts at half speed, babies not mounted, dromedary attributes), `MountBreeding` (horse-family pairs need tamed and unridden, camels mate only with their own kind, mules never). Constants checked against ServerConfig rows for horse, donkey, mule, dromedary and bactrian. Tests: `MountStatsGoldenTest` (6), `MountInheritanceGoldenTest` (8), `MountBreedingGoldenTest` (10), `MountRulesGoldenTest` (6): 30 pass. No generated data folder, so no TSV. Open: the mount entity and attribute glue, the saddle and riding controls, the BetterModel visuals, the riding AI, the item and loot glue (chest and carried items, overburden weight), the genetic size shared with M5-LIV, and the vanilla `Horse.Variant` (7) and `Markings` (5) counts (from Minecraft 1.21.1, not in the TFC tree, not verified here).

## Hunting & fishing

- [~] **M5-HUNT-01 Javelins, hides & meat yields** · deps: M5-WILD-01, M4-TOOL-02
  - TFC: `common/entities/misc/ThrownJavelin.java`, `common/items/HideItemType.java`, `util/loot/AnimalYieldProvider.java`
  - Done when: golden yield tests (age/familiarity based).
  - Status (@claude): pure rules done. `core/hunt/`: `HuntDrops` (all 83 TFC animal and fish loot tables at c3b4d9536, 158 drop entries; yields rolled in TFC order), `HuntDrop`/`HuntCount`/`HuntCondition`/`HuntKill` (uniform and `tfc:animal_yield` counts, not-predated, is-male, killed-by-player, counts-as-player-kill, random chance; size scale from genetic size, player attack and familiarity), `HuntJavelin` (charge 10 ticks, one durability per throw, no break roll in TFC, thrown damage 1.5 x tier bonus). Golden: `HuntDropsGoldenTest` against `hunt_drops_golden.tsv` (83 species, 165 rows: 158 drops + 7 empty species; every row's drop and its yield for three probe kills). Unit tests: `HuntDropsTest`, `HuntJavelinTest`. 19 tests pass. Age is not an input: TFC's yield reads genetic size, familiarity and player attack only. Open: the prey AI (M5-WILD-01), the javelin and drop glue on Paper (entity, loot on death, butcher interaction), the item model and CE items for meat, hides and the javelin.; open: the loot-count integer read uses floor, the reading of vanilla NumberProvider that is not verified against the Minecraft source

- [~] **M5-FISH-01 Fishing & lobster traps** · deps: M5-WILD-03
  - TFC: `common/entities/misc/TFCFishingHook.java`, `common/items/TFCFishingRodItem.java`, `common/component/Bait*.java`, `common/blockentities/LobsterTrapBlockEntity.java`
  - Done when: bait/catch golden tests; lazy trap catch.
  - Status: pure core ported in `core/.../fishing/` (`BaitType`, `BaitRules` for rod loading, hook bait and eating, `FishingRod` for pull strength and cast gate, `Hook` for pull exhaustion, the break at 100, the damage and the pull velocity, `LobsterTrap` for bait, the 6-slot catch, the 10% junk roll and the trap state; the loot rolls reuse `HuntDrops`). Verified against the 23 TFC loot tables of `small_fish` and `caught_in_lobster_traps` (`fishing_loot_golden.tsv`: 23 files, 23 entries, total weight 1 each, trap tag and hunt drops agree) in `FishingLootGoldenTest`; `FishingRulesTest` (14) and `LobsterTrapTest` (12); 30 tests pass. TFC has no trap timer (a mob's AI enters and catches on arrival), so there is no lazy timer to port. Open: the rod and trap item and block glue, the water and biome facts on Paper (the infinite-water tag for the hook, the rod's tier speed), the loot roll on Paper, the trap block and CE visuals, the bait and hook AI glue on Paper.

## Exit criteria
The "husbandry run" self-test passes (tame → breed → collect products → slaughter for yields). All fauna models are reviewed. Spawn tables match TFC. No region-thread spikes from AI under a 200-animal load test (spark profile in `docs/perf/`).
