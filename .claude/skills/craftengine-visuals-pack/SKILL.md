---
name: craftengine-visuals-pack
description: Choosing how every TFC block looks on vanilla clients through CraftEngine, and building/delivering the merged resource pack. Covers block_state_mappings (one-hop, frozen install copy), the auto_state pools with verified sizes per MC version, repurposing vanilla states with explicit state + model (full-cube budget, buttons/carpets/crop ages, model-conflict rule, sound silencing, client-prediction traps, render layers on 1.21.x vs 26.x), the allocation/budget worksheet, 1.21.4 item model definitions for state visuals (heat glow via custom_model_data), the pack pipeline (no build at startup, merge order, workflows, validation, hosting, delivery), and BetterModel/BetterHud merging and shader conflicts. Use this whenever choosing or changing a block's appearance or hitbox, running out of visual states, writing the datagen visual allocator, designing state-dependent item visuals, or touching resource-pack build/merge/hosting settings, even if the user just says "the ore texture is wrong" or "players don't get the pack".
---

# Visual states and the resource pack (CraftEngine)

Sources: `references/research-report.md` (CE @ `b3079c5` + vanilla client jars 1.21.4/1.21.11/26.1.2; it contains the per-block table of all default mappings) and `references/appearance-strategy.md` (our TFC family → visual table, budgets, YAML examples). §-numbers refer to the research report.

## How a block's look is decided (§1–§3)
- Each CE state is sent to clients as a **visual vanilla state**. Its hitbox, client physics/prediction, particles, occlusion and SoundType are the client's.
- **`auto_state` pools** exist only because CE's internal `mappings.yml` maps real vanilla states onto look-alikes, which frees them. Verified sizes:

  | Pool | 1.21.4–1.21.11 | 26.1+ |
  |---|---|---|
  | `solid` (note_block + mushroom, shared objects) | 1289 | 1489 |
  | leaves (dry + waterlogged) | 117 + 117 | same |
  | tripwire (lower + higher) | 63 + 63 | same |
  | chorus | **0** | 0 |

- **A mapping we add only creates an `auto_state` candidate if the block belongs to a pool.** Stone, wool, ores and similar become **explicit-only** visuals (`state: minecraft:stone` + model).
- **Mappings are one hop.** If real X is mapped to Y and Y is itself some custom block's visual, real X looks like that custom block. Map real states only to pure-vanilla states that no custom block uses.
- **An explicit `state:` + model rewrites that exact vanilla state for every client.** The client merges blockstates per state, so other states keep their vanilla look; this works even for multipart vanilla blocks.
- **Two different models on one vanilla state abort the whole custom block** (the wiki calls it a warning; the code throws). Shared explicit states need byte-identical variant JSON.
- Each visual state in use silences its whole SoundType client-side, and CE replays the sounds server-side. Expected, but know that a `stone` visual mutes vanilla stone-type sounds client-side.
- **Client-prediction traps:**
  - buttons: pair `powered=false/true` with the same model, which leaves 168 looks;
  - `redstone_ore`: override both `lit` states, or skip it;
  - never use ice/slime/honey/soul sand/cobweb/sweet berry/powder snow/scaffolding/ladders, or vines/kelp unless you want climbing;
  - cherry/pale-oak leaves spawn particles client-side.
- **Transparency:**
  - 26.1+: the render layer is per texture, but occlusion still follows the vanilla block, so a see-through texture on a solid visual gives x-ray.
  - ≤1.21.11: the layer is per block. For cutout or translucent TFC blocks on 1.21.x, use leaves/grate/glass-type visuals or `transparent` + renderer.

**Verified in game (M0-VIS-01, `docs/spikes/M0-VIS-01.md`, 1.21.11 + 26.3 clients):**
- explicit absent states, double-slab identity duplicates, button states and `auto_state` all render our textures;
- `block_state_mappings` hide the vanilla originals;
- one model on two different vanilla states is accepted (hardened = raw);
- flattened ore PNGs look right;
- runtime pool counts via `AutoStateGroup.candidateCount()`/`candidates()` match 1289/1489;
- `auto_state: solid` takes `brown_mushroom_block` states first.

## Budget and allocation (researched: `docs/research/block-budget-workarounds.md`, per-block data in `docs/research/vanilla-fullcube-supply.csv`)

**Supply** of opaque full-cube visual states (CE default-mapping states excluded):

| | 1.21.4 | 1.21.11 | 26.1.2 | 26.3 |
|---|---|---|---|---|
| CE `solid` pool | 1289 | 1289 | 1489 | 1489 |
| Identity duplicates: render identical to another state, so they're safe even if the vanilla block exists (beehive honey 1–4, target power, double slab → full block, infested, waxed copper, tnt) | 126 | 126 | 126 | 165 |
| Absent from a TFC world: map real ones away, block vanilla mechanics that create them | 471 | 475 | 475 | 496 |
| Nether/End only: usable only if those dimensions are disabled | 67 | 67 | 67 | 67 |
| **Total** | 1953 | 1957 | 2157 | 2217 |

**Demand** (TFC, deduped by model content; `scratchpad` scripts reproduced in the report):
- ores 1113 (53 ore types × 21 rocks; flatten base+overlay into one texture per ore×rock, because ≤1.21.11 solid-layer visuals ignore alpha);
- rock ~190 (hardened **shares** raw's visual);
- logs/wood 240, planks 21, soils ~65, connected grass ~48, sand/sandstone ~28, deposits 84;
- deco: metal blocks 61, alabaster 51, crate/workbench 40.

Mechanical blocks (clutch/encased axle, 180) are rare, so they go to `entity_renderer` and take no budget. Core terrain ≈ 1790 fits every version. With deco ≈ 1940, it fits 26.x with ~250 spare, but 1.21.x is ~0–70 short depending on Nether/End. Close that gap by moving the rarest deco (metal blocks) to renderers or the leaves pool (sparse blocks only). **Merging ore grades is not needed.**

Allocation order: `solid` → identity duplicates → (Nether/End) → absent-in-TFC (clean ones before minor-caveat ones) → leaves pool for sparse cubes → `transparent` + renderer. Reserve option: exposed-only overlay for the rarest gem ores. It works if each exposed face gets a display positioned in the air cell and shifted back 0.01 (inside the block the light is 0, so it renders black), but Community CE has no culling, so it's only for rare ores. Measure exposed faces per chunk first.

Also required: a datapack that edits client-relevant tags for repurposed blocks (ambient-sound tags, `#crystal_sound_blocks`, `#impermeable` for waterlogged double slabs). Also intercept vanilla mechanics that would create repurposed blocks in a TFC world: lava+water → cobblestone/stone/obsidian/basalt, concrete-powder hardening, copper oxidation, recipes and loot.

### Allocator rules (from the earlier analysis)
- The datagen allocator:
  1. dedupes identical models (hardened = raw);
  2. assigns from the audited CSV (`docs/research/vanilla-fullcube-supply.csv`) in the order above;
  3. emits **full state strings** (never `block:N`);
  4. emits `block_state_mappings` hiding real instances of every repurposed state, without repeating CE's internal lines;
  5. reports used/available per pool and per MC version, and fails CI on overflow.
  If it still doesn't fit, **ask the user** (e.g. merge ore-grade visuals on 1.21.x only). Never degrade silently.
- The installed `mappings.yml` is frozen at first install, so pool sizes differ per server. After `CraftEngineReloadEvent`, check the pool candidate counts at runtime and warn on shortfall.
- First-time `auto_state` allocation can be nondeterministic with multi-threaded loading. Prefer explicit assignments from datagen, or seed `cache/visual_block_states.json`. Run `/ce clean-cache visual_block_states` after removing blocks.

## Visual budget profiles (decided; spec in ARCHITECTURE.md §9.1)
The allocator implements **all** profiles `level-0` … `level-3b`, and the owner selects one per MC version family in config (`visuals.profile`). Defaults: **`level-3a` on 1.21.x, `level-2` on 26.x.** Always on:
- metal and mechanical blocks via `entity_renderer`;
- hardened = raw;
- flattened ore textures;
- button states (not tripwire) for groundcover.

Rules when implementing:
- datagen emits one allocation per (profile × version family) into the jar; the installer writes the selected one;
- at runtime, check the real CE pool counts: fail clearly if the profile doesn't fit, warn below `visuals.solid-reserve-warning` (500);
- `visuals.use-nether-end-blocks: auto` uses Nether/End blocks only if those dimensions are off;
- never silently change a profile. Switching is world-safe (client-side mappings only) but needs a restart and a pack rebuild.

## State-dependent item visuals (§4.3)
Our plugin writes `custom_model_data` on the stack (`floats[0]` = heat 0..1, `colors[0]` = glow colour). The CE item `model:` is a `minecraft:range_dispatch` on `custom_model_data` (thresholds), and above a threshold it uses a `composite` of glow overlay + base, with a `custom_model_data` tint. Keep the pack minimum ≥ 1.21.4 and `always-use-custom-model-data: false`, otherwise CE claims `floats[0]`. Update the floats rarely (each change re-sends the item). The same pattern covers pan stages, mold fill, bloom size.

## Resource pack pipeline (§5–§8)
- **CE does not build the pack at startup.** Only `/ce reload pack|all` or `/ce workflow <name>` builds it. Players get the last uploaded zip. So the installer triggers a full CE reload/pack build after writing our pack on first install and after content updates. BetterModel and BetterHud rebuilds also need a CE pack build to reach players.
- Merge order is "first file written wins": CE pack folders (filesystem order), then `merge-external-folders`, then zips. Keep everything under our own namespace, and don't ship `minecraft:` blockstates/models another pack also ships. Don't ship a file at a path CE should *generate* (existing files beat `generation:`).
- Settings we want:
  - `supported-version.min` ≥ 1.21.4;
  - `merge-external-zip-files` keeps `BetterModel/build.zip` (default). BetterHud registers itself through its hook, so don't list it twice;
  - `exclude-core-shaders: false` (true would delete BetterHud's text shader);
  - **disable CE's built-in `remove_shulker_head` pack**: its old-format shader may break 1.21.6+/26.x clients (unverified).
- CE ships **no** `rendertype_text`/`text` core shader, so BetterHud's shader is safe from CE.
- Hosting: `self` (built-in HTTP with token/rate limit) or external hosts. Delivery: `send-on-join`. `kick-if-failed-to-apply` defaults to **true** if the key is missing. Set it deliberately.
- `AsyncResourcePackCacheEvent` listeners must call `registerExternalResourcePack` on **every** firing.

## Pitfalls (§9)
Duplicate mappings shift indices; frozen `mappings.yml`; one-hop mappings; the model conflict aborts the block; stale cache reservations; no pack build at startup; filesystem merge order; other packs' multipart dropped for overridden blocks; shipped files beat generation; per-SoundType silencing; client prediction; transparency/occlusion; `supported-version.min` below 1.21.2 makes CE claim CMD floats; `exclude-core-shaders`; digits in `$$#suffix`; `kick-if-failed-to-apply`; `/hud reload` and BetterModel reloads need a CE pack build.
