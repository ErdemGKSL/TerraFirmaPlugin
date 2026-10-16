---
name: model-authoring
description: How Claude creates and fixes 3D models for this project (BetterModel .bbmodel entity models, vanilla block/item model JSON). Write Python generators (rig library with standard bone names, per-species parameter files, mirrored parts, automatic box-UV packing), never hand-written model JSON. Validate with tools/models/tfpmodels/validate.py, render headlessly through the Blockbench web app in Playwright and look at the PNGs before claiming success, and convert TerraFirmaCraft's 128 Java AnimationDefinitions into .bbmodel keyframes with the verified axis rule. Use this whenever a task adds, ports or changes an animal/entity model, animation, block or item 3D shape, UV layout or texture template, or touches tools/models, even if the user only says "add a moose" or "the walk looks wrong".
---

# Model authoring (generate → validate → render → look → fix)

Toolchain: `tools/models/`. Research and alternatives: `docs/research/3d-model-generation.md`. Runtime rules for entity models: the `bettermodel` skill. Textures: the `image-requests` skill.

## Principles
1. **Never hand-write `.bbmodel`/model JSON.** Write or extend a *generator* (`tfpmodels/`) plus a small *parameter file*. Generators give symmetry, consistent bone names, packed UVs, deterministic UUIDs and clean diffs, and a fix in one place fixes every species.
2. **Always look before claiming done.** Run the validator, render with `render/render.mjs`, then **Read the PNGs**. Iterate up to ~5 times. Render angles and animation times relevant to the change (e.g. walk at 0, ¼, ½, ¾ of the cycle).
3. **Reuse TFC first.** 44 TFC `.bbmodel`s and 128 Java `AnimationDefinition`s exist. Import and convert them before authoring anything new (`tfc-asset-migration` decides what's imported).
4. AI 3D generators (Meshy, Tripo, TRELLIS...) are **reference only** (silhouettes), never output. Never use Hunyuan3D (its license excludes the EU).

## Generating a new entity model
```bash
cd tools/models
python3 -I -c "import sys; sys.path.insert(0,'.'); from tfpmodels.generate import main; sys.exit(main(['x','species/<id>.json','out']))"
node render/render.mjs out/<id>.bbmodel out/render walk 0,0.25,0.5,0.75
```
- **Species file** (`species/<id>.json`): `id` (BetterModel id `[a-z0-9_.]+`, prefix `tfp_`), `rig`, part sizes (`body`, `leg`, `neck`, `head`, `snout`, `ear`, `tail`), `extras` (`attach`: bone, `name`, `mirror`, `offset` **relative to the attach bone's pivot**, `size`, `rotation`) and `anim` tuning. The example is `species/tfp_example_bovine.json`.
- **Rigs** (`tfpmodels/rigs.py`) and their **bone-name contract**: `root → body → neck → h_head` (the `h_` prefix makes BetterModel follow look direction), legs `leg_fl/fr/bl/br`, `tail`; `*_l`/`*_r` pairs are mirrored and share UV (`uv_key`). Only `quadruped` exists so far. Add `biped_bird`, `fish`, `serpentine` the same way, keeping the naming contract, because the animation library and BetterModel rely on it.
- **Never name a bone with BetterModel hitbox tags** (`b_`, `ob_`, `p_`, `sp_`, `hitbox`); the validator rejects them.
- **UVs:** `uv.pack_box_uv` shelf-packs box UVs into a power-of-two texture. `generate` writes `<id>_uv_template.png` (one colour per face direction) and embeds it as the placeholder texture. To get the real texture, file an `IMAGES.md` request that attaches the template, size, face colours and TFC palette references, then embed the result.
- **Animation library** (`tfpmodels/anims.py`): `idle`, `walk` (diagonal gait), `eat` (`override: true`, so it layers over walk). Use BetterModel's exact names (`idle`, `walk`, `run`, `eat`, `sleep`, `attack`, `swim`, ...).
- **Rotation sense (Blockbench 5, verified by render):** `-x` tilts an upward part forward (−z) and pitches a forward-facing part down. Check signs with a render rather than reasoning about them.

## Converting TFC Java animations
```bash
python3 -I tools/models/tfc_anim/convert.py <in.bbmodel> <TFC Model.java>[,<More.java>] <out.bbmodel> JAVA_CONST=bm_name ...
```
- `parse_java_anims.py` reads `AnimationDefinition.Builder` constants. It parses all 128 TFC definitions (5957 keyframes) under `client/model/entity/*` and `client/animation/*`, e.g. camel animations live in `BactrianCamelAnimation.java`.
- **Axis rule (verified 2026-10-16):**
  - `.bbmodel` format < 5: Java values are stored as-is (Deer, Lion, Crocodile).
  - Format ≥ 5: negate **rotation x, rotation y, position x** (Mongoose).
  - Round-trip test: the converter reproduced 289 of 293 keyframes of TFC's own `.bbmodel` animations exactly. The 4 misses are Crocodile, where TFC's Java and Blockbench copies have drifted; that isn't a convention issue.
- Java bone names must equal `.bbmodel` group names. The converter warns and skips the rest (e.g. Deer's `DEER_RUN` targets a `tail1` bone the model doesn't have, so that channel is dropped).
- Rename to BetterModel names while converting (`DEER_WALK=walk`). Delete or rename the old `animation.deer.*` entries too, so they don't ship as dead weight.

## Validating
`python3 -I tools/models/tfpmodels/validate.py <files>` (strict, for our models), or with `--lenient` for imported TFC models (it skips UV-layout checks, since TFC reuses UV space on purpose). It checks:
- BetterModel id format, duplicate bone names, hitbox-tag bones;
- root-level cubes, non-cube elements;
- box-UV bounds and overlaps (strict mode only), very large cubes (>32 px);
- animations targeting missing bones, non-embedded textures.

Current TFC state: 28/44 pass `--lenient`. The rest have stale animation channels or huge windmill cubes. Fix these on import.

## Rendering
`node tools/models/render/render.mjs <model> <outDir> [anim] [t1,t2,...]` loads `https://web.blockbench.net/` (Blockbench 5.2.x) in Playwright Chromium with SwiftShader WebGL and saves `front_left`, `side`, `front` and `top` PNGs per time. It needs network access to web.blockbench.net. Override with `BLOCKBENCH_URL` (e.g. a pinned self-hosted build) and `PLAYWRIGHT_NODE_MODULES`. It can also round-trip vanilla block JSON via `Codecs.java_block` (see `tools/models/prototype/jb.mjs`). Side view: the right of the image is the model's front (−z).

## Block/item models (vanilla JSON)
Same principle: a small Python box DSL → JSON → render. Rotation limits depend on the oldest supported client. For **1.21.4**, use a single axis, 22.5° steps, within ±45°. Free angles arrived in 1.21.6, multi-axis rotation in 1.21.11. Prefer TFC's existing models (`tfc-asset-migration`). CraftEngine wiring: `craftengine-visuals-pack`.

## Definition of done for a model
Validator clean → renders reviewed at the relevant angles and times → texture from `IMAGES.md` embedded and re-rendered → in-game check through the `plugin-testing` self-test/showcase (BetterModel scale, pivots, Molang) → the generator inputs and outputs committed together.
