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

## Budget and allocation (§10, appearance-strategy.md)
- The verified explicit full cubes total only **197** (stone family, ores, wool, concrete, concrete powder, terracotta, glazed terracotta). TFC's full cubes (≈1125 graded ores + ≈210 rock variants + soils + wood) **exceed solid + 197 on 1.21.x**. The datagen allocator must:
  1. dedupe identical models;
  2. use an **audited, curated** list of further vanilla full-cube blocks (planks, logs/wood × axis, bricks, copper, coral, quartz, prismarine...), each checked for prediction and render layer;
  3. assign deterministically and emit **full state strings** (never `block:N` shorthand);
  4. emit `block_state_mappings` that hide real instances of every repurposed state, without repeating CE's internal lines;
  5. report used/available per pool and per MC version, and fail CI on overflow.

  If it still doesn't fit, **ask the user** (e.g. merge ore-grade visuals on 1.21.x only). Never degrade silently.
- The installed `mappings.yml` is frozen at first install, so pool sizes differ per server. After `CraftEngineReloadEvent`, check the pool candidate counts at runtime and warn on shortfall.
- First-time `auto_state` allocation can be nondeterministic with multi-threaded loading. Prefer explicit assignments from datagen, or seed `cache/visual_block_states.json`. Run `/ce clean-cache visual_block_states` after removing blocks.

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
