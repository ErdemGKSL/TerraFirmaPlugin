# Ores as exposed-face overlays on the host rock: design

Date: 2026-10-22. Status: **decided by the project owner** (ores move to display-entity overlays; the ladder keeps only
`level-0`, `level-1`, `level-2`). Inputs: `docs/research/ce-block-visual-workarounds-2.md` (option 1, spikes S1/S2),
`docs/research/block-budget-workarounds.md` §5.1, `ARCHITECTURE.md` §9.1, the `craftengine-visuals-pack`,
`craftengine-gui-furniture` and `paper-folia-threading` skills, the TFC checkout at the pin (`/opt/tfc`, `c3b4d9536`).

Backlog: `PAR-WORLD-17` (S1 census and entity budget), `PAR-INFRA-17` (S2, `destroy_stages` and the feel of renderer
cubes), `PAR-INFRA-15` (datagen: host-rock visual, overlay models, allocator always-on rule, delete `level-3a`/`3b`),
`PAR-INFRA-16` (overlay runtime), `PAR-REL-12` (budget guards). Waves 4 to 8 of `plans/parity/README.md`.

**Summary.**
- Every TFC ore block keeps its own CraftEngine (CE) block id and state, its hardness, tool tier, loot and sounds.
- Its appearance is the host rock's raw-rock visual. CE lets many states share one visual, so ores cost **0** visual
  states, and the opaque full-cube demand drops by 1113.
- The ore texture is drawn only where a player can see it: one packet-only `item_display` per **exposed face** (the
  neighbour in that direction is air, fluid or non-occluding). It is placed in the neighbour cell, so it takes that cell's
  light, and it is pulled back to 0.01 in front of the face.
- Buried ore is byte-identical to rock in chunk packets. This is a free anti-xray.
- Expected entity count per player with the default gating (4-chunk send radius, 48-block client cutoff):
  - ≈1.8 k typical;
  - ≈7.8 k worst average (cave-rich mountains);
  - a few hot chunks with ≈450 faces each.
- Without gating at view distance 10: ≈10 k typical, ≈42 k worst. S1 measures the real values.

---

## 1. What every ore-family block becomes

TFC registers ores as `ore/<ore>/<rock>` (17 non-graded) and `ore/<grade>_<ore>/<rock>` (12 metals × poor/normal/rich).
Each one is `Block.Properties.of().mapColor(STONE).sound(SoundType.STONE).strength(rock.category().hardness(6.5f), 10)
.requiresCorrectToolForDrops()` (`common/blocks/rock/Ore.java` `create`). Its model is `tfc:block/ore` (rock cube + overlay
cube, `particle: #all` = the rock) or `tfc:block/ore_column` for shale and claystone. The 53 overlay textures
(`textures/block/ore/<ore>.png`, graded ones per grade) do **not** depend on the rock.

| TFC block family | Count | CE block (server) | Visual state (client) | Ore look | Visual states used |
|---|---|---|---|---|---|
| Graded ores `ore/{poor,normal,rich}_<metal>/<rock>` | 12 × 3 × 21 = 756 | unchanged id, properties, settings (hardness = raw rock of its category, `requiresCorrectToolForDrops`, stone sounds), loot | the host rock's raw unit `tfp:visual/rock/raw/<rock>` (axis_y look, incl. TFC's random mirrored/rotated variants) | overlay item `tfp:ore_overlay/<grade>_<metal>` on exposed faces (36 items) | 0 |
| Non-graded ores `ore/<mineral>/<rock>` (amethyst, borax, cinnabar, cryolite, diamond, emerald, graphite, gypsum, lapis_lazuli, opal, pyrite, ruby, saltpeter, sapphire, sulfur, sylvite, topaz) | 17 × 21 = 357 | unchanged | host raw rock unit | overlay item `tfp:ore_overlay/<mineral>` (17 items) | 0 |
| Columnar hosts (shale, claystone; `tfc:block/ore_column`) | 53 × 2 (inside the rows above) | unchanged | raw shale / raw claystone unit (`cube_column`) | same overlay on all 6 faces (TFC uses `overlay` = `overlay_end`) | 0 |
| Small ores `ore/small_<metal>` (loose pieces, vein indicators) | 12 | unchanged | button carrier from the hitbox selector (PAR-INFRA-13) | its own model and texture, always visible (groundcover) | unchanged (button states) |
| Ore deposits `deposit/<ore>/<rock>` (gravel with nuggets in riverbeds) | 4 × 21 = 84 | unchanged | level-0/1: own visual per rock; level-2: one gravel+specks visual per ore (4) | full block model, no overlay | 84 / 84 / 4 |
| Ore items (graded pieces, gems, powders) | items | PAR-PROC-19 | item models | n/a | 0 |
| Ore block items (creative tab, pick-block) | 1113 items | CE block item | item model = `minecraft:composite` of the raw rock model + the overlay model | rock + ore on all faces | 0 |

Consequences:
- **Flattened ore PNGs go away.** `OreFlattener` output (1113 PNGs) is no longer needed for blocks, and the block items use
  a composite item model. The pack shrinks. ≤1.21.11's per-block render layer is no problem, because the overlay is an
  item model, which supports alpha on every version.
- The overlay set is **53 one-quad item models**, not 53 × 21: the overlay texture does not depend on the rock.
- Grades stay distinct in every profile (TFC parity restored on 1.21.x, where the old default `level-3a` merged poor and
  normal).
- Nothing changes server-side: drops, propick results, collapse recipes (`recipe/collapse/ore/**`: rich → normal → poor),
  tags, tool checks.

## 2. Visual profile ladder after the change (replaces the §9.1 table)

The ore rule is **always on**, next to the other always-on rules:
- metal and mechanical blocks via `entity_renderer`;
- hardened = raw;
- groundcover on button states;
- leaves pool only for leaves.

Flattened ore textures are no longer an always-on rule; they are dropped (§1). `level-2`'s gem-only overlay is generalised
to all ores and removed from the `level-2` row. `level-3a` and `level-3b` only merged ore grades, so they now change
nothing and are **removed** (owner decision).

Demand, all of TFC (the §9.1 figures minus the 1113 ore visuals; at `level-2` the 189 gem visuals were already gone):

| Profile | Adds (cumulative) | Gameplay impact | Demand |
|---|---|---|---|
| `level-0` | nothing | none | **768** |
| `level-1` | crate/workbench → renderer (−40); wood & stripped_wood axes share one visual (−80) | none | **648** |
| `level-2` | deposits rock-agnostic (84 → 4) | deposits look the same in every rock | **568** |

Solid-pool slots left for other CE packs. The non-pool sources (identity duplicates and absent-in-TFC states, plus
Nether/End states when used) are taken first; the solid pool only covers the rest. Values are N/E blocks not used
(N/E blocks used: the server has no Nether/End).

| Profile | 1.21.11 (pool 1289, explicit 601 / 668) | 26.3 (pool 1489, explicit 661 / 728) | shipped 1.21 family (explicit 592 / 659) | shipped 26 family (explicit 596 / 663) |
|---|---|---|---|---|
| `level-0` | 1122 (1189) | 1382 (1449) | 1113 (1180) | 1317 (1384) |
| `level-1` | 1242 (1289) | 1489 (1489) | 1233 (1289) | 1437 (1489) |
| `level-2` | 1289 (1289) | 1489 (1489) | 1289 (1289) | 1489 (1489) |

"Shipped family" uses the allocator's supply: a state counts only if it is free in every researched version of the family
(1.21.4 + 1.21.11, 26.1.2 + 26.3).

- **Spare explicit states** where the demand is below the explicit supply:
  - `level-1`: 20 on 1.21.11 with N/E blocks used; 13 / 80 on 26.3;
  - `level-2`: 33 / 100 on 1.21.11; 93 / 160 on 26.3.
- **Defaults (proposed): `level-2` on both 1.21.x and 26.x.** It fits everywhere with the whole solid pool free.
- `level-0` (maximum fidelity) also fits everywhere and leaves ≥1113 slots.
- `level-1` is now a small step (−120) between them. It stays because the owner keeps three levels, but it is the first
  candidate to merge if the ladder is simplified later.
- What datagen emits today (rock, ore, soil, sand families): 1374 visuals at `level-0`, of which 1113 are ores. After the
  change it is **261 visuals in every profile** (no wood, deposit, crate or workbench family is emitted yet). The solid
  pool stays unused; only 261 of the 592/596 explicit states are needed.
- **Optional allocator tweak** (PAR-INFRA-15 decides, with a test): with this much room, the allocator may take the solid
  pool **before** the `absent_minor` states while ≥1000 solid slots stay free. That suppresses fewer vanilla blocks and
  avoids the minor-caveat states. It is off by default until decided.

**Config migration.** `visuals.profile` accepts only `level-0`, `level-1` and `level-2`.
- `level-3a` / `level-3b` in an existing config are read as `level-2`, with a startup warning that names the file and key
  ("level-3a was removed: ores are drawn as overlays and grades no longer cost visual states; using level-2"). This is not
  silent: it is logged at every start until the owner edits the key.
- The config edit protection (`edits.json`) is untouched; we never rewrite the owner's value.

**Install-time check (`ProfileCheck`).** `level-2` is now the highest profile. If the selected profile does not fit the
real CE pool counts, the message names the next higher profile that fits. If even `level-2` does not fit (other CE packs
took the pool), startup fails with a clear message:
- the shortfall per pool;
- that no higher profile exists;
- the options: remove or shrink other CE packs, set `use-nether-end-blocks` if the dimensions are off, or clean CE's
  stale visual cache (`/ce clean-cache visual_block_states`).

It never degrades silently.

## 3. Rendering the overlay

### 3.1 Placement, model and shading
- **One entity per exposed face** (chosen over one entity per ore block):
  - light is sampled at one point per entity, so a multi-face entity would light every face from one neighbour cell;
  - per-face entities light each face exactly like vanilla flat lighting (the neighbour cell in the face direction);
  - the cost is ≈1.5 entities per exposed ore instead of 1 (§4).
- **Entity:** `item_display`, position = centre of the neighbour cell (block centre + normal), translation = −normal ×
  (0.5 − 0.01), so the quad sits 0.01 in front of the face.
  - 0.01 z-fights only beyond ≈90 m (round 1 §5.1), and the client cutoff is ≤64 m.
  - The rotation per face is a fixed quaternion. No `brightness` override: the light is sampled in the air cell.
- **Model:** one item model per overlay (53). It is a single 16×16 quad (the 1.21.4+ `minecraft:model` item definition)
  with `shade: false` and `tintindex 0`.
  - The tint source is `custom_model_data` colour 0, set per entity to the vanilla face shade: up 1.0, north/south 0.8,
    east/west 0.6, down 0.5 (as `0xFFFFFF`, `0xCCCCCC`, `0x999999`, `0x808080`). This matches block face shading exactly
    without extra textures.
  - The UV orientation per face reproduces the block model's default face UVs, so specks line up with TFC's look on every
    side. A golden test renders the 6 faces against the TFC model.
- `view_range` is set so that the client cutoff is ≈48 m (§3.4).

### 3.2 Packet-level overlay, not CE's `entity_renderer`
- CE renderers are fixed per appearance. A dynamic exposed-face set would need either 63 face-combination appearances
  per ore state (≈70 k extra server states, far above `block.serverside-blocks`), or a CE block entity per ore (persisted
  data and memory for ≈300 blocks per chunk).
- The overlay is therefore our own **packet-only** layer in `paper/oreoverlay` + `paper/packet/display`:
  - no Bukkit entities, nothing persisted, no server tick cost;
  - entity ids come from the server's id counter.
- CE Premium culling does not apply to it, so we implement the parts we need ourselves:
  - distance gating (§3.4);
  - per-player rate limiting: a token bucket like Premium's (1000 burst, 25 per tick refill);
  - a per-player cap (default 8000, nearest first).

### 3.3 Data flow (decide async, apply sync; Folia)
| Stage | Thread | What happens |
|---|---|---|
| Chunk sent to a player (`CHUNK_DATA` send) | Netty | Look up the chunk's immutable `OreOverlayTable`. If present, append the spawn packets of the faces within the player's radius in the same bundle as the chunk. If missing, queue a build for the chunk and send its faces when it is published. |
| Table build | region thread → CPU pool → region thread | The region thread copies the chunk's ore positions (from CE's per-chunk custom block storage through `integration/craftengine/oreoverlay`, an int-array copy) and an occlusion bitset of the chunk + 1-block border (vanilla `isOccluding` of the visual; tfp blocks from the datagen `ore_overlay/occluding.json` list). The CPU pool computes the face masks (`core/oreoverlay/ExposedFaces`, pure). The region thread publishes the table (atomic swap in a concurrent map keyed by world + chunk). No world write. |
| Block change seen by a viewer (`BLOCK_CHANGE`, `MULTI_BLOCK_CHANGE` send) | Netty | Fast path, same packet bundle (§3.5): from the published table and the new state id of the changed position, decide provisionally which overlay faces of that position and its 6 neighbours appear or vanish, and send the spawn/remove packets with the block change. Enqueue the position for the authoritative rebuild (coalesced per position per tick). |
| Authoritative refresh | region → CPU → region | Snapshot the 3×3×3 neighbourhood of the coalesced positions, decide on the CPU pool, publish the new table on the chunk's region thread, and send viewers only the difference from what each was sent (per-viewer sent-set). Positions near a chunk edge also schedule the neighbour chunk on **its** region. |
| Player moves | Netty / entity scheduler | Chunks entering or leaving the send radius spawn or remove their faces (from the table only). |
| Chunk unload / player leaves the chunk range (`UNLOAD_CHUNK`, quit) | Netty | Remove that chunk's entities for that player (one `REMOVE_ENTITIES` with the id list). When the chunk unloads server-side, drop the table (recomputed on the next send). |

- **Triggers are viewer packets.** Every visible block change reaches a viewer as a block-change packet, whatever caused it:
  mining, placing, `/fill`, `/setblock`, WorldEdit, explosions, fluids, pistons, CE placement, collapses. So the overlay
  never misses a source that fires no Bukkit event. A chunk nobody watches is recomputed at its next send.
- **Pistons:** a moving ore is a moving-piston block entity drawn from the visual (rock) for 2 ticks. Its overlay is
  removed at the start of the move and re-added at the destination when the `BLOCK_CHANGE` arrives.
- **Falling ore:** TFC's collapse turns rich → normal → poor ore into falling blocks. A falling entity carrying a `tfp:ore`
  state gets 6 face overlays as passengers. They are spawned with the entity and removed with it.
  PAR-PHYS-01/02 call `OreOverlays.attachToFalling`.

### 3.4 Distance gating and the "no popping ring" rule
- Client side: a display entity is drawn only within `64 × view_range × entityDistanceScaling` blocks. That is a
  **spherical** per-entity, per-frame test, so it causes no square chunk ring.
- `view_range` is chosen for a ≈48 m cutoff at the default entity distance (100 %). At 48 m a 16-texel face covers
  ≈16 screen pixels at 1080p / FOV 70, so an ore speck is ≈1–2 pixels. The pop is low-contrast; S2 checks it with
  screenshots at 32/48/64 m.
- Server side: faces are sent for chunks within `ceil(48/16) + 1 = 4` chunks (Chebyshev) of the player, i.e. 81 chunks.
  The send radius covers the client cutoff sphere, so the visible edge is always the client's sphere, never the server's
  chunk square.
- Config: `ore-overlay.cutoff-blocks` (default 48), `ore-overlay.max-per-player` (8000),
  `ore-overlay.surface-outcrops-full-range` (default true). Faces whose air cell is at or above the column's
  `WORLD_SURFACE` heightmap − 8 (cliffs, outcrops: a TFC prospecting cue) are sent within the full view distance with a
  matching `view_range`. They are few (S1 counts them).

### 3.5 Same-tick appearance (no flicker or pop-in)
- When a player breaks the stone in front of an ore, the overlay must show in the same frame as the hole. The fast path
  on the outgoing block-change packet decides from the immutable table plus the new state (pure, O(7) lookups) and
  bundles the overlay spawn with the block change.
- The authoritative rebuild, one or more ticks later, only corrects a wrong provisional guess. That is rare: it needs a
  concurrent change in the same 3×3×3 cell.
- When an ore is mined, its own faces are removed in the same bundle as the `BLOCK_CHANGE` to air.

## 4. Entity-count and packet budget

### 4.1 Ore blocks per chunk (from the TFC worldgen data at the pin)
Method (scratch script outside the repo, inputs `data/tfc/worldgen/configured_feature/vein/*.json`):
- per vein: (shape volume) × density × (ore weight share) × (replaced rocks / 21) ÷ rarity × (part of the y range below
  the surface);
- cluster volumes by Monte Carlo of TFC's `Metaballs3D.simple` (size 20 ≈ 2.9 k, 30 ≈ 9.9 k, 40 ≈ 16.8 k cells);
- pipes π r² · 2h · 0.6; discs π (0.35 size)² · 2h;
- all 44 ore-bearing veins are in `#tfc:in_biome/veins` for every biome; the montane veins (y ≥ 80..120) only reach ore
  below high terrain.

| Terrain | Mean ore blocks per chunk | Largest contributors |
|---|---|---|
| Lowland (surface ≈ y 75) | ≈ **300** | normal tetrahedrite 35, sulfur 33, normal bismuthinite 27, normal sphalerite 27, ruby marble belt 22, normal malachite 21, rich native gold 20 |
| Mountains (surface ≈ y 160) | ≈ **400** | + montane tetrahedrite 27, montane cassiterite, montane bismuthinite |
| Chunk inside a large cluster vein (p95 order of magnitude) | ≈ **1 200–1 500** | one size-30/40 vein spans 4–9 chunks |

This agrees with round 1's assumption (200–600).

### 4.2 Exposure
- TFC 1.21 has vanilla-style noise and noodle caves (`ChunkNoiseFiller` `noiseCaves`, noodle caves), plus the cave,
  deep_cave and canyon carvers (probability 0.02 / 0.08 / 0.01).
- Our worldgen does not carve tunnels yet (ledger: caves not ported, PAR-WORLD-06). S1 therefore measures exposure on a
  vanilla world of the same version as a proxy, as well as on our world.
- Assumptions until S1:
  - share of underground solid blocks with an air/fluid/non-occluding neighbour: 3 % low, **5 % typical**, 12 % high
    (cave-dense mountains, canyons, cliffs);
  - exposed faces per exposed ore: **1.5** typical, 2 worst.

| Case | Faces per chunk | Within the gated radius (81 chunks) | Full view distance 10 (441 chunks) | Full VD 8 / 6 (289 / 169) |
|---|---|---|---|---|
| Typical lowland: 300 × 5 % × 1.5 | ≈ 22 | **≈ 1 800** | ≈ 10 000 | 6 500 / 3 800 |
| Worst average (mountains, cave-dense): 400 × 12 % × 2 | ≈ 96 | **≈ 7 800** | ≈ 42 000 | 28 000 / 16 000 |
| Hot chunk (vein cut by a cave): 1 500 × 15 % × 2 | ≈ 450 per chunk | adds ≈ 3 k when 6–8 such chunks are in range | | |

Per-ore entities instead of per-face would save ≈ 1/3 (1 / 1.5). That is not worth the lighting error (§3.1).

### 4.3 Packets and CPU
- **Per entity on send:**
  - `ADD_ENTITY` ≈ 55 B;
  - `SET_ENTITY_DATA` ≈ 120–150 B (item stack with `item_model` + `custom_model_data` colour, translation, left
    rotation, `view_range`);
  - in a bundle with the chunk; compression shrinks these well (repetitive).
- **Removal:** one `REMOVE_ENTITIES` per chunk.
- **Join or teleport:**
  - typical ≈ 1.8 k entities = 3.6 k packets ≈ 0.35 MB raw;
  - worst ≈ 7.8 k = 15.6 k packets ≈ 1.6 MB raw;
  - for comparison, the chunk data alone at view distance 10 is ≈ 5–9 MB raw.
- **Walking:** one new chunk row of 9 chunks ≈ 200 entities typical, ≈ 860 worst. The rate limiter spreads bursts.
- **Mining:** ≤ 6 spawns and ≤ 6 removals per broken block, sent only to the viewers of that chunk.
- **Server:**
  - no entities and no ticking;
  - table build ≈ 16 × 16 × (height) occlusion bits + ≈ 300 ore positions per chunk on the CPU pool (target ≤ 0.5 ms per
    chunk, JMH in PAR-REL-12);
  - memory ≈ 300 × 8 B + face masks per loaded chunk.
- **Pass gate (S1):**
  - p95 faces within the gated radius ≤ 8 k per player;
  - client frame time + ≤ 2 ms with 8 k overlays on the test client;
  - join MSPT unchanged within noise.
  - If it fails: lower the cutoff (40 m → 3 chunks, 49 chunks), lower the cap, or (owner) reconsider per-ore entities.

### 4.4 What CE Community lacks
- **Culling:** CE renderers in every sent chunk spawn for every viewer, and the ray-tracing culling, 64-block view
  distance and spawn rate limiting are Premium only (`client-optimization.entity-culling`). Our overlay does not use CE
  renderers, so we build gating, rate limiting and the cap ourselves (§3.2, §3.4). Premium culling helps only the CE
  renderers we keep (metal blocks, mechanical, crates at `level-1`); never depend on it.
- **No random `offset`, no ticker sleep/wake** in 26.9.2 (not needed here).
- **Not missing:** `destroy_stages` is Community (S2).

## 5. Mining, hitbox, light and other behaviour

- **Hitbox and outline:** the host rock's visual is a normal full cube. Outline, collision, client prediction and step
  sounds are exactly those of a rock block.
- **Mining speed, tool tier, hardness:**
  - TFC ores have the raw-rock hardness of their category and need the correct tool;
  - the client predicts from the shared visual, which is also a rock, and CE's break-speed handling corrects it
    server-side (existing behaviour, checked in game);
  - drops come from the ore's loot.
- **Break particles and sounds:**
  - TFC's own ore model uses the **rock** as its particle texture (`tfc:block/ore` `particle: #all`) and `SoundType.STONE`.
    So rock-coloured break particles and stone sounds are TFC parity, not a divergence;
  - CE replays the ore block's configured sounds server-side (the visual's SoundType is silenced client-side).
- **Cracks:**
  - vanilla cracks render on the host rock faces, under the 0.01 overlay, so the specks would hide cracks;
  - while a block is being mined, the runtime swaps that block's face overlays to a **cracked variant**: a composite item
    model of the overlay + `minecraft:block/destroy_stage_N`, the vanilla textures from the client jar, so nothing is
    copied. The stage follows the progress the server computes from the player's dig speed (the same formula vanilla
    uses for `BLOCK_DESTRUCTION` packets) and is sent to every viewer of the block (≤ 10 metadata packets per break);
  - fallback, if S2 finds a stage mismatch: dim the overlay (tint ×0.5) during mining;
  - CE `destroy_stages` is not used for ores: the carrier is a real model block, so the vanilla cracks exist.
- **Light and AO:**
  - flat light from the neighbour cell, so day/night, torches, lava and underwater are correct;
  - no smooth-light AO gradient on the specks, while the rock under them has AO: a small residual in inner corners,
    recorded in the ledger;
  - entity diffuse lighting is disabled (`shade: false` + tint per face, §3.1).
- **Seams:** the quad is exactly the face size, 0.01 in front. At outer corners two quads leave a 0.01 gap, invisible with
  speck textures. Mipmaps come from the same block atlas as the base.
- **X-ray:**
  - buried ores are rock in the chunk packets, and no overlay entity exists for them, so resource-pack x-ray shows rock;
  - entity-ESP clients see only overlays of faces that are already exposed (in caves within 48 m);
  - Paper anti-xray is not needed for ores;
  - the propick is unaffected (server-side).
- **Explosions, `/fill`, WorldEdit:** handled through the block-change packets (§3.3). The S2/ore checks assert no
  leftover overlay after a TNT blast and a `/fill … air`.
- **Pick-block (creative):** 1.21.4+ sends a server-side pick request, and CE answers with the real block's item, i.e. the
  ore block item (checked in game).
- **Waterlogging/fluids:** water and lava neighbours count as exposing (the face is visible through the fluid), and the
  display inside water is lit by the water cell.

## 6. Feel checklist (every renderer-drawn block: ore overlays, metal blocks, crates, mechanical blocks, devices drawn in hitbox-only mode)

Each related item (`PAR-INFRA-16`, `PAR-INFRA-17`, and the families that use renderer cubes: `PAR-DECO-09`, `PAR-DECO-12`,
`PAR-MECH-01`) has these as done criteria, with a screenshot or recorded value in `docs/verification/<ID>/`. It must
feel like a vanilla block:

1. **Cracks:** the crack animation tracks the mining progress.
   - Renderer cubes: CE `destroy_stages` (barrier carrier, no vanilla crack).
   - Overlay ores: the cracked overlay variant, so no crack is hidden under the overlay.
2. **Break particles and sound** have the material's own colour and sound where TFC differs from the host; for ores TFC
   uses the rock's particles and stone sounds (§5). For renderer cubes, check whether CE's particle overrides apply to a
   barrier carrier, else send server-side `block_crack`/item particles on break (an explicit action, allowed).
3. **Hit, step, place sounds** are the TFC SoundType of the block (CE replay), not the carrier's.
4. **Mining speed, tool tier, hardness** match TFC (time-to-break measured with a hand, the right tool and a wrong tool).
5. **No flicker or pop-in:** the overlay appears and disappears in the same tick as its face is exposed or covered
   (10 frames at 50 ms around a break and a place).
6. **Light:** correct at noon, at midnight and next to a torch; no black faces and no full-bright faces.
7. **No z-fighting** at 1, 16, 32, 48 m.
8. **No visible seam** between host rock and overlay (8× crops of an edge and an inner corner).
9. **Outline and hitbox** identical to a normal block (8× crop of the outline; standing height on top).
10. **Falling and collapse** unchanged: a falling ore keeps its overlay while it falls and lands as the right grade.
11. **No leftover overlay** after an explosion, `/fill`, `/setblock` or a piston push.
12. **Creative pick-block** gives the block's own item.
13. **Consistent look at all distances:** screenshots at 16/32/48/64/96 m show no square ring, and the spherical cutoff
    pop is not noticeable on a high-contrast ore (native gold, malachite) at 1080p.

## 7. Spikes

**S1 (PAR-WORLD-17): census and entity budget.**
- Census: an itest case counts, per chunk, ore blocks, exposed ores and exposed faces, split by ore and by depth band
  (surface outcrop vs cave), on our TFC world and on a vanilla world of the same seed (exposure proxy until caves are
  ported). 3 seeds × 32×32 chunks; p50/p95/max; totals in the gated radius and at view distance 6/8/10.
- Client cost: a synthetic field of 2 k / 8 k / 20 k overlay quads around the bot; frame time from the headless client
  (F3 average and the debug profiler), join packets and bytes, server MSPT.
- Screenshot of an exposed vein (today's full-cube ore look) as the "before" reference.

**S2 (PAR-INFRA-17): `destroy_stages` and the feel of renderer cubes.**
- One barrier-carrier metal block drawn by `item_display` with `destroy_stages`, plus one overlay ore on the synthetic
  layer.
- Checklist items 1–13 on 1.21.11 and 26.3.

## 8. Divergences recorded

- **Ores are overlays on exposed faces.** A buried ore looks like its host rock. Visible faces match TFC within the
  residuals: no AO on the specks, a 48 m cutoff where specks are about one pixel. Ledger row added 2026-10-22.
- **Ore grades are distinct in every profile.** This removes the old `level-3a` divergence.
