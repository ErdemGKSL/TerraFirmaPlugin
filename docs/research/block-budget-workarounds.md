# Full-cube visual budget: vanilla supply inventory and workaround survey

Scope: Paper/Folia server with CraftEngine (CE) Community, vanilla clients 1.21.4 to 1.21.11 and 26.x. Every TFC full-cube block (rock, ~1113 ore×rock, soil, wood) needs an opaque full-cube vanilla **visual state**. The CE `solid` pool (note_block + mushroom) has 1289 states (1.21.x) or 1489 (26.1+). This report counts what else exists, and surveys how other projects get around the limit.

Evidence tags: **[JAR]** decoded from the Mojang client jar. **[MCMETA]** misode/mcmeta server reports. **[SRC]** CE source b3079c5. **[TFC]** TFC source clone. **[WEB]** URL. **[CALC]** arithmetic in this report. **[INF]** inference, not tested in game.

## Table of contents
0. [Headline numbers](#0-headline-numbers)
1. [Method](#1-method)
2. [Supply inventory (A)](#2-supply-inventory-a)
   - 2.1 [Per-category totals](#21-per-category-totals)
   - 2.2 [Identity duplicates](#22-identity-duplicates)
   - 2.3 [Absent-in-TFC-world blocks](#23-absent-in-tfc-world-blocks)
   - 2.4 [Nether/End blocks](#24-netherend-blocks)
   - 2.5 [Avoid list and caveats, with evidence](#25-avoid-list-and-caveats-with-evidence)
   - 2.6 [Render layers ≤1.21.11 (decoded)](#26-render-layers-12111-decoded)
   - 2.7 [Things CE already handles](#27-things-ce-already-handles)
3. [Demand side: what TFC actually needs](#3-demand-side-what-tfc-actually-needs)
4. [Workaround survey (B)](#4-workaround-survey-b)
5. [Evaluation of the three candidate ideas](#5-evaluation-of-the-three-candidate-ideas)
   - 5.1 [Exposed-only ore overlay](#51-exposed-only-ore-overlay)
   - 5.2 [Leaves states as opaque cubes](#52-leaves-states-as-opaque-cubes)
   - 5.3 [Rotated/orientable blocks](#53-rotatedorientable-blocks)
6. [Recommendation](#6-recommendation)
7. [Sources and scripts](#7-sources-and-scripts)
8. [UNVERIFIED / open questions](#8-unverified--open-questions)

---

## 0. Headline numbers

The tables below count **extra** opaque full-cube states. States that CE's default `mappings.yml` already touches, as source or as target, are excluded: note_block, the three mushroom blocks, 9 leaves, copper grates, petrified/oak slab and the cut-copper slabs, 1630 states on 1.21.x, 1830 on 26.1.2 and 1914 on 26.3.

**"Terrain-grade"** means a state with no client side effect, or only a minor one (see §2.5).

| Category | 1.21.4 | 1.21.11 | 26.1.2 | 26.3 |
|---|---|---|---|---|
| 1. Identity duplicates (safe even if real vanilla blocks exist) | **126** | **126** | **126** | **165** |
| 2. Absent-in-TFC-world (vanilla instances must be mapped away) | **471** | **475** | **475** | **496** |
| 3. Nether/End (needs no vanilla Nether/End) | **67** | **67** | **67** | **67** |
| **Total terrain-grade extra supply** | **664** | **668** | **668** | **728** |
| + CE `solid` pool | 1289 | 1289 | 1489 | 1489 |
| **= opaque full-cube budget** | **1953** | **1957** | **2157** | **2217** |

Not terrain-grade, listed but not counted above:

| | 1.21.4 | 1.21.11 | 26.1.2 | 26.3 |
|---|---|---|---|---|
| Identity dups with a major caveat (waterlogged double slabs 52 (26.3: 91), spruce/birch leaf variants 52, dispenser/dropper/crafter `triggered` 24, jukebox 1) | 129 | 129 | 129 | 168 |
| Absent blocks with a major caveat (interactable workstations, falling blocks, leaves canonical states, mycelium, wet sponge) | 115 | 115 | 115 | 115 |
| Avoid (light, movement, BE renderers, glass, redstone ore): identity / absent / nether | 22 / 114 / 9 | 25 / 118 / 9 | 25 / 118 / 9 | 29 / 119 / 9 |
| Present in a TFC world (bedrock, snow_block, ice family, powder snow, snow) | 7 | 7 | 7 | 7 |

Checksum: CE-used + all categories = every full-cube state: 2690 (1.21.4), 2701 (1.21.11), 2901 (26.1.2), 3089 (26.3). [CALC, `research/fc/totals.json`]

Per-block data: `research/vanilla-fullcube-supply.csv` (540 rows, every block with at least one full-cube state, all four versions).

---

## 1. Method

1. **States.** Property sets per block come from `misode/mcmeta` `<ver>-summary/blocks/data.min.json`. It is generated from the vanilla server data generator, so it covers properties that the blockstate JSON hides (`target[power]`, `redstone_ore[lit]`, …). The 26.3 file has 1286 blocks; this version also removed `chain` and added sulfur, cinnabar, poplar, and wool/concrete slabs and stairs. [MCMETA, URLs in §7]
2. **Model per state.** Every combination of property values is matched against `assets/minecraft/blockstates/<b>.json` in the client jar, using variant partial-key matching and multipart `when`/`OR`/`AND`/`a|b`. Models are resolved through their parent chain. [JAR, `scripts/fullcube.py`]
3. **Full cube.**
   - All six boundary faces must be drawn by an unrotated element that spans 0..16 on the two in-plane axes, after the variant's `x`/`y` rotation. This rotation step is needed for mushroom blocks, whose parts are single-face templates.
   - No element may leave 0..16.
   - Result: 482 / 484 / 484 / 540 blocks have at least one full-cube state. Partial blocks are slabs (`type=double` only), piston and sticky piston (`extended=false`) and snow (`layers=8`).
   - `chiseled_bookshelf` fails the test because its front is assembled from 6 slot elements. It is visually full, but interactable on the front face, so it was left out.
4. **Identity signature.** A SHA-1 over the resolved elements, textures and variant `x/y/z/uvlock/weight`. This is stricter than comparing model names. Two states count as identity duplicates only if the signatures are equal.
   - Leaves and mangrove roots are an exception. For these the `waterlogged=true` states are kept distinct, because the fluid renderer draws visible water inside non-occluding blocks.
   - Cross-block duplicates are checked only for `waxed_*`, `infested_*` and `*_slab[type=double]` against a canonical non-slab, non-waxed and non-infested owner.
5. **CE exclusion.** Each version-conditional line of the internal `mappings.yml` is evaluated (sources **and** targets). A target must keep its vanilla look, so it cannot be repurposed either. [SRC `common-files/src/main/resources/resources/internal/configuration/mappings.yml`]
6. **Client behaviour (26.3 unobfuscated jar).**
   - `Blocks.<clinit>` was parsed to find the Block class of every block, including classes built inside lambdas, and its `Properties` calls: `lightLevel`, `friction`, `speedFactor`, `jumpFactor`, `noOcclusion`, …
   - Every class in `net/minecraft/world/level/block` was checked for overrides of `animateTick`, `useWithoutItem`, `useItemOn`, `stepOn`, `fallOn`, `entityInside`, `skipRendering`, `getLightDampening`, `newBlockEntity`/`getTicker`, `getCollisionShape`, …
   - Source: [JAR, `scripts/blockprops.py`, output `research/fc/blockprops_26.3.json`].
   - Known parser slips were corrected by hand. Blocks registered right after a "collection" register helper are attributed the previous class (copper_ore, dripstone_block, red_sandstone, terracotta). Copper and concrete-powder blocks are registered through `WeatheringCopperCollection`/`ColorCollection` and are missing from the parse.
   - Light emitters were cross-checked against PrismarineJS minecraft-data `emitLight` for 1.21.4 and 1.21.11. [WEB]
7. **Render layer ≤1.21.11.**
   - `ItemBlockRenderTypes` static map, decoded from the obfuscated jar with Mojang's `client_mappings`.
   - 1.21.4: `gls` → `RenderType` methods. 1.21.11: `hoe` → `ChunkSectionLayer` enum.
   - Source: [JAR, `scripts/rendertypes.py`, outputs `research/fc/rendertypes_<ver>.txt`].
8. **Client-interactable set.** Taken from CE's own list (`InteractUtils`), which is what CE uses to compensate. [SRC]

---

## 2. Supply inventory (A)

### 2.1 Per-category totals

Full breakdown (states). c = clean, m = minor caveat, M = major caveat, a = avoid.

| Version | CE-used | Identity c/m/M/a | Absent c/m/M/a | Nether-End c/m/M/a | TFC-present c/a |
|---|---|---|---|---|---|
| 1.21.4 | 1630 | 90 / 36 / 129 / 22 | 299 / 172 / 115 / 114 | 54 / 13 / 0 / 9 | 2 / 5 |
| 1.21.11 | 1630 | 90 / 36 / 129 / 25 | 299 / 176 / 115 / 118 | 54 / 13 / 0 / 9 | 2 / 5 |
| 26.1.2 | 1830 | 90 / 36 / 129 / 25 | 299 / 176 / 115 / 118 | 54 / 13 / 0 / 9 | 2 / 5 |
| 26.3 | 1914 | 129 / 36 / 168 / 29 | 314 / 182 / 115 / 119 | 54 / 13 / 0 / 9 | 2 / 5 |

- **1.21.4 → 1.21.11:** creaking_heart goes from 12 to 18 states, and the copper bulbs move into the lit/avoid split. These are the +4 absent and +3 identity-avoid states.
- **26.1:** adds no full cubes, only note_block instruments, which CE uses.
- **26.3:** adds the cinnabar and sulfur families, poplar log/wood/planks, the 32 wool/concrete double slabs (cross-block duplicates of wool and concrete) and potent_sulfur (avoid: geyser BE tickers).

### 2.2 Identity duplicates

You can free these with `block_state_mappings` from the duplicate to its canonical twin. Real vanilla instances keep the same look. Terrain-grade (clean + minor) on 1.21.11 is **126**:

| Group | States freed | Maps to | Client behaviour of the freed state |
|---|---|---|---|
| `beehive`, `bee_nest` `honey_level=1..4` × 4 facings | 16 + 16 = 32 | `honey_level=0` same facing | clean. honey_level 5 is a different model, kept. Interaction only at level 5 [SRC InteractUtils L146–158]. |
| `target[power=1..15]` | 15 | `power=0` | minor: vanilla redstone dust next to it renders as connected. |
| `tnt[unstable=true]` | 1 | `unstable=false` | minor: flint & steel interaction. |
| Double slabs `type=double,waterlogged=false` whose model is identical to a full block (planks, stone family, bricks, sandstone, prismarine, quartz, purpur, tuff, …) | 49 (1.21.x) / 88 (26.3: +32 wool/concrete, +poplar/cinnabar/sulfur) | e.g. `oak_slab[type=double,waterlogged=false]` → `oak_planks` | clean. Hardness and SoundType are equal for these pairs. CE handles breaking anyway (§2.7). |
| `infested_*` (stone, cobblestone, 4 stone bricks, deepslate × 3 axis) | 9 | base block | clean. Client break-time prediction differs, but CE drives breaking (§2.7). |
| `waxed_*` copper full blocks (copper_block, exposed/weathered/oxidized, cut, chiseled) | 12 | unwaxed twin | minor: an axe or honeycomb is predicted client-side (§2.5). |
| `waxed_*_copper_bulb[lit=false]` | 8 | unwaxed bulb | minor (same). The `lit=true` variants emit light (avoid). |
| **Not terrain-grade** | | | |
| Double slab `waterlogged=true` (52; 26.3: 91) | | `waterlogged=false` | **major**: the client thinks it holds water, so drip particles appear under it over air. Fixable: add the block to the server-synced `#minecraft:impermeable` tag (§4 row T). |
| `spruce_leaves`, `birch_leaves` (13 per waterlogged value = 26 each, 52 total). CE leaves these out because the colours are fixed. | | | **major**: leaves behaviour (§5.2). |
| `dispenser`/`dropper[triggered=true]` (6 + 6), `crafter[triggered]` (12) | | | **major**: client-interactable. |
| `jukebox[has_record=true]` | | | **major**: interactable. |
| `redstone_ore`/`deepslate_redstone_ore[lit=true]`, `creaking_heart[natural]` (9), `trial_spawner[ominous…]` (6) | | | **avoid** (light / BE ticker). |

- **Model-vs-render caveat.** Identity is judged on block models. A few properties also change non-model client behaviour (fluid, light, interaction), and these were classified per state. `redstone_ore[lit]` uses one model but `lit=true` emits light 9, so it is "avoid".
- **No identity duplicates** among the plain stone, ore, wool, concrete or terracotta blocks. They have 1 state each.

### 2.3 Absent-in-TFC-world blocks

**Terrain-grade on 1.21.11: 475** (299 clean + 176 minor). Usable only after every real vanilla instance is hidden. Either map it to some other pure-vanilla state that no custom block uses, or accept that a stray vanilla `stone` would look like the TFC block using that visual. In a TFC world there should be none.

These blocks must also never be created by vanilla mechanics; see the CSV `notes`:
- lava + water → cobblestone, stone, obsidian;
- basalt generator;
- concrete powder + water;
- copper oxidation;
- grass/mycelium spread;
- crafting recipes.

Intercept `BlockFormEvent`/`BlockSpreadEvent` and remove the recipes.

Largest clean families (1.21.11, states):

| Family | States | Notes |
|---|---|---|
| Glazed terracotta 16 × 4 facing | 64 | clean; per-state model override, so facing does not matter (§5.3) |
| Wool 16, concrete 16 | 32 | clean |
| Terracotta 17 | 17 | **minor**: ambient desert dry-vegetation sounds since 1.21.5 (tag `triggers_ambient_desert_dry_vegetation_block_sounds` includes `#terracotta`) [JAR 26.3 `data/minecraft/tags/block/*.json`, `AmbientDesertBlockSoundsPlayer`] |
| Stripped logs/wood 10 species (11 on 26.3) × 2 × 3 axes | 60 (66) | clean |
| Logs/wood (non-stripped) × 3 axes | 60 (66) + bamboo_block 3 | **minor**: axe stripping is predicted client-side (§2.5) |
| Planks | 10–11 | clean (the slab doubles of these are identity dups, §2.2) |
| Stone family (stone, granite, diorite, andesite + polished, tuff/calcite/dripstone, deepslate family, stone bricks family, cobble, mossy cobble, smooth stone, sandstone family, bricks, mud bricks, packed mud, resin) | ~60 | clean |
| Overworld ores (coal, iron, copper, gold, emerald, lapis, diamond + deepslate variants), raw ore blocks, mineral blocks | ~25 | clean (redstone ores are avoid) |
| Coral blocks live/dead | 10 | clean (live → dead conversion is a server tick only) |
| piston/sticky piston `extended=false` × 6 facings | 12 | clean |
| observer (12), command blocks (36), jigsaw (12), test_block (4) | 64 | minor: op/creative GUI only, redstone dust connects to the observer |
| hay_block, bone_block, muddy_mangrove_roots (3 axes each), carved_pumpkin (4), melon, sponge, dried kelp, honeycomb block, moss, pale moss, sculk, clay, dirt, coarse/rooted dirt, bookshelf, fletching_table, reinforced_deepslate | ~35 | clean. fletching_table is a plain `Block` in 26.3 [JAR] and not in CE's interactable list [SRC]. |
| grass_block, podzol (snowy × 2) | 6 | minor: the client predicts `snowy` from neighbour updates |

Major-caveat absent blocks (115): interactable workstations (crafting/cartography/smithing table, loom, furnace family `lit=false`, barrel 12, dispenser/dropper 6+6, crafter 36), falling blocks (sand, red sand, gravel, 16 concrete powders, suspicious sand/gravel 4+4: dust particles under them over air), mycelium (constant spore particles), wet_sponge (drips), canonical spruce/birch leaves states, mangrove_roots.

### 2.4 Nether/End blocks

**67 terrain-grade** in every version: netherrack, nether bricks ×4, red nether bricks, nether/warped wart blocks, nylium ×2, crimson/warped planks, stems/hyphae (+stripped) × 3 axes, soul soil, basalt/polished basalt × 3 axes, the blackstone family, nether gold/quartz ore, ancient debris, netherite block, lodestone, the quartz family, end stone/bricks, purpur block/pillar.

You need no vanilla Nether/End dimension and no recipes or bartering that create them.

Avoid: glowstone, shroomlight, magma (light 3 + `emissiveRendering`), crying obsidian (light 10 + particles), soul sand (speed 0.4, 14 px collision), respawn anchor when charged.

### 2.5 Avoid list and caveats, with evidence

| Effect | Blocks | Evidence |
|---|---|---|
| **Client light emission** | glowstone, sea_lantern, shroomlight, 3 froglights, jack_o_lantern, magma_block, crying_obsidian, beacon, sculk_catalyst, vault, trial_spawner; `lit=true` of redstone_lamp, furnace/smoker/blast_furnace, redstone ores, all copper bulbs; respawn_anchor `charges>0` | `Properties.lightLevel` in `Blocks.<clinit>` [JAR 26.3]; PrismarineJS `emitLight` [WEB] |
| **Movement** | ice/packed/blue/frosted ice (`friction` 0.98/0.989), slime (friction 0.8 + `fallOn`/`stepOn` bounce), honey (`speedFactor` 0.4, `jumpFactor` 0.5, `entityInside`), soul_sand (`speedFactor` 0.4, own collision shape), mud (`MudBlock.getCollisionShape` 14 px), powder_snow (`entityInside`, own collision), snow `layers=8` (`SnowLayerBlock` collision) | [JAR 26.3 class overrides + Properties floats] |
| **BE renderers / client tickers** | spawner, trial_spawner, vault, beacon, creaking_heart (`animateTick` + ticker), potent_sulfur (26.3: `CLIENT_GEYSER_PLUME_TICKER`, `CLIENT_NOXIOUS_GAS_TICKER`, `SULFUR_BUBBLES` particles), structure_block/test_instance_block | [JAR 26.3 `PotentSulfurBlock`] |
| **Glass family** | glass, tinted glass, 16 stained glass | `noOcclusion`, `propagatesSkylightDown`, translucent (1.21.11) / cutout (1.21.4, glass only). Neighbour faces are not culled and the client light engine lets light through → mismatch with the server's opaque CE block. |
| **Client prediction of `lit`** | redstone_ore, deepslate_redstone_ore | `RedStoneOreBlock` overrides `stepOn`, `attack`, `useItemOn`, `animateTick` [JAR] |
| Client-interactable: **major** for terrain, because the client does not predict placement against the face | crafting/cartography/smithing tables, loom, furnaces, barrel, dispenser, dropper, crafter, jukebox (with record), chiseled bookshelf (front), beehive (level 5 + shears/bottle), respawn anchor (glowstone/charged), command blocks/jigsaw/structure/test (op+creative), TNT (flint), redstone ore | CE `InteractUtils` L69–700. CE detects these on the *visual* state, cancels the vanilla item use, simulates it and resends swing/sound (`ItemEventListener` L340–520) [SRC]. Placement still happens server-side but is not predicted, so there is a one-RTT delay. |
| Item-tool prediction (**minor**) | logs, wood, stems, hyphae, bamboo_block (axe strip); copper blocks (axe scrape/wax-off, honeycomb wax) | `AxeItem.useOn` (1.21.11 `djy`) calls `Level.setBlock` with no `isClientSide` guard; `HoneycombItem.useOn` (26.3) does the same. `ShovelItem`/`HoeItem` are guarded (`dwo.B_()`) [JAR]. Effect: with a vanilla-axe-based tool, the client briefly shows the stripped/scraped state's visual (which may be another custom block) until the server update arrives. Avoided if TFC axes are not based on vanilla axe items. [INF] |
| Falling-block dust (**major** for rock) | sand, red_sand, gravel, concrete powders, suspicious sand/gravel | `FallingBlock.animateTick` [JAR] |
| Ambient sounds (**minor**, tag-fixable) | sand, red_sand, terracotta ×17 (1.21.5+); soul_sand/soul_soil only next to a dried ghast | `AmbientDesertBlockSoundsPlayer` reads `#triggers_ambient_desert_*` [JAR 26.3] |
| Leaves | all leaves | `LeavesBlock`: `getLightDampening` = 1, `skipRendering` only vs other leaves and only when `cutoutLeaves` is off (1.21.11+), `animateTick` (rain drips, falling-leaf particles via `TintedParticleLeavesBlock`/`UntintedParticleLeavesBlock`), 26.x `AmbientLeavesBlockSoundPlayer` [JAR] |
| Waterlogged states | double slabs, leaves, mangrove roots | `ClientLevel.trySpawnDripParticles`: if the fluid state is non-empty and the block is not in `#impermeable`, drips spawn under it when the block below is not full [JAR 26.3] |
| Neighbour-shape prediction | grass_block/podzol/mycelium (`snowy`), concrete powder (→ concrete next to water), mushroom blocks (faces false next to the same block; CE pool), creaking_heart | client `updateShape` runs on predicted placements [INF from vanilla code paths] |

**Tags are server-authoritative.** Block tags are synced to the client, so a datapack can remove the vanilla sand/terracotta from the ambient-sound tags, remove amethyst from `#crystal_sound_blocks`, and add waterlogged-visual blocks to `#impermeable`. This turns several "minor/major" items into clean ones. The server-side effect is limited to vanilla blocks, which are absent in a TFC world anyway. [INF; tag names verified in JAR 26.3 `data/minecraft/tags/block/`]

### 2.6 Render layers ≤1.21.11 (decoded)

| | 1.21.4 (`RenderType`) | 1.21.11 (`ChunkSectionLayer`) |
|---|---|---|
| cutout | 263 blocks incl. **glass**, spawner, trial_spawner, vault, beacon, copper grates | **CUTOUT 289**: the old cutout and cutout_mipped merged, so leaves, grass_block and mangrove_roots are here too |
| cutout_mipped | 18: all leaves, grass_block, mangrove_roots | (merged into CUTOUT) |
| translucent | 39: stained glass, tinted glass, ice, frosted ice, slime, honey | **TRANSLUCENT 42**: **glass moved here** |
| solid | everything else (all stone, wool, concrete, terracotta, logs, note_block, mushroom blocks) | same |

- Leaves are special-cased in `getChunkRenderType`: with "cutout leaves" off (fast), they render SOLID.
- **26.x:** the layer is chosen per quad from texture transparency (prior report §3.7).
- **Consequence for TFC ores on ≤1.21.11.** TFC ore models are `parent: tfc:block/ore` with `all` = rock and an `overlay` texture [TFC `assets/tfc/models/block/ore/*/*.json`]. On a SOLID-layer visual (note_block, stone, wool…) the overlay's transparent pixels do not discard, so they render as their RGB.
  - Fix: **flatten** rock + overlay into one 16×16 texture per ore×rock in datagen. 1113 small textures is acceptable.
  - [INF from the solid shader having no alpha discard; verify]

### 2.7 Things CE already handles

| Item | What CE does | Source |
|---|---|---|
| **Hardness of the visual is irrelevant** | For custom blocks CE sets the client `block_break_speed` attribute / mining fatigue and drives break progress server-side | [SRC `BukkitServerPlayer` L947–1092] |
| **Interactable visuals** | Compensated (see §2.5) | [SRC `ItemEventListener` L340–520, `InteractUtils`] |
| **Sound** | The visual's whole SoundType is silenced and replayed | prior report §3.4 |

So bedrock, reinforced deepslate and command blocks (unbreakable or very hard client-side) are usable visuals.

---

## 3. Demand side: what TFC actually needs

[TFC, counts from `assets/tfc/blockstates`; INF for totals]

**Ores.** `blockstates/ore/` has 65 entries:
- 12 graded metals × {poor, normal, rich}: 36 full-cube ore types;
- 12 `small_*` (loose pieces, not cubes);
- 17 non-graded minerals;
- 53 full-cube ore types × 21 rocks = **1113** states.

**Hardened rock = raw rock.** `rock/hardened/tuff.json` points at `tfc:block/rock/raw/tuff` (+ mirrored) exactly like `rock/raw/tuff.json`. **Hardened rock needs no extra visual.** It should share raw rock's state or model. The `axis` property and the random mirrored variants can be reproduced in the one state's override, because a variant list of weighted models is legal.

Rough remaining full-cube demand (INF):
- rock: 21 × (raw, cobble, mossy cobble, smooth, bricks, cracked/mossy bricks, chiseled, gravel) ≈ 190;
- sand and sandstone: ≈ 32;
- soils: ≈ 40;
- wood: 19 species × (log, stripped log, wood, stripped wood) × 3 axes + planks ≈ 250.

With ores that is **≈ 1600–1650** opaque full cubes, against the 1289 solid pool. The extra supply (668 on 1.21.11, 728 on 26.3) covers the gap, with ~300 left over. Leaves stay in their own pool.

**Biggest demand-side lever:** if poor/normal/rich share one texture per ore×rock (or differ only in loose-rock/propick feedback), ore demand drops from 1113 to 609. This is a design divergence; record it in the ledger.

---

## 4. Workaround survey (B)

| # | Technique | Who uses it | States / capacity | Client cost and caveats | Source |
|---|---|---|---|---|---|
| A | **note_block states** | CE (solid pool), Polymer `FULL_BLOCK`, ItemsAdder `REAL_NOTE`, Oraxen/Nexo NoteBlock, Crucible | 1150 states (1.21.x) / 1350 (26.1+). Advertised: Polymer 1149, Nexo "up to 1149", ItemsAdder **750**, Oraxen "~800" | Solid layer. Instrument/neighbour prediction must be patched (CE flag `isClientSideNoteBlock`; Paper `disable-noteblock-updates`) | [SRC BlockGenerator L68–84]; [WEB polymer.pb4.eu polymer-blocks/basics; docs.nexomc.com noteblock-mechanic; wiki.itemsadder.com/adding-content/blocks; docs.oraxen.com] |
| B | **mushroom blocks** (3 × 64) | CE (189 in pool), ItemsAdder `REAL` (191), Crucible | 189–192 | Multipart. Neighbour-face prediction. Natural worldgen of huge mushrooms ("glitched blocks") | [WEB wiki.itemsadder.com, itemsadder.devs.beer/faq/glitched-blocks] |
| C | **tripwire** | CE, Polymer `TRIPWIRE_BLOCK(_FLAT)`, ItemsAdder `REAL_WIRE` (127), Oraxen/Nexo StringBlock | 126–128 | Tiny or no hitbox, cutout. Disables tripwire redstone | [WEB as above] |
| D | **chorus_plant** (63) | ItemsAdder `REAL_TRANSPARENT`, Oraxen/Nexo ChorusBlock | 63 | Odd hitbox, transparency OK. CE pool size is **0** (chorus is unused in CE defaults) | [WEB docs.nexomc.com chorusblock-mechanic] |
| E | **leaves** distance × persistent × waterlogged | CE leaves pool (117+117), Polymer `TRANSPARENT_BLOCK`/`BIOME_TRANSPARENT_BLOCK` | 28 per type | §5.2 | [WEB Polymer DefaultModelData.java] |
| F | **other identity-duplicate pools**: anvil facing, sapling stage, cave/weeping/twisting vines age, kelp, sugar cane, cactus, farmland moisture 2–6, sculk sensor power, doors/trapdoors (powered), copper slabs | CE `mappings.yml`, Polymer (`FARMLAND_BLOCK` 5, slabs 5, doors 168 per direction) | see prior report §1.5 | Shape of the source block (not full cube) | [SRC mappings.yml; WEB Polymer DefaultModelData.java] |
| G | **Explicit repurposing of vanilla full cubes** (this inventory) | CE explicit `state:` | +664…728 terrain-grade | The real vanilla blocks must be mapped away. One model per state | §2 |
| H | **Display entity on an empty or barrier state** ("furniture-like blocks") | CE `entity_renderer` + `transparent`/barrier, Polymer `requestEmpty` + virtual entities, Filament (Fabric, Polymer-based), ItemsAdder/Oraxen/Nexo furniture | unlimited | One or more entities per block. Polymer docs warn of "much higher client performance cost". CE Community does **no** culling (Premium only). Lighting is sampled at one point (§5.1) | [WEB polymer.pb4.eu; modrinth filament]; [SRC config.yml L812–842; prior report renderers §1.9] |
| I | **Block-entity renderer abuse**: ItemsAdder `TILE` = spawner rendering a custom-model entity | ItemsAdder | "infinite" | "Textures disappear at a distance, revealing the vanilla spawner texture"; lag with many in view; the ItemsAdder docs say do not use it for ores or worldgen | [WEB wiki.itemsadder.com/adding-content/blocks] |
| J | **Data-driven registries with renderers**: banner patterns, pot sherds, painting variants | none for full blocks | unlimited textures | Not full cubes (banner and pot shapes). Not applicable to terrain | [INF] |
| K | **Per-player or per-region resource packs**: rebind the same states to different models per region | none known for terrain | ×N per pack set | 1.20.3+ supports several stacked packs and removal by UUID. Every change triggers a full client resource reload (loading screen, seconds). Only workable at rare boundaries (world change). Not practical for TFC rock regions | [WEB minecraft.net 1.20.3-pre1; docs.papermc.io/adventure/resource-pack] |
| L | **Core-shader tricks** (select a texture by vertex colour / biome tint / section-local position) | used for HUD (BetterHud), rarely for terrain | theoretically ×N | Shared core shaders conflict with BetterHud. Sodium/Iris replace the terrain pipeline and ignore core terrain shaders. Biome is per 4×4×4 cell, not per block. Shader format changed in 1.21.5+ and 26.x. **Not recommended** | [INF] |
| M | **Biome-tint tricks**: tinted greyscale model, colour from biome | grass/leaves only | per 4×4×4 cell | Only blocks with a client colour provider (grass_block, most leaves) tint. Our biome-palette rewrite could encode a colour, but not per block | [INF] |
| N | **Orientable states** (facing/axis) | CE (any state), Polymer doors | already counted in the state totals | See §5.3: there is no multiplication beyond the state count | §5.3 |
| O | **Hide non-exposed ores (anti-xray style)** | Paper anti-xray `engine-mode: 1` | 0 states saved by itself | Sends hidden ores as host rock. Only exposed ores need a distinct look | [WEB docs.papermc.io/paper/anti-xray] |
| P | **Exposed-only overlay entity** (candidate) | our idea; similar to Polymer/Filament display blocks but conditional | ores need 0 states | §5.1 | §5.1 |
| Q | **Double-slab → full-block mapping** | not seen elsewhere | 49 (1.21.x) / 88 (26.3) clean | Real double slabs look identical | §2.2 |
| R | **Datapack tag edits** to silence client-side side effects (`#impermeable`, ambient-sound tags, `#crystal_sound_blocks`, `#climbable`) | CE mentions tweaking the vine tag (mappings.yml L246) | turns caveat states into clean ones | Server-side effect on the same vanilla blocks | [SRC mappings.yml L246; JAR tags] |
| S | **Merge visually redundant TFC variants**: hardened = raw (already identical in TFC); optionally merge ore grades | – | −504 states if grades merge | Divergence from TFC | §3 |
| T | **Flattened textures** (no cutout overlay) | – | 0 | Required for two-layer ore models on solid-layer visuals ≤1.21.11 | §2.6 |

Summary of others' limits: ItemsAdder caps REAL_NOTE 750, REAL 191, REAL_TRANSPARENT 63, REAL_WIRE 127, FIRE 14, TILE unlimited. Oraxen: NoteBlock ~800, StringBlock 127, ChorusBlock 63, furniture unlimited. Polymer: full blocks 1149 (note block only), and after exhaustion `requestEmpty` + display entities. **None of them repurposes vanilla full cubes beyond note/mushroom.** CE's explicit `state:` plus the mapping approach is the only one that scales to the ~660–730 extra states found here.

---

## 5. Evaluation of the three candidate ideas

### 5.1 Exposed-only ore overlay

**Idea.** A hidden ore block uses the host rock's visual. An `exposed` state property toggles an `entity_renderer` item_display that shows only the ore overlay.

**For:**
- Ore visual states drop from 1113 to 0. Ores then share their rock's visual (`exposed=false`) and their rock's exposed visual (`exposed=true`). The state needs no distinct appearance, because CE allows several custom states to share one visual.
- Natural anti-xray: hidden ores are byte-identical to rock in chunk packets.
- CE supports exactly this:
  - `entity_renderer` per appearance;
  - a state change morphs the element with one metadata packet, or adds it;
  - `position`, `translation` and `brightness` are configurable [SRC `ItemDisplayBlockEntityElementConfig` L40–249];
  - renderers are stored per chunk and spawned when the chunk is sent (`LevelChunkWithLightListener` L243) [prior report].

**Against / costs:**

1. **Lighting inside an opaque block → black.**
   - Entity light is sampled at a single `BlockPos.containing(getLightProbePosition())`, and `getLightProbePosition` returns `getEyePosition`, which for a display is its position [JAR 26.3 `EntityRenderer.getPackedLightCoords`, `Entity.getLightProbePosition`]. CE's default `position` is the block centre (0.5, 0.5, 0.5), inside the opaque visual, where the client stores sky and block light 0. The overlay therefore renders at minimum brightness, i.e. black. [JAR + INF on light values inside opaque blocks]
   - Fix 1: a `brightness` override. Fixed light means it ignores day/night and torches, so it is wrong in caves.
   - Fix 2 (recommended): **one element per exposed face**, with `position` in the adjacent air cell (e.g. `0.5, 1.02, 0.5` for the up face) and `translation` back to the face. Light is then sampled exactly where vanilla flat lighting samples a face (the neighbour in the face direction).
   - Residual mismatch: no smooth lighting/AO gradient, and entity diffuse shading differs from block face shading. Mitigate with `shade: false` model faces and per-direction pre-darkened textures (×0.8 N/S, ×0.6 E/W, ×0.5 down) [INF].
   - `DisplayRenderer` uses the override only when it is set (`getBrightnessOverride`) [JAR].
2. **Z-fighting.**
   - Depth resolution with near plane 0.05 and 24-bit or float depth: Δz ≈ z²/(0.05·2²⁴) [CALC]. That is ≈0.0003 blocks at 16 m, 0.0012 at 32 m and 0.0049 at 64 m.
   - A scale-1.001 cube offsets faces by 0.0005 and z-fights beyond ≈20 m.
   - Use face quads offset **0.01** (OK to ≈90 m) or 0.02 (≈130 m) outward. Overlays are mostly thin ore specks, so a 0.01-block gap is invisible.
3. **Entity count and packets.**
   - CE **Community** has no culling: "shows every renderer of every chunk sent to the player". Culling, the 64-block view distance and spawn rate limiting are Premium only [SRC Config L851; config.yml L812–842].
   - Per exposed face: one AddEntity + one SetEntityData on chunk send, and 1 metadata packet per morph.
   - Exposure estimate [INF/CALC, not measured]:
     - TFC veins are large; assume an average of 200–600 ore blocks per chunk below the surface;
     - assume 3–8% of underground solid blocks touch cave air;
     - that gives **≈6–50 exposed ores per chunk**, ×1.3–2 exposed faces each.
   - At view distance 10 (441 chunks) that is **≈3 k–40 k display entities per player**. That is acceptable at the low end and FPS-hostile at the high end. Vanilla handles a few thousand displays well; tens of thousands cost noticeably.
   - Mitigations:
     - our own dynamic `BlockEntityElement` with distance gating (show within N chunks);
     - one element per ore (not per face) where lighting allows;
     - restrict overlays to cave-exposed faces, not the surface.
   - **Measure with our worldgen** (count ores with ≥1 air/water neighbour per chunk) before deciding.
4. **Pop-in and consistency.**
   - Distant cliffs show no ores beyond the gating distance. Surface outcrops are a common TFC prospecting cue, so consider always showing them.
   - The `exposed` state must update on neighbour changes (mining, collapse, landslide) on the region thread. It is cheap with CE `place(state.with(exposed))`, but every mined block touches up to 6 neighbours.
5. **Mining feel.** The ore texture is on an entity, so CE's destroy-stage overlay, the block-outline highlight and particles use the rock visual. Breaking particles show rock, not ore [INF].

**Verdict:** viable as a **fallback for low-volume ores** (rare minerals: gems, borax, cryolite…). Risky as the primary path for all 1113 ores on Community CE because of entity counts. Prefer real states (§6), and keep the overlay for overflow.

### 5.2 Leaves states as opaque cubes

The leaves pool is already counted separately: 117 non-waterlogged + 117 waterlogged (CE), + 26 spruce/birch extra per waterlogged value.

**Rendering:**
- **1.21.4:** cutout_mipped layer with Fancy, SOLID with Fast. An opaque texture looks identical in both. `LeavesBlock` has **no** `skipRendering` in 1.21.4 [mappings], so every leaves face next to another leaves block is drawn.
- **1.21.11 / 26.x:** a `cutoutLeaves` option. When it is off, faces between any two leaves blocks are culled (`skipRendering`: neighbour `instanceof LeavesBlock`). When it is on, they are drawn [JAR 26.3 bytecode].
- On 26.x, opaque textures go to the solid layer per quad.

**Culling against non-leaves:**
- Leaves are `noOcclusion`, so stone faces touching a leaves-visual block are **not** culled.
- A cave wall made of leaves-visual rock therefore renders the hidden faces of every neighbouring block.
- A solid mass of leaves visuals (Fancy / cutout on, or 1.21.4) renders all 6 faces of every block. That is x-ray-level geometry, a large FPS and memory cost.

**Light:**
- Light dampening 1 (`getLightDampening` returns 1) [JAR].
- Client-side relighting after block changes lets sky and block light through leaves-visual "rock": light leaks in caves near player edits.
- This mismatches the server, where the CE block is opaque [INF; CE's light system may resend].

**Other effects:**
- rain-drip particles under the block;
- falling-leaf particles (tinted species via `TintedParticleLeavesBlock`; CE has `remove-tinted-leaves-particle` for 1.21.5+);
- 26.x ambient leaf sounds;
- waterlogged states render visible water.

**Verdict:** acceptable for **sparse** full cubes inside other terrain (e.g. some ores: each costs ≈6 extra hidden neighbour faces) and for genuinely leafy blocks. **Not** acceptable for bulk rock, soil or planks.

### 5.3 Rotated/orientable blocks

- CE writes one variant per **full state string** with the model you give [prior report §3.2]. Each `axis`/`facing` state is therefore an independent slot that can carry an unrelated model. Vanilla's rotation of that state does not apply.
- **Orientation does not multiply anything beyond the state count**, and the state counts above already include every facing and axis state:
  - glazed terracotta 64;
  - logs/wood/stems 3 each;
  - observer 12, piston 12, dispenser/dropper 12, command blocks 12 each, crafter 48, jigsaw 12;
  - carved pumpkin 4, loom 4, …
- **Constraint 1:** if real vanilla instances of the block may exist, their rotated states are *not* identity duplicates. Rotation changes the look, and my signature includes `x`/`y`. They are usable only via category 2 (absent).
- **Constraint 2:** none of these properties changes client-side by itself. Facing/axis are set only at placement, and CE handles placement. `observer[powered]` is server-driven.
- Client side effects come from the block class, not the orientation: dispenser/dropper/crafter are interactable, logs are axe-strippable.
- **Best orientable sources:** glazed terracotta (64, clean), stripped logs/wood (60–66, clean), pistons (12, clean), observer (12, minor), command blocks/jigsaw (48, minor).

---

## 6. Recommendation

1. **Allocate in this order** (1.21.11 numbers):
   1. CE `solid` 1289;
   2. identity duplicates 126 (beehive/bee_nest honey 32, target 15, double-slab → full 49, infested 9, waxed copper 20, tnt 1);
   3. Nether/End 67 (if the server disables those dimensions and recipes);
   4. absent-in-TFC 475, clean before minor. Total **1957** (26.3: **2217**).
2. **Map the real blocks away.** For category 2/3 blocks, emit `block_state_mappings` that point real vanilla instances at a look-alike pure vanilla state, and intercept vanilla mechanics that create them (lava/water, concrete, oxidation, spread, recipes).
3. **Ship a datapack** that edits client-relevant tags: remove repurposed blocks from the ambient-sound tags and `#crystal_sound_blocks`, and add waterlogged double slabs to `#impermeable`. This upgrades ~20 caveat states and makes the 52–91 waterlogged double slabs usable for **blocks that never touch air below** [INF].
4. **Reduce demand:**
   - share the visual between hardened and raw rock (identical in TFC);
   - consider merging ore grades (−504);
   - flatten ore overlays into single textures for solid-layer visuals on ≤1.21.11.
5. **Overflow:** leaves pool for sparse cubes only. Exposed-only overlay for the rarest ores. `transparent` + entity_renderer for anything else rare.
6. **Measure before committing to overlays.** Run a datagen/worldgen probe that counts exposed ore faces per chunk.

---

## 7. Sources and scripts

**Vanilla jars**
- Client jars from piston-meta:
  - 1.21.4 `scratchpad/dl/jar_1.21.4/client.jar`
  - 1.21.11 `dl/jar_1.21.11/client.jar`
  - 26.1.2 `dl/jar_26.1.2/client.jar`
  - **26.3** `dl/jar_26.3/client.jar`, downloaded via `version_manifest_v2.json` → `v_26.3.json`
- Mojang `client_mappings` for 1.21.4 and 1.21.11: `dl/map_<ver>/client.txt`.
- Disassembled classes (`javap`) in `dl/cls_*`:
  - `ItemBlockRenderTypes` (`gls`, `hoe`);
  - `LeavesBlock`;
  - `Blocks` (26.3, verbose);
  - all `net/minecraft/world/level/block/*.class` (26.3);
  - `PotentSulfurBlock`;
  - `AmbientDesertBlockSoundsPlayer`, `AmbientLeavesBlockSoundPlayer`;
  - `ClientLevel.trySpawnDripParticles`;
  - `WaterFluid.animateTick`;
  - `EntityRenderer.getPackedLightCoords`, `Entity.getLightProbePosition`;
  - `DisplayRenderer`;
  - `AxeItem`/`ShovelItem`/`HoeItem` (1.21.11 `djy`/`dmu`/`dlj`);
  - `HoneycombItem` (26.3).
- Tags: `data/minecraft/tags/block/{triggers_ambient_desert_sand_block_sounds,triggers_ambient_desert_dry_vegetation_block_sounds,terracotta,triggers_ambient_dried_ghast_block_sounds}.json` (26.3 jar).

**Block data**
- misode/mcmeta: `https://raw.githubusercontent.com/misode/mcmeta/<ver>-summary/blocks/data.min.json` for `<ver>` ∈ {1.21.4, 1.21.11, 26.1.2, 26.3}, saved in `dl/mcmeta/`.
- PrismarineJS minecraft-data `data/pc/{1.21.4,1.21.11}/blocks.json` (`emitLight`, `transparent`, `filterLight`), saved in `dl/mcdata/`.

**CraftEngine source (b3079c5)**
- `common-files/src/main/resources/resources/internal/configuration/mappings.yml`
- `common-files/src/main/resources/config.yml` L812–842
- `bukkit/.../util/InteractUtils.java` L60–1000
- `bukkit/.../item/listener/ItemEventListener.java` L330–520
- `bukkit/.../plugin/user/BukkitServerPlayer.java` L947–1092
- `bukkit/.../block/entity/renderer/constant/ItemDisplayBlockEntityElementConfig.java`
- `bukkit/.../plugin/injector/BlockGenerator.java` L68–84
- prior notes `research/ce/renderers-furniture-gui.md` §1.9, `research/ce/visuals-and-pack.md`

**TFC**
- `src/tfc/src/main/resources/assets/tfc/blockstates/{rock/raw,rock/hardened,ore}/…`
- `models/block/ore/*/*.json`
- vanilla `Blocks.*` referenced in `src/tfc/src/main/java/net/dries007/tfc/world` (bedrock, ice, packed_ice, blue_ice, snow_block, powder snow, cobweb, hanging roots)

**Web**
- https://wiki.itemsadder.com/adding-content/blocks/ (placed_model caps)
- https://itemsadder.devs.beer/faq/glitched-blocks
- https://docs.nexomc.com/mechanics/custom-block-mechanics, …/noteblock-mechanic, …/chorusblock-mechanic
- https://docs.oraxen.com/creating-content/blocks
- https://polymer.pb4.eu/latest/polymer-blocks/basics/ (1149 full blocks, `requestEmpty` warning)
- https://github.com/Patbox/polymer/blob/dev/1.21.4/polymer-blocks/src/main/java/eu/pb4/polymer/blocks/api/BlockModelType.java and …/impl/DefaultModelData.java
- https://wiki.mythiccraft.io/mythiccrucible/WorldData/Custom-Blocks
- https://modrinth.com/project/filament
- https://docs.papermc.io/paper/anti-xray/
- https://minecraft.wiki/w/Template:Nbt_inherit/display_entity/template (brightness: "When absent, the entity is lit from the light at its own position")
- https://www.minecraft.net/article/minecraft-1-20-3-pre-release-1 and https://docs.papermc.io/adventure/resource-pack/ (multiple packs, removal by UUID)

**Scripts** (`scratchpad/scripts/`, all run with `python3 -I`, paths as args):
- `fullcube.py`: state enumeration, model resolution, full-cube test, signatures, CE exclusion → `research/fc/fc_<ver>.json`
- `rendertypes.py`: decode `ItemBlockRenderTypes` with mappings → `research/fc/rendertypes_<ver>.txt`
- `blockprops.py`: 26.3 `Blocks.<clinit>` + class-override parser → `research/fc/blockprops_26.3.json`
- `classify.py`: categories and behaviour → `research/vanilla-fullcube-supply.csv`, `research/fc/totals.json`

---

## 8. UNVERIFIED / open questions

1. **Not tested in game.**
   - Every client-behaviour claim derived from bytecode: drip particles under waterlogged double slabs, leaf-face culling, interactable-visual placement delay, axe-strip flicker.
   - The idea that tag edits (`#impermeable`, ambient-sound tags) take effect on vanilla clients through tag sync, without other side effects.
2. **Parser coverage.** Classes of 26.3 blocks registered through `WeatheringCopperCollection`/`ColorCollection` were not parsed. Copper (`WeatheringCopperFullBlock`/`Block`) and concrete powder (`ConcretePowderBlock` ← `FallingBlock`) were classified from vanilla knowledge. 1.21.4/1.21.11 behaviour is assumed equal to 26.3, except the version-specific facts checked via mappings (leaves `skipRendering` absent in 1.21.4, `AxeItem` unguarded in 1.21.11).
3. **Exposed-ore statistics** (6–50 per chunk) are an estimate. Measure them with our worldgen.
4. **Overlay entity rendering:**
   - diffuse shading vs block face shading;
   - whether `shade:false` plus pre-darkened textures match well;
   - the exact z-fight distance (depth format differs: 24-bit in 1.21.4, possibly 32F in 26.x);
   - performance at 10k+ item_displays on typical clients.
5. **Solid-layer overlay pixels on ≤1.21.11.** Do transparent overlay pixels render as RGB (needs flattening), or does the vanilla solid pipeline discard? It is believed not to discard.
6. **Client light leaks** with leaves or glass visuals, and whether CE's light system resends server light to correct them.
7. **Neighbour-shape prediction** (grass `snowy`, mushroom faces, concrete powder) on placement next to a visual: whether CE corrects it immediately.
8. **Whether TFC world output contains any other vanilla full cube.** Only `Blocks.*` references in `world/` were scanned; feature JSON may reference more (`minecraft:clay` appears in data, probably as an item).
9. **26.3-only blocks** (cinnabar, sulfur, poplar) are assumed to emit no light and have no client ticks. Only `PotentSulfurBlock` was disassembled. The parser shows plain `Block` for sulfur and cinnabar.
10. **PrismarineJS `emitLight`** reflects default states. State-dependent emitters were added by hand from the `Blocks.litBlockEmission` usage.
