# CraftEngine block-visual workarounds, round 2: what CE really offers beyond note-block pools

Date: 2026-10-22. Question from the owner: is there a CraftEngine (CE) feature that lifts the cap on distinct opaque full-cube looks for vanilla clients, so TFC is not limited to note-block/mushroom-style pools? Our numbers: supply 1957 (1.21.11) / 2217 (26.3) opaque full-cube visual states; demand ≈1790 core terrain + ≈150 deco; ores alone 53 types × 21 rocks = 1113. Background: `docs/research/block-budget-workarounds.md` (round 1), `docs/research/vanilla-fullcube-supply.csv`, `ARCHITECTURE.md` §9.1, the `craftengine-visuals-pack` skill.

**Short answer.** No. On a vanilla client, CE cannot show more distinct block-rendered opaque full cubes than the vanilla client registry has full-cube states. Our CSV already inventories all of them (2701 on 1.21.11, 3089 on 26.3, of which 1957 / 2217 are terrain-grade). CE's own docs offer one escape for vanilla clients: drop the block model (`transparent: true`) and draw the block with a display entity (`entity_renderer`). That gives "virtually unlimited custom blocks", at a cost of one or more entities per block. CE's second escape is a **Fabric client mod** that registers real placeholder blocks. That one is unlimited, but only players who install the mod get it. Everything else either reuses the same state budget or is Premium-only performance help for the entity route. The useful new findings are:

1. **Exposed-only entity overlays on a shared carrier look**, extended from the 9 gem ores (level-2) to all ores. This frees up to **924 more states** (the whole ore demand of 1113 becomes 0), but it costs entities. It needs a census spike (S1).
2. **`destroy_stages` block setting.** It is in the jar we run and it is Community. It gives crack overlays (mining feedback) to entity-rendered blocks, which removes the main gameplay objection to renderer-drawn full blocks (spike S2).
3. **The CE client mod** gives unlimited real blocks to opted-in Fabric 26.2 players. Vanilla players still need a vanilla visual for every state, so it **does not raise the vanilla cap**. Whether a hybrid works (distinct looks for modded players, merged looks for vanilla players) is unknown (spike S3).

Evidence tags: **[JAR]** read from the CE jar we run (`craftengine-mwFiNxVO.jar`, `paper-plugin.yml` version **26.9.2**, the latest Modrinth release, 2026-09-28) with `javap`/`unzip`/`strings`. **[WIKI]** the CE wiki at https://xiao-momi.github.io/craft-engine-wiki/ (owner-designated primary docs; all 178 sitemap pages fetched 2026-10-22, appendix A). **[SRC1]** round-1 source reading at CE commit `b3079c5` (= 26.9.2). **[WEB]** other web source. **[CALC]** arithmetic. **[INF]** inference, not tested in a client.

Access notes:
- `https://docs.momirealms.net/craftengine/` did not resolve from this environment (DNS failure via the proxy and via r.jina.ai). The wiki's canonical URL is `momirealms.net/craft-engine-wiki/`, and the github.io copy was fully readable. The English and `zh-Hans` versions of the key pages (block states, entity renderer, exclusive features) have the same content.
- The machine's disk was full during this research (other worktrees), so a CE source clone was not possible. Code claims are therefore from bytecode in the jar, or from round 1's source reading at the same commit.

---

## 0. Ranked recommendation for TFC

| Rank | Option | Verdict | Extra full-cube looks unlocked | Next step |
|---|---|---|---|---|
| 1 | Keep real-state allocation with the §9.1 profiles (solid pool + identity duplicates + absent-in-TFC + optional Nether/End) | usable now (in place) | baseline 1957 / 2217 | none; it is the plan |
| 2 | **Exposed-face overlay on a shared carrier, for all ores** (generalise level-2's gem rule): a hidden ore sends its host rock's visual; each exposed face gets one `item_display` placed in the air cell | usable with caveat (entity count) | ore demand 1113 → 0. Beyond level-2: **+924** (44 non-gem ore types × 21 rocks). Beyond level-3a: +672 | **Spike S1** (census + client FPS). If it passes, add a profile `level-2o` |
| 3 | `destroy_stages` for every renderer-drawn full block (metal blocks, crates, overlay ores) | usable now (Community, in jar) | 0 by itself. It makes ranks 2 and 4 acceptable for mining feel | **Spike S2**, then adopt in `tfp:` templates |
| 4 | Barrier-carrier + `item_display` full blocks for rare deco (metal blocks 61, alabaster 51, crate/workbench 40) | usable with caveat | each moved family frees its count (≈150 total) | already the §9.1 rule for metal/mechanical. Extend after S2 |
| 5 | Premium entity culling (ray tracing, 64-block view distance, rate limiting) as an *optional* accelerator for 2 and 4 | usable with caveat (Premium licence; never required) | 0 states; cuts visible overlay entities [INF: largely, in caves] | in S1, measure with culling on vs off if a Premium jar is available |
| 6 | CE Fabric client mod: real placeholder blocks for opted-in players | usable with caveat (26.2 Fabric only; vanilla clients unchanged) | unlimited for modded players only; **0 for vanilla** | **Spike S3** (hybrid feasibility); otherwise document only |
| 7 | Demand cuts (not CE): ore grades share a look (level-3a/3b), or ores drawn on k rock "families" instead of 21 rocks | usable now (divergence) | grades: −252 (3a) / −504 (3b); families: 53×(21−k), e.g. k=5 → **−848** | owner decision; only if 2 fails |

Not possible / rejected (details in §2–§4): extra CE pools of opaque full cubes (none exist), cross-shape states (stairs/doors/trapdoors) carrying cube models, furniture-backed terrain, `block_display` renderers (same budget), per-position block tint, core-shader recolouring, biome-colour encoding, animated/atlas tricks, per-chunk or per-player packet multiplexing, per-region resource packs.

---

## 1. The hard ceiling (why no CE setting can raise it)

- A vanilla client renders a block from its **block-state id**, and the resource pack decides that state's model **globally**. The client's state registry is fixed per version. CE therefore maps each server state `craftengine:custom_N` to an existing vanilla state [WIKI configuration/block/states: "Visual Block State ... the plugin attaches custom models to specific vanilla states"; WIKI getting_start/first_block: "Minecraft has a fixed pool of block states — you can't create new ones. CraftEngine 'borrows' unused vanilla states"].
- **Verified in code** that CE itself treats the vanilla registry as the limit for vanilla clients. In the client-mod handshake (`core/plugin/network/mod/protocol/ServerboundEnableClientCustomBlockPacket.handle`), the client must report `vanillaSize == BlockManager.vanillaBlockStateCount()` and `currentSize == BlockManager.currentBlockRegistrySize()`, or it is kicked (`disconnect.craftengine.vanilla_block_registry_mismatch` / `current_block_registry_mismatch`). Only then does CE call `setClientCustomBlock(true)` and give that user an `IntIdentityList(currentSize)`, i.e. real custom ids [JAR]. Without the mod, every custom state goes through the visual mapping.
- Full-cube states on the vanilla client: 2701 (1.21.11) and 3089 (26.3). Of these, 1630–1914 are touched by CE's own `mappings.yml`, a few are unusable, and the rest give our terrain-grade 1957 / 2217 [round 1, `vanilla-fullcube-supply.csv`]. **This is the ceiling for block-rendered opaque cubes on vanilla clients, whatever CE does.**

---

## 2. Every CE option, with verdict

### 2.1 `auto_state` groups: all of them, real sizes

The jar has 24 group constants (`core/block/AutoStateGroup`): `NOTE_BLOCK, BROWN_MUSHROOM, RED_MUSHROOM, MUSHROOM_STEM, MUSHROOM, SOLID, (NON_)TINTABLE_LEAVES, WATERLOGGED_(NON_)TINTABLE_LEAVES, LEAVES, WATERLOGGED_LEAVES, LOWER_TRIPWIRE, HIGHER_TRIPWIRE, TRIPWIRE, SUGAR_CANE, CACTUS, CAVE_VINES, WEEPING_VINES, TWISTING_VINES, KELP, CHORUS, PRESSURE_PLATE, SAPLING` [JAR]. The wiki documents the same list [WIKI configuration/block/states]. The `visuals-pack` skill table lists only solid/leaves/tripwire/chorus.

States freed by the internal `mappings.yml` (26.9.2; counted from its lines, summed over all version sections, so they are upper bounds for 26.3) [JAR `resources/internal/configuration/mappings.yml`]:

| Group / mapping family | Freed states | Shape / client behaviour | Use for TFC full cubes? |
|---|---|---|---|
| note_block (+ mushrooms 3×63) = `solid` | 1289 / 1489 (runtime-verified) | full cube | in use |
| leaves (12 leaves blocks × 26 on 26.3; pool 117+117 on 1.21.x) | 117 + 117 | full cube, **no occlusion** | sparse cubes only (round 1 §5.2) |
| tripwire lower/higher | 63 + 63 | no collision | no |
| cave_vines 48, weeping 25, twisting 25, kelp 25 | 123 | climbable / water | no |
| sugar_cane 15, cactus 15 | 30 | no collision / damage | no (sugar_cane = the `transparent` carrier) |
| weighted pressure plates 14+14 | 28 | plate | no |
| saplings (stage 1) | 8–9 | cross | no |
| chorus | **0** (section present, no lines) | | no |
| *(not groups; used by templates via explicit state)* cut-copper stairs 4×80, doors 21×32, trapdoors 21×32, fence gates 12×16, copper bars 4×32, lightning rods 4×24, chains 4×6, lanterns 4×4, slabs 5×6, grates 4×2, anvils 3×2 | ≈2 000 | non-cube shapes (grates: full but see-through, no occlusion) | **no** (see 2.3) |

**Verdict: not possible.** No group adds opaque full cubes beyond `solid`, and all of these are already accounted for in round 1. Grates (8 states) behave like the leaves pool: sparse use only.

### 2.2 Explicit `state:` + `block_state_mappings` (repurposing absent vanilla blocks)

**Verdict: usable now (in use).** Already counted: 126/165 identity duplicates + 475/496 absent-in-TFC + 67 Nether/End. The wiki confirms "any available vanilla block state" can carry a model [WIKI intro/exclusive_feature "Any Block State as Model"], and shows a mapped shorthand `state: "note_block:0"` [WIKI configuration/block/states]. Mappings are one hop and cannot create states [SRC1]. Nothing new to unlock.

### 2.3 Cube model on a non-cube state (stairs, doors, trapdoors, gates...)

**Verdict: not possible for terrain.** The hitbox, collision, occlusion and client prediction come from the visual vanilla state on both sides [SRC1; WIKI block states "visual state determines ... carrier", settings "occlusion of skylight is entirely determined by the client-side"]. A cube drawn on a stair state can be walked into, culls its neighbours wrongly and is lit like a stair. Rejected. Those ≈2000 freed states remain what they are meant for: TFC stairs, doors, trapdoors and gates.

### 2.4 `transparent: true` + `entity_renderer` ("furniture-like blocks")

- **What CE says:** "The main use of a block entity renderer is to work around the limit on block variants ... By clearing a vanilla block's state with `transparent: true` and placing a display entity on the block, you can ... create virtually unlimited custom blocks." The documented convention is a shared `auto_state: {type: sugar_cane, id: transparent}` [WIKI configuration/block/states, …/states/entity_renderer].
- **For full blocks** a sugar-cane carrier is wrong, because it has no collision. Use the barrier state (one state, absent from TFC worlds, full collision, invisible) shared by every renderer cube. This is already in the skill's decision order.
- **Element types in 26.9.2:** item_display, text_display, block_display, item, armor_stand, better_model, model_engine [JAR `bukkit/block/entity/renderer/constant/*ElementConfig`, compatibility/*].
- **`tint_source`** (copies `dyed_color`/`custom_model_data` from a `tint_source_block` behaviour's stored item onto the rendered item) works for display items only [WIKI entity_renderer "Tint Source", configuration/block/behaviors/tint_source_block; JAR `core/block/entity/render/tint/*`]. It gives per-block colour for renderer blocks, not for model-rendered terrain.
- **Costs, unchanged from round 1:**
  - Community has no culling, so every renderer in every sent chunk is spawned for every player (≈2 packets per element on chunk send);
  - light is sampled at one point (black inside an opaque neighbour, so put the display in the air cell);
  - no AO or smooth light;
  - entity diffuse shading differs from block shading;
  - break particles and outline come from the carrier.
- The wiki warns of x-ray when the carrier's own model is non-full and has hardcoded rendering.

**Verdict: usable with caveat.** It is unlimited in looks but linear in entities. Fine for rare blocks, wrong for bulk terrain.

| Rating for TFC | ores 53×21 bulk | grades | connected blocks | packets | TPS | light/AO | hitbox | mining feedback | prediction traps | Folia | effort |
|---|---|---|---|---|---|---|---|---|---|---|---|
| barrier + item_display per block | ✗ (hundreds/chunk) | ✓ | ✓ via properties → appearances | high (2/elem/viewer on chunk send) | low (packet build on Netty; no ticking) | ✗ single-point light, no AO | ✓ barrier full cube | ✓ with `destroy_stages` (2.6) | barrier: none known | ✓ (CE show/hide on Netty, our state changes on region thread) | low (YAML) |

### 2.5 Exposed-face overlay on a shared carrier (generalised level-2)

- **Mechanism:**
  - Every ore state's appearance is its host rock's visual (block-rendered, 0 new states; many CE states may share one visual [WIKI "Expanded form: sharing visual states"; SRC1]).
  - An `exposed`-face set drives our own dynamic element: one `item_display` per exposed face, positioned in the air cell (correct flat light) and translated back to the face +0.01 against z-fighting (round 1 §5.1).
  - Hidden ores are byte-identical to rock in chunk packets, a free anti-xray.
- **Unlock:** level-2 already does this for 9 gem types (189 states). Extending it to all 53 types frees the remaining **924** states. Ore demand becomes 0, and total demand drops to ≈770 under level-0 rules (§9.1 level-0 1881 − 1113) [CALC]. That leaves ≈1190 (1.21.11) / ≈1450 (26.3) states for other CE packs.
- **Cost:**
  - round-1 estimate 6–50 exposed ores per chunk × 1.3–2 faces, i.e. ≈3 k–40 k displays per player at view distance 10;
  - the extra ores are the common ones (graded metal ores, coal, minerals), so the gem-only numbers do not carry over;
  - this needs **measuring** (S1).
- **Option C (custom element) needed:** distance gating, e.g. only within 4–6 chunks, with surface outcrops always shown. Community CE has no culling. Premium culling (2.8) would do most of this for us if the owner has it.
- **Mining feel:** destroy cracks come from the rock carrier unless `destroy_stages` is used (2.6). Break particles show rock. The propick and loose ore pieces still identify the ore.

| ores 53×21 | grades | connected | packets | TPS | light/AO | hitbox | mining | prediction | Folia | effort |
|---|---|---|---|---|---|---|---|---|---|---|
| ✓ (0 states) | ✓ (texture per grade on the overlay item) | n/a | **the risk**: proportional to exposed faces | low–medium (exposed-set updates on neighbour change, region thread, budgeted) | ~ flat light from the air cell, no AO; pre-darken per face | ✓ rock carrier | ~ cracks via carrier; particles rock | none (carrier is a normal cube) | ✓ if the exposed set is computed async and applied by the region runner ("decide async, apply sync") | medium (custom `BlockEntityElement` = version-sensitive NMS packet code) |

**Verdict: usable with caveat.** It is the only CE-based route that removes the ore demand without changing TFC's looks. Gate it on S1.

### 2.6 `destroy_stages` (new to our skills)

- **Docs:** block `settings.destroy_stages: {items: [...], translation, scale, rotation, brightness, view_range, ...}` "replace[s] the vanilla crack textures" with item displays [WIKI configuration/block/settings].
- **Verified present in 26.9.2:** `core/block/entity/render/display/DestroyStageDisplayEntity`, `DestroyStageDisplayEntitySetting` (keys `items`, `position`, `rotation`, `brightness`, `view_range`, `progress`, …), `DestroyStageDisplayRecorder` [JAR]. It is not marked Premium on the wiki.
- **Use:** renderer-drawn full blocks (barrier carrier: no vanilla cracks visible because the carrier is invisible), and overlay ores if we want ore-coloured cracks.

**Verdict: usable now.** It unlocks no states, but removes the "no mining feedback" objection to 2.4 and 2.5. Spike S2.

### 2.7 Furniture-backed blocks

Furniture is a real persisted `ItemDisplay` entity + packet elements + hitbox entities; off-grid [WIKI configuration/furniture, …/variants; SRC1]. **Verdict: not possible for terrain** (an entity per block, interaction/shulker hitboxes, no worldgen placement, no light/occlusion). It is worse than 2.4 in every column.

### 2.8 Premium-only features (what they would change)

| Premium feature | Source | Effect on our budget |
|---|---|---|
| Entity culling: server ray tracing, `view-distance` 64, per-player rate limiting (bucket 1000, refill 25/tick), display view-range zeroing | [JAR config.yml `client-optimization.entity-culling` "[Premium Exclusive]"; WIKI intro/exclusive_feature, entity_renderer "Entity Culling", furniture/variants; commands `/ce feature toggle-entity-culling`, `entity-culling-distance-scale`] | 0 states, but it makes 2.4/2.5 scale: cave overlays behind rock are not sent [INF]. Optional accelerator only: §9.1 says never depend on Premium |
| Client-bound item model/data/material | [JAR config.yml `item.client-bound-model`; WIKI item/data, item/models] | items only; irrelevant to blocks |
| Pack protection / "mega-texture" atlas merging | [WIKI intro/exclusive_feature, workflows] | pack size/obfuscation; 0 states |

The culling parser exists in the Community jar too (`AbstractBlockManager$BlockParser.parseCullingData`) [JAR]. Round 1 verified it is ignored without Premium [SRC1 Config L851].

### 2.9 CE client mod (Fabric)

- **What it is:** GitHub `Xiao-MoMi/craft-engine-client-mod` (linked from [WIKI addon/craft_engine_client_mod]). Its README [WEB]:
  - "registers placeholder blocks on the client and lets the server remap them at runtime", which makes real custom blocks with "unlimited count, no vanilla state sacrifices";
  - `server-side-blocks` (client, default 10000) must be ≥ the server's `block.serverside-blocks` [WIKI compatibility/axiom];
  - `enable-client-custom-block` defaults to false (rejoin to apply);
  - also offers block-update cancellation (fewer ghost blocks), a creative tab and skipping the pack loading screen;
  - Fabric only, **Minecraft 26.2**, Java 25.
- **Server side** [JAR]:
  - `resource-pack.generate-mod-assets` (default false) adds assets for the mod (`AbstractPackManager.generateBlockOverrides(…, generateModAssets)`);
  - `network.mod-channel.visual-block-states-max-per-packet: 5000`;
  - protocol `ClientboundVisualBlockStates*`, `ServerboundEnableClientCustomBlockPacket`, `Clientbound/ServerboundCancelBlockUpdate*`;
  - `chunk.sync-custom-blocks-on-chunk-load`.
- **Why it doesn't solve our problem:** our target is vanilla clients (PRD). A modded player sees unlimited looks, but every vanilla player still needs a vanilla visual for every state, so the vanilla allocation must still fit. Version coverage (26.2 Fabric only) misses 1.21.11 and 26.3 at the time of writing.
- **Hybrid (unknown):** could modded players see distinct grade/rock looks while vanilla players get a merged profile? CE assigns one model per appearance and refuses two models on one vanilla state. So we would need to post-process the generated mod assets (`AsyncResourcePackGenerateEvent`) to give `craftengine:custom_N` a model different from its vanilla visual. Spike S3.

**Verdict: usable with caveat** (opt-in nicety). **0 for vanilla.**

### 2.10 Other CE features checked

| Feature | Verdict | Evidence | Unlock |
|---|---|---|---|
| `block_display` renderer element | not possible as a workaround | it renders a vanilla block state with the client's model for that state [WIKI entity_renderer], so it uses the same budget | 0 |
| `models:` weighted list (random model per position) | usable now, but not a budget tool | [WIKI block states ⑤] random per-position choice (vanilla `weight`), not controllable per block. Free texture variety for rock/soil (TFC-like) without extra states | 0 |
| Model `x/y/z` rotation, `uvlock` (z needs 1.21.11+) | no gain | [WIKI block states, model sub-keys]; orientation is already counted per state (round 1 §5.3) | 0 |
| `blueprint:` (.bbmodel → block model) | authoring convenience | [WIKI block states ⑦] | 0 |
| Paper anti-xray with CE ids (`craftengine:custom_N` in `hidden-blocks`) | usable with caveat | [WIKI compatibility/anti_xray]. Hides buried ores in packets, but CE renderers are spawned regardless, so it does not reduce overlay entities. Folia support of Paper anti-xray is unknown | 0 |
| Multiple / optional packs per player (`resource-pack.packs`, presets, `/ce feature pack-preference`) | no gain | [WIKI getting_start/workflows/multi_pack; JAR config.yml `packs:`/`presets:`]. An optional pack can re-skin the same states but cannot add states | 0 |
| Atlas `paletted_permutations` / `unstitch` sources | pack-size helper | [WIKI configuration/atlas]. Could generate ore×rock recolours client-side to shrink the pack | 0 |
| `tint_source_block` behaviour | renderer-only | [WIKI behaviors/tint_source_block] | 0 |
| Random `offset` for renderer elements, block-entity ticker sleep/wakeup API | **not in 26.9.2** | [WIKI entity_renderer "Random Offset", api/block_entity_ticker] document them, but the jar has no `offset` key in renderer configs and no sleep/wake classes [JAR]. Our skills' "not in our version" notes are confirmed | n/a |
| CE Proxy addon | irrelevant | [WIKI addon/craft_engine_proxy] proxy-side helper | 0 |
| `sound-system`, `simplify-adventure-break-check`, `deceive-bukkit-material` | unrelated to looks | [JAR config.yml `block:`] | 0 |

---

## 3. Minecraft 1.21.5+ / 26.x features

| Feature | Verdict | Evidence | Unlock |
|---|---|---|---|
| 1.21.4 item model definitions (`select`, `range_dispatch`, `composite`, tints) | usable now, for display items only | [WIKI configuration/item/models/*, advanced/model_tinting: "Since 1.21.4 ... tintindex is simply the position in the tints: list"] | unlimited looks for 2.4/2.5 overlays (one item, select by CMD string), 0 for terrain |
| Blockstate format | no change: still `variants`/`multipart` per state | round 1 [JAR] decoding of 1.21.4/1.21.11/26.1.2/26.3 | 0 |
| Per-texture render layer (26.1, snapshot 7: any block model can be cutout/translucent) | usable on 26.x | [WEB texture-pack changelogs; round 1 §2.6]. Two-layer TFC ore models work without flattening on 26.x; occlusion still follows the vanilla block | 0 states (quality only) |
| Block tinting | not possible from a pack | block tint sources are registered in client **code** (`BlockColorRegistry`/`BlockTintSource`) [WEB docs.fabricmc.net/develop/blocks/block-tinting]; vanilla registers them only for grass, foliage, water, redstone, stems, etc. | 0 |
| New blocks in 26.3 (sulfur, cinnabar, poplar leaves, wool/concrete slabs/stairs) | already counted | `vanilla-fullcube-supply.csv` (26.3 column 2217) | included |

---

## 4. General vanilla-client techniques (web + reasoning)

| Technique | Verdict | Why | Unlock |
|---|---|---|---|
| Core-shader block recolouring / texture selection by vertex colour or position | not possible (for us) | Sodium/Iris replace the terrain pipeline; shared core shaders conflict with BetterHud; the shader format changed in 1.21.5+/26.x; the shader cannot know a block's rock or ore type [round 1 L; INF] | 0 |
| Biome-colour encoding (our async biome remap paints underground 4×4×4 cells with a "rock colour" biome; tinted greyscale model) | not possible in practice | only blocks with a hardcoded colour provider tint (grass_block: 1 state; the leaves pool is non-occluding, §5.2 round 1); colour is per 4×4×4 cell with client-side blending, so it smears at strata edges; rocks differ in pattern, not only hue; it would fight our seasonal biome remap | 0 |
| One model state + per-position tint for ore specks | not possible | the tint is per biome cell, not per ore | 0 |
| Animated textures / atlas frames | not possible | frames advance globally (all positions show the same frame), so they cannot select per block. Still useful (zero-cost) for glowing items in displays | 0 |
| Per-chunk / per-player packet multiplexing (reuse state V for "copper-in-granite" here and "copper-in-basalt" there) | not possible | V's model is fixed by the pack for the whole client; rebinding needs a resource reload | 0 |
| Per-region resource packs | not possible | each switch is a full client resource reload (seconds), round 1 K | 0 |
| ItemsAdder/Oraxen/Nexo/Polymer limits | reference only | none of them exceeds note+mushroom for real cubes; all fall back to display entities [round 1 §4] | 0 |
| CE-based ore-heavy servers | no public report found | searches (web, CE GitHub issues) found nothing beyond `auto_state: solid` usage [WEB] | — |
| Block-entity-free item displays with light sampling | = 2.4/2.5 | light from the display position; put it in the air cell | as 2.5 |

---

## 5. Cross-checks against our skills (doc vs jar)

| Wiki claim | Our skill | Jar / source result | Action |
|---|---|---|---|
| Variant matching: "appearance: takes the value from the **first** matching entry" | `craftengine-blocks`: "the **last** matching appearance wins" (source-verified at b3079c5) | 26.9.2 = b3079c5. Bytecode of `AbstractBlockManager$BlockParser` was inconclusive within this session | **Unresolved contradiction.** Harmless for us (datagen emits non-overlapping, fully specified keys), but add a unit check in the M0 spike re-run or read `BlockParser.parseCustomBlock` source when disk allows |
| Same vanilla state with two models: "the plugin will log a conflict warning" | skill: "the code throws; the whole block aborts" | round 1 [SRC1] + M0-VIS-01 | keep the skill (stricter); datagen must never emit it anyway |
| 24 auto_state groups incl. cactus/sugar_cane/vines/kelp/pressure_plate/sapling | skill lists 4 | confirmed by `AutoStateGroup` enum [JAR] | add the full list + sizes (§2.1) to the visuals-pack skill when it is next edited |
| `entity_renderer.offset` random offset | skill: "not in our CE version" | no `offset` in renderer config classes [JAR] | skill correct |
| Ticker sleep/wakeup API page | skill: "dev branch only" | no sleep/wake classes in 26.9.2 [JAR] | skill correct |
| `destroy_stages` block setting | not in skills | present [JAR] | add to `craftengine-blocks` / `craftengine-gui-furniture` after S2 |
| Entity culling Premium | skill: Premium-only | config marks `[Premium Exclusive]` [JAR]; round 1 verified the gate | consistent |
| Client mod | not in skills | protocol + config present [JAR] | mention in the `craftengine` hub as "not a vanilla-client solution" |

---

## 6. Spike specs (things that need a game client)

Run these through the headless harness (`spikes/client/`) on Paper 1.21.11 and 26.3. Per CLAUDE.md, test execution goes to Haiku subagents.

**S1: exposed-ore census and overlay cost (decides rank 2).**
1. Census (pure Java, `worldgen` module):
   - for 3 seeds × a 32×32-chunk area around spawn, count per chunk: ore blocks, ores with ≥1 air/water neighbour, exposed faces;
   - split by ore type and by depth band (surface ≤ 8 blocks below the heightmap vs caves);
   - report p50/p95/max per chunk and the totals at view distances 6/8/10.
2. Client cost:
   - place a synthetic field of N `item_display` face quads (N = 5 k, 20 k, 50 k) inside 10 chunks of the player;
   - measure client frame time (`/debug` profiler or F3 average), login packet count and bytes, and server MSPT while players join;
   - repeat with CE Premium culling on if a Premium jar is available.
3. **Pass:** p95 exposed faces × chunks-in-view ≤ ≈8 k per player, and frame time +≤2 ms on the test client. If it passes, implement a dynamic overlay element with distance gating and a `level-2o` profile; if it fails, keep gems-only.

**S2: `destroy_stages` on renderer cubes (decides rank 3).** Make one barrier-carrier block drawn by `item_display` (cube item model) with `destroy_stages.items` = 10 crack items. In survival on 1.21.11 and 26.3, check:
- cracks appear and track progress;
- no vanilla barrier particles or outline artefacts;
- which particles appear on break (the carrier decides them: record what a barrier carrier shows, and whether `settings` particle overrides fix it);
- light at noon, at night and next to a torch;
- interaction with 2 adjacent renderer cubes (z-fighting).
Screenshot each step.

**S3: client-mod hybrid (decides rank 6).** On 26.2 with the Fabric mod (`enable-client-custom-block: true`) and `generate-mod-assets: true`:
1. confirm that the mod renders `craftengine:custom_N` with the model of its appearance;
2. in an `AsyncResourcePackGenerateEvent` listener, give two CE states that share one vanilla visual (level-3a merged grades) two different models in the mod blockstates only;
3. check that the vanilla client shows the merged look and the modded client the distinct looks;
4. check the kick paths (mismatched `server-side-blocks`).

---

## 7. What is verified and what is assumed

**Verified in code (26.9.2 jar):**
- the client-mod handshake and registry checks;
- the auto_state group list;
- `mappings.yml` freed-state counts;
- `destroy_stages` classes;
- renderer element types;
- the absence of `offset` and of ticker sleep;
- the Premium tag on entity culling in config;
- `generate-mod-assets` / mod-channel settings;
- multi-pack config keys.

**Verified in docs (wiki, 178 pages):** everything cited [WIKI].

**From round 1 (source b3079c5 + client jars):** the supply numbers, one-hop mappings, the model-conflict abort, Community no-culling, the light-probe position.

**Assumptions [INF]:**
- entity counts and FPS impact of overlays;
- the effect of Premium culling in caves;
- barrier-carrier light behaviour;
- hybrid feasibility of the client mod;
- Folia support of Paper anti-xray;
- the biome-tint smearing argument (not tested in game).

---

## Appendix A: every CE wiki page read (https://xiao-momi.github.io/craft-engine-wiki/ + path)

Block-visual, state-limit and client-rendering pages are **bold**.

| Path | Takeaway |
|---|---|
| (root) | Intro: "create new blocks that go beyond vanilla Minecraft's block limit" via borrowed states; no client mods required |
| **intro/simply_better** | Open source, free; templates; furniture is "true entity mode"; nothing on the state cap |
| **intro/exclusive_feature** | "Any Block State as Model"; Premium: pack protection + mega-texture atlas, client-side item components, **entity culling** |
| intro/spark | spark output caveats for CE |
| intro/under_development | empty placeholder |
| getting_start | tutorial index |
| getting_start/installation | Community (Modrinth/GitHub) vs Premium (VoxelShop/BuiltByBit/NMCrate) |
| getting_start/project_structure | pack folders; configuration/resourcepack layout |
| getting_start/items | first item |
| getting_start/item_models | first item model |
| **getting_start/first_block** | "fixed pool of block states — you can't create new ones"; auto_state note_block groups |
| getting_start/first_furniture | display-entity furniture, off-grid |
| getting_start/first_image | font images |
| getting_start/next_steps | where to go next |
| getting_start/database | SQLite/MySQL storage settings |
| getting_start/set_up_host (+ /self, /external, /dropbox, /onedrive, /gitlab, /lobfile, /mcpacks, /openlist, /s3, /s3/cloudflare_r2, /s3/tencentcloud_cos) | pack hosting providers; self-host rate limiting; no visual impact |
| getting_start/workflows | generate → validate → zip → upload; protection needs Premium |
| **getting_start/workflows/multi_pack** | optional extra packs per player; cannot add states |
| advanced | advanced tutorial index |
| advanced/item_model_definition | 1.21.4 item model definitions |
| **advanced/model_tinting** | tintindex → `tints:` list for items (1.21.4+); items only |
| advanced/template_in_action | templates in practice |
| addon | addon index |
| **addon/craft_engine_client_mod** | link to the Fabric client mod (real custom blocks for modded clients) |
| addon/craft_engine_proxy | link to the proxy addon |
| api | API index |
| api/block_behavior | custom Java block behaviours |
| api/block_entity | CE block entities (per-block data, renderers) |
| api/block_entity_ticker | ticker sleep/wakeup (**not in 26.9.2**) |
| compatibility | index |
| **compatibility/anti_xray** | Paper anti-xray works with `craftengine:custom_N` ids |
| compatibility/asp | Advanced Slime Paper native |
| **compatibility/axiom** | needs the client mod; client `server-side-blocks` ≥ server; `generate-mod-assets` |
| compatibility/bluemap | BlueMap integration |
| compatibility/datapack | `craftengine:simple_block` feature; internal ids where needed |
| compatibility/denizen, /external_item_sources, /mythicmobs, /placeholderapi, /quickshop_hikari, /skript, /supported_levelers | third-party integrations; no visual impact |
| compatibility/world_painter | use real block ids |
| configuration | config folder format, section delimiters |
| configuration/atlas | atlas sources incl. `paletted_permutations` (pack-size helper) |
| configuration/attribute (+ /damage-formula, /definition, /equipment-set, /modifier, /operation) | experimental attribute system; no visual impact |
| **configuration/block** | block anatomy; `state` is the only required section |
| configuration/block/behaviors | behaviour index |
| configuration/block/behaviors/attached_stem_block, bouncing_block, budding_block, bush_block, button_block, change_over_time_block, chime_block, concrete_powder_block, crop_block, decay_block, directional_attached_block, display_item_block, door_block, double_high_block, drawer_block, drop_experience_block, face_attached_horizontal_directional_block, falling_block, fence_block, fence_gate_block, grass_block, hangable_block, hanging_block, item_frame_block, lamp_block, leaves_block, liquid_flowable_block, multi_high_block, near_liquid_block, on_liquid_block, pressure_plate_block, sapling_block, seagrass_like_block, seat_block, simple_particle_block, simple_storage_block, slab_block, snowy_block, sofa_block, spreading_block, stackable_block, stairs_block, stem_block, strippable_block, sturdy_base_block, surface_spreading_block, toggleable_lamp_block, trapdoor_block, vertical_crop_block, wall_torch_particle_block (one page each) | gameplay behaviours. The visual-relevant ones: display_item_block/drawer_block/item_frame_block (renderer per block), seagrass_like_block (thin carrier lets water flow) |
| **configuration/block/behaviors/tint_source_block** | stores an item as tint provider for render elements (renderer-only) |
| **configuration/block/settings** | `support_shape`; skylight occlusion is client-side only; **`destroy_stages`** (custom crack displays); `client_bound_tags` (vanilla blocks only) |
| **configuration/block/states** | visual vs internal state; 24 auto_state groups; shared `id`; explicit `state`; models list; `transparent`; blueprint; renderer as "workaround to the limit"; variant rules (first-match claim, §5) |
| **configuration/block/states/entity_renderer** | element types, display params, random `offset` (not in 26.9.2), tint_source, **culling = Premium**, conditions; x-ray warning |
| configuration/block/states/properties | property types; waterlogged needs a waterlogged visual |
| configuration/category | item browser categories |
| configuration/emoji, /font, /image, /global_variable, /translations, /sound, /jukebox_song, /painting | text/images/sounds/registries; no block-visual impact |
| configuration/equipment | trim-based / component equipment; Premium note on one mode |
| **configuration/furniture** (+ /behaviors, /behaviors/display_item_furniture, /glowing_furniture, /simple_storage_furniture, /settings, /variants) | entity-based off-grid decoration; furniture culling = Premium; glowing via light blocks |
| configuration/item (+ /behaviors and its 11 sub-pages, /data, /models and its 8 sub-pages, /settings, /updater) | item config; client-bound data/model = Premium; nothing on block states |
| configuration/loot_source, /recipe | loot sources; recipes (some recipe features Premium) |
| configuration/worldgen_feature | CE worldgen features (trees, ores) |
| reference | index |
| **reference/commands** | `/ce debug visual-state-usage`, `auto-state-usage`, `real-state-usage`, `is-section-injected`; culling/display view-distance feature commands |
| reference/conditions, /events, /functions, /loot_table, /number_format, /particle_effect, /script, /template, /text_format, /text_format/chain_arguments, /file_conflict | config reference; no budget impact |
| search | search page (no content) |

Sources (web): https://xiao-momi.github.io/craft-engine-wiki/ (sitemap.xml, 178 URLs) · https://github.com/Xiao-MoMi/craft-engine-client-mod · https://github.com/Xiao-MoMi/craft-engine/commits/dev · https://api.modrinth.com/v2/project/craftengine/version · https://docs.fabricmc.net/develop/blocks/block-tinting · https://www.modpackindex.com/mod/112533/craftengine-client-mod · CE GitHub issues search (issue #334, #624: nothing on state limits).
