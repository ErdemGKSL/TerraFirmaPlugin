---
name: tfc-asset-migration
description: How to move TerraFirmaCraft assets (textures, block/item models, blockstates incl. multipart, NeoForge custom model loaders, item model predicates, lang files, sounds, GUI textures, Blockbench entity models, HUD art) into this plugin's CraftEngine / BetterModel / BetterHud content through the datagen tool, with attribution and validation. Use this whenever a task copies, converts, renames, re-textures or validates any TFC asset, adds a texture/model/sound/translation, writes or extends tools/datagen, or a block/item/entity "looks wrong" in game, even if the user only says "bring over the TFC textures" or "make it look like TFC".
---

# Migrating TFC assets

All TFC assets go through **`tools/datagen`**. It is a deterministic converter from a pinned TFC checkout to `content/`, and it never involves hand-editing generated files. Why it matters: the TFC tree has ~4500 blockstates, ~5600 item models, 366 models with NeoForge-only loaders and 519 multipart blockstates. Hand work doesn't scale, and it can't be re-run when TFC updates.

Read first: `tfc-feature-porting` (where assets belong in a port), `craftengine` and its sub-skills (how a block/item becomes CE YAML and how appearances are chosen), `bettermodel`, `betterhud`.

## Source inventory (TFC @ pinned commit)

| What | TFC path | Count / notes |
|---|---|---|
| Textures | `src/main/resources/assets/tfc/textures/{block,item,entity,gui,...}` | 32 MB; 26 `.mcmeta` (animated). `colormap/` is not shipped as-is (tint is handled by the biome plan). |
| Block models | `assets/tfc/models/block/**` | Many use vanilla parents; some use TFC parents like `tfc:block/ore` (base + `overlay` layer). |
| Item models | `assets/tfc/models/item/**` | 73 use `overrides` with TFC predicates (`tfc:heat`, ...) |
| Custom loaders | `"loader": ...` in 366 models | `tfc:plant` 154 (picks a model per season stage), `neoforge:fluid_container` 66, `neoforge:separate_transforms` 42, `tfc:trim` 36, `tfc:fluid_container` 24, `tfc:leaves` 24, `tfc:mold` 16, single ones for ingot piles, mold table, scraping |
| Blockstates | `assets/tfc/blockstates/**` | 519 multipart (rock walls 189, wood fences/etc. 121, plants 74, connected grass sides...) |
| Vanilla overrides | `assets/minecraft/{blockstates,models,...}` | TFC re-skins a few vanilla assets (e.g. `flower_pot`, `slime_block`). Check them against our repurposed vanilla states. |
| Lang | `assets/tfc/lang/*.json` (+ `resources/lang/`) | en_us, **tr_tr**, de_de, es_es, ja_jp, ko_kr, pl_pl, pt_br, ru_ru, ... |
| Sounds | `assets/tfc/sounds.json` + `sounds/**` | CC0 unless noted; `rock_slide_long_3` is CC-BY-4.0 |
| Entity models | `resources/entity_models/*.bbmodel` (44) | Blockbench sources for BetterModel |
| GUI art | `assets/tfc/textures/gui/**` | anvil, knapping, barrel, firepit, player_calendar/nutrition/climate, ... |
| Field guide | Patchouli book templates | Content source for our in-game guide |

## Pipeline (what datagen does, in order)

1. **Load** the TFC tree at the pinned commit, and record a hash of each input file in `content/.datagen-manifest.json`.
2. **Rename** the namespace `tfc:` → our namespace (`tfp:`, provisional) in every id, model `parent`, texture ref and lang key. Vanilla refs stay `minecraft:`.
3. **Textures:** copy them to the CE pack `resourcepack/assets/tfp/textures/**`, keeping relative paths and `.mcmeta`. Validate the PNGs: power-of-two/16-multiple sizes, animated strips matching their `.mcmeta`.
4. **Models:** copy, rewriting refs. Then resolve the non-vanilla constructs that vanilla clients can't read. See `references/model-conversion.md` for each one:
   - `tfc:plant` (seasonal stage models): keep each stage model, and expose the stage as a **block property** so each state gets a plain model. The season → stage logic moves to our behaviour.
   - `neoforge:separate_transforms`: pick the GUI vs in-hand model through the 1.21.4 item model definition (`select` on `display_context`).
   - `neoforge:fluid_container`, `tfc:fluid_container`, `tfc:mold`: precompute one item model per fluid/fill state that matters, selected with `range_dispatch`/`select` on custom model data. Or show contents only in the tooltip (record it as a divergence).
   - `tfc:leaves`, `tfc:trim`, `tfc:ingot_pile`, `tfc:double_ingot_pile`, `tfc:mold_table`, `tfc:scraping`: dynamic visuals. Use a CE `entity_renderer` (dynamic), or baked per-state models where the state space is small.
   - Item `overrides` with TFC predicates → 1.21.4 item model definitions (`range_dispatch` on custom model data that **we** set in outgoing packets or on the stack).
5. **Blockstates → CE YAML:** each TFC blockstate becomes a CE block's `properties` + `appearances` + `variants`.
   - **Variants-only** blockstates map directly.
   - **Multipart** blockstates have two options. *Flatten*: for every state combination, merge the applicable parts into one baked model. Feasible when the combination count is small and parts don't need conflicting rotations. Otherwise use the CE pattern of a real vanilla base state (hitbox) + `entity_renderer`. Overriding single states of a vanilla multipart block works (the client merges per state), but it is only practical for blocks with few states. Fences/walls have many connection states, so they need renderers.
   - The appearance (auto pool / explicit vanilla state / renderer) is chosen by the allocator. See `craftengine-visuals-pack` → `references/appearance-strategy.md`.
6. **Items → CE YAML:** item id, material, model path, `block_item` link, item settings derived from TFC data (size/weight, tool tier). The datagen tool owns the TFC-item → CE-item id table, which other code uses.
7. **Lang:** turn TFC JSON lang into CE `lang:` sections with renamed keys (client-side translations), for all locales including `tr_tr`. Server-side messages go to CE `translations:` or our own i18n.
8. **Sounds:** `sounds.json` → CE `sounds:` definitions, `.ogg` files copied. Credit CC-BY sounds in `NOTICE`.
9. **GUI art** → CE `images:` (font glyphs) for container titles, plus the "outside the inventory" button sprite. HUD art → BetterHud pack folder (`packs/tfp/assets/tfp/`).
10. **Entity models:** preprocess the `.bbmodel` files for BetterModel (animation renames, authored animations, variant textures) → jar resources, registered via `ModelAssetsEvent`. Rules are in the `bettermodel` skill.
11. **Reports:** visual allocation report, CE state count (→ `block.serverside-blocks`), list of assets with no target (must be empty, or listed as divergences), pack size.

## Rules
- **Generated output is never edited by hand.** Fix the converter or add an override file under `tools/datagen/overrides/` (a small, reviewed patch keyed by asset id) and regenerate.
- **Determinism:** the same TFC commit + same overrides must give byte-identical output. Sort everything, and don't embed timestamps.
- **Attribution:** copied TFC assets are EUPL-1.2 and covered by `NOTICE`. Any asset with another license (CC-BY sounds, MIT code) gets its own `NOTICE` line. Never copy assets from other mods or packs.
- **No vanilla collision:** TFC's own `assets/minecraft/**` overrides, and our repurposed vanilla states, must not target the same vanilla blockstate/model. The allocator checks this.
- **Pack size budget:** track total size per release. Strip unused textures; the reverse-reference check is in datagen.
- **Our art:** new art made for plugin-only UI (fake inventory button, HUD frames) lives in `content/art/` with its own license note. Keep it visually consistent with TFC.

## Validation
- datagen unit tests: id rename coverage, every model's parent and texture resolves, every blockstate state has exactly one appearance, no pool overflow, lang key coverage per locale.
- In-game: `/tfp debug showcase <family>` + `plugin-testing` → `references/visual-checklist.md`.
- When something "looks wrong", trace it back: CE appearance → generated model → TFC source model/loader → converter rule. Fix the rule, not the output.
