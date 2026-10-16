# 3D Model Generation for an AI Coding Agent (Minecraft-style): Research Report

*Researched 2026-10-16 for the TerraFirmaPlugin project (EUPL-1.2 Paper plugin porting TerraFirmaCraft). Targets: BetterModel `.bbmodel` entity models, vanilla Java block/item model JSON (via CraftEngine), and textures.*

**How to read this.** Every factual claim has a source URL. **[Verified locally]** means I ran it in this environment on 2026-10-16. **[Opinion]** and **[Speculation]** mark my own judgement. **[Unverified]** marks claims I could not confirm. Project-internal facts are cited by local path (for example the existing BetterModel research at `.claude/skills/bettermodel/references/research-report.md`).

---

## Table of contents

0. [Executive summary](#0-executive-summary)
1. [Direct authoring by the LLM (bbmodel / vanilla JSON)](#1-direct-authoring-by-the-llm)
2. [Blockbench automation (plugin API, headless, MCP servers, conversion, Animated Java)](#2-blockbench-automation)
3. [MCP servers for 3D in general (Blender, Unity, Godot, Minecraft)](#3-mcp-servers-for-3d-in-general)
4. [Text/image-to-3D generators and voxelization to cuboids](#4-text-to-3d--image-to-3d-and-voxelization)
5. [Textures and UV layout](#5-textures-for-3d-models)
6. [Animations](#6-animations)
7. [Validation and preview (with a working headless pipeline)](#7-validation--preview)
8. [Recommended pipeline for this project [Opinion]](#8-recommended-pipeline-for-our-project-opinion)
9. [Open questions / things I could not verify](#9-open-questions--unverified)
10. [Sources](#10-sources)

---

## 0. Executive summary

- **The highest-quality path for Minecraft-style models is for Claude to author the geometry itself as structured data**, using a Python/JS generator (parts, symmetry, bone hierarchy). It should **render the result headlessly** after every change and look at the images. Generative 3D (Meshy, Tripo, Rodin, TRELLIS.2, Hunyuan3D) outputs dense triangle meshes. Turning those into a clean ≤30-cube low-poly animal is lossy and usually looks worse than hand-composed boxes. Use them at most as a *reference* or a rough voxel blockout ([§4](#4-text-to-3d--image-to-3d-and-voxelization)).
- **Headless Blockbench works here.** [Verified locally] The Blockbench web app (`https://web.blockbench.net/`, v5.2.2) loads in the environment's Playwright 1.56.1 Chromium with SwiftShader WebGL. I generated a `.bbmodel` from Python, loaded it with `Codecs.project.load`, evaluated a walk animation at chosen times, and saved PNG renders. I also round-tripped a vanilla Java block model through `Codecs.java_block`. That gives a real "Blockbench as the renderer and validator" loop with **no desktop app and no MCP**. Scripts are in `tools/models/prototype/`.
- **Blockbench MCP servers exist and are active** (jasonjgardner GPL-3.0 with a headless stdio mode and a WebGPU renderer; sosadly MIT, 70 tools, desktop-only; adhi-jp MIT; sigee-min/bbmcp, which now appears to be "Ashfox"). Most need the Blockbench **desktop** app. The jasonjgardner **headless** server is the only one that edits files without the app, and its renderer needs a GPU and Node ≥ 23.6 ([§2.3](#23-mcp-servers-for-blockbench)).
- **TFC already contains most of the animation data.** The local TFC source has **122 `AnimationDefinition` constants in 40 model classes** under `client/model/entity/`. These are Blockbench-exported Java keyframe animations, so they can be **mechanically converted back into bbmodel keyframes**. Mind the axis-sign conventions. The remaining procedural `setupAnim` math (e.g. `Mth.cos(limbSwing*0.6662F)*1.4F`) maps cleanly onto Molang or sampled keyframes ([§6](#6-animations)).
- **Vanilla model JSON constraints depend on the lowest client version you support.** Free (non-22.5°) rotations arrived in 1.21.6 (25w16a). Multi-axis rotation and removal of the ±45° limit arrived in 1.21.11 (25w46a). Item-model elements are still documented as −45..45. If the plugin supports 1.21.4 clients, keep block models to single-axis 22.5° steps ([§1.4](#14-vanilla-java-blockitem-model-json)).
- **Textures:** have Claude produce a **UV template PNG** with labelled face rectangles from the model, plus a precise spec. The image model then paints *to the template*. Claude downsamples, quantizes and validates the result. The current OpenAI image API's **minimum canvas is 655,360 px** (~810×810). It cannot emit 64×32 directly, so a downscale and palette-snap step is mandatory ([§5](#5-textures-for-3d-models)).

---

## 1. Direct authoring by the LLM

### 1.1 The `.bbmodel` format: what is (and is not) specified

- Official docs: ".bbmodel files are JSON-based… mainly intended as an internal format," and "There is no complete specification of the JSON format at this point in time." The page recommends reading example files and the source `js/formats/bbmodel.js`. Breaking changes are possible. ([blockbench.net/wiki/docs/bbmodel](https://blockbench.net/wiki/docs/bbmodel))
- **Format version history** (same page):
  - **5.0 (Blockbench 5.0, 2025-10-12):** groups moved to a top-level `"groups"` array. `"outliner"` holds only the hierarchy as `{uuid, isOpen, children:[uuid|node]}`. **Position keyframes are no longer X-inverted, and rotation keyframes are no longer X/Y-inverted.** This matters for any converter.
  - **4.10:** relative texture paths changed. `name` is ignored and taken from the filename. Default textures are no longer written per face.
  - **4.9:** formats with `per_texture_uv_size` (Generic) use per-texture `uv_width`/`uv_height` instead of global `resolution`.
- **Top-level keys written by the current codec.** From the `compile()` function in [`js/formats/bbmodel.js`](https://raw.githubusercontent.com/JannisX11/blockbench/master/js/formats/bbmodel.js) (`FORMATV = '5.0'`, repo `package.json` version 5.2.2):
  - `meta: {format_version, model_format, box_uv}`
  - project properties such as `name` and `model_identifier`
  - `resolution: {width, height}`
  - `elements[]`, `groups[]`, `outliner[]`
  - `textures[]` (each `getSaveCopy()`, plus `source` as a **PNG data URL** and `internal: true` when embedding; `relative_path` when saved next to files)
  - optional `texture_groups`, `collections`, `animations[]`, `animation_controllers[]`, `animation_variable_placeholders`, `display`, `reference_images`, `export_options`, `history`
- **Element (cube) fields** (observed in the files we round-tripped): `name, type:"cube", uuid, from[3], to[3], origin[3], rotation[3]` (optional), `inflate`, `box_uv`, `uv_offset[2]` (box UV), `faces{north,east,south,west,up,down: {uv[4], texture}}`, `mirror_uv`, `color`, `autouv`, `rescale`, `locked`, `render_order`. [Verified locally: Blockbench 5.2.2 loads a cube with exactly these fields and reports it in `Cube.all`.]
- **Group fields:** `name, uuid, origin[3], rotation[3], color, export, mirror_uv, isOpen, locked, visibility, autouv`. [Verified locally]
- **Animation fields:** `uuid, name, loop ("once"|"loop"|"hold"), override, length, snapping, anim_time_update, blend_weight, start_delay, loop_delay`, plus `animators: { <group uuid>: {name, type:"bone", keyframes:[{channel:"rotation"|"position"|"scale", data_points:[{x,y,z}], time, interpolation:"linear"|"catmullrom"|"bezier"|"step", uuid}]}}`. Data-point values are strings, so **Molang expressions** are allowed. [Verified locally: a `math.sin(query.anim_time*360)*5` data point loaded and evaluated.]
- **Ground truth for this project:** the 44 TFC files in `scratchpad/src/tfc/resources/entity_models/`. My scan of them:
  - formats: `modded_entity` ×34, `bedrock` ×10
  - format versions: 4.0–5.0
  - **all box UV**
  - 33 already contain animations
  - texture sizes 16²…256²

  BetterModel's parser picks the axis convention from `meta.format_version` (major ≥5 means BLOCKBENCH_5). Source: local `.claude/skills/bettermodel/references/research-report.md` §4, citing BetterModel `api/data/raw/ModelMeta.java`.

### 1.2 BetterModel-specific constraints (summary, already researched in-repo)

These come from `.claude/skills/bettermodel/SKILL.md` and its `references/research-report.md` (BetterModel 3.5.1). Public repo: [github.com/toxicity188/BetterModel](https://github.com/toxicity188/BetterModel), MIT, which advertises cubes, meshes, locators, Molang and IK.
- Every cube must sit in a group (root-level cubes are not rendered). Each group becomes one `item_display` bone.
- Any cube rotation works: BetterModel splits a bone into sub-models per rotation. Clients older than 1.21.4 (via ViaVersion) lose free rotation.
- Bone-name tags: `h_` (head follows look), `hi_`, `tag_`, `glow_`. Avoid hitbox or seat tags (`b_`, `ob_`, `p_`, `sp_`, `hitbox`) in this project.
- Built-in animation names: `idle`, `walk`, `spawn`, `damage`, `death`, `jump`, `idle_fly`, `walk_fly`. Project extras: `run`, `eat`, `sleep`, `attack`, `swim`.
- Molang keyframes (`query.anim_time`) are supported.
- Don't mix per-texture UV sizes ([issue #371](https://github.com/toxicity188/BetterModel/issues/371) per the local report). Avoid float UV sizes (#386).
- Public docs on bone tags and loop types (third-party-generated docs, lower trust): [mintlify.com/toxicity188/BetterModel/guides/creating-models](https://mintlify.com/toxicity188/BetterModel/guides/creating-models).

### 1.3 Can an LLM author good models directly? Evidence and limits

- **No rigorous public benchmark** for "LLM writes Blockbench JSON" turned up. The closest evidence:
  - **LLaMA-Mesh** (NVIDIA, arXiv 2411.09595): serializing meshes as plain-text vertices/faces lets a fine-tuned LLM generate meshes. Its stated limits are context length and geometric precision. ([arXiv](https://arxiv.org/abs/2411.09595v1))
  - **3D Building Generation in Minecraft via LLMs** (arXiv 2406.08751): with suitable prompts, LLMs can produce complete Minecraft buildings. This covers block placement, not entity models. ([arXiv](https://arxiv.org/abs/2406.08751v1))
  - **ShapeLib** (arXiv 2502.08884): LLMs design *libraries of parametric shape programs* over cuboid collections. This supports the "procedural generator" approach below. ([arXiv](https://arxiv.org/abs/2502.08884))
  - **sosadly/blockbench-mcp** claims a full GeckoLib grizzly bear (model, texture, walk/run/sleep/attack) "from a single prompt" via its tools and screenshot feedback. This is a vendor claim. ([lobehub listing](https://lobehub.com/mcp/sosadly-blockbench-mcp), [GitHub](https://github.com/sosadly/blockbench-mcp))
- **[Opinion] Quality limits observed in practice:**
  1. LLMs are good at *proportions and topology described in words* ("4 legs 4×12×4, body 12×10×18, head offset forward"). They are bad at *raw coordinate bookkeeping across hundreds of numbers*, and miss a pivot or mirror one leg wrongly.
  2. Without visual feedback, errors go unnoticed (inverted pivots, faces pointing the wrong way, UV islands overlapping).
  3. Texture painting by numbers (writing pixel arrays) is the weakest step.
- **[Opinion] Tips that materially improve LLM authoring:**
  - **Never hand-write the bbmodel JSON.** Write a *generator script* (Python) with a tiny DSL: `part(name, size, pivot, offset, parent, mirror=True, inflate=0)`. The script emits UUIDs, box-UV offsets, outliner and animators. Keep the script in `tools/datagen` so models are reproducible and diffable.
  - **Compose from primitives with symmetry:** define left-side parts and mirror (`x → -x`, swap `left_`/`right_` names, `mirror_uv`).
  - **Bone hierarchy convention** (matches TFC/vanilla quadrupeds): `root → body → {neck → head → {ears, horns/antlers, jaw}, tail…}, leg_front_left, leg_front_right, leg_hind_left, leg_hind_right`. Pivots go at the joint (top of leg, base of neck). Vanilla quadruped legs pivot at the hip, as TFC's `rightHindLeg.xRot` math shows.
  - **Units:** 16 units = 1 block. Model faces −Z (north) in Blockbench. Keep feet at y=0.
  - **Auto box-UV packing:** compute `uv_offset` with a simple shelf packer over each cube's box-UV footprint, `(2·(d+w)) × (d+h)`. Then let Blockbench's own validation (or the checks in §7) catch overlaps.
  - **Re-use the 44 TFC models** as style references and as *templates*: similar animals (caribou → reindeer variants, wolf → jackal) should be produced by parametric edits of an existing model, not from scratch.

### 1.4 Vanilla Java block/item model JSON

From the Minecraft Wiki "Model" page ([minecraft.wiki/w/Model](https://minecraft.wiki/w/Model)):
- `from`/`to` must be within **−16..32** (a 3×3×3-block region). Blockbench lists the Java Block/Item size limit as 3×3×3 ([Blockbench formats](https://www.blockbench.net/wiki/blockbench/formats)).
- **Rotation history (block elements):**
  - **14w07a:** one axis, 22.5° steps, −45..45.
  - **25w16a (1.21.6):** the 22.5°-multiple restriction was removed.
  - **25w46a (1.21.11):** multiple axes (`x`, `y`, `z` fields) allowed and the ±45° limit removed.
- **Item model elements** are still documented as "45 through −45" with `origin, axis, angle, rescale` only.
- `light_emission` (0–15) since 24w33a (1.21.2).
- In 26.3 snapshot 3, `shade` was removed and `shade_direction_override` added.
- Blockbench-only fields (e.g. `texture_size`) are ignored by Minecraft.
- **[Verified locally]** Blockbench 5.2.2 `Codecs.java_block.compile()` wrote `"format_version": "26.3"` and `"credit": "Made with Blockbench"` into the exported JSON. Blockbench targets a selectable MC version. [Unverified] Exactly which rotation syntax it emits for older targets.
- **Implication [Opinion]:** CraftEngine block visuals reach every client version the server accepts. Restrict generated block/item models to **single-axis, 22.5° steps within ±45°** unless the project drops <1.21.6 (or <1.21.11) clients. Add a linter for this (§7).

---

## 2. Blockbench automation

### 2.1 Plugin / JS API

- Plugins register with `Plugin.register(id, {title, author, icon, version, variant: 'desktop'|'web'|'both', min_version, onload, onunload…})`. The wiki tutorial points to the **Plugin API reference at [web.blockbench.net/docs](https://web.blockbench.net/docs)** and says the TypeScript types are more up to date than the wiki. ([blockbench.net/wiki/docs/plugin](https://www.blockbench.net/wiki/docs/plugin))
- Core globals usable from scripts [Verified locally in the web app]:
  - `Codecs` (`project, java_block, bedrock, bedrock_old, modded_entity, optifine_entity, obj, gltf, fbx, collada, stl, skin_model, image, bedrock_entity_file, bedrock_voxel_shape`)
  - `Formats` (`free` = Generic, `java_block, bedrock, bedrock_block, bedrock_old, modded_entity, optifine_entity, optifine_part, skin, image`)
  - `Cube.all`, `Group.all`, `Texture.all`, `Animation.all`, `Timeline.setTime()`, `Animator.preview()`, `Preview.selected`, `newProject(format)`
- Plugin index (machine-readable): [blockbench-plugins `plugins.json`](https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins.json). Relevant entries (fetched 2026-10-16):

| Plugin id | Title / version | Relevance |
|---|---|---|
| `animated_java` | Animated Java 1.11.0 (min BB 5.1.4, desktop) | Display-entity animation for vanilla Java ([site](https://animated-java.dev), [repo](https://github.com/animated-java/animated-java)) |
| `gltf_to_minecraft` | glTF to Minecraft 0.1.2 (2026-08-01) | Converts glTF/Sketchfab models **into cubes** (GeckoLib / CPM) with bones, textures, animations ([repo](https://github.com/MopicMP/gltf-to-minecraft)) |
| `gltf_importer` | glTF Importer 1.2.1 | Imports .gltf/.glb as meshes (Generic) |
| `vox_importer` | Voxel Importer 1.2.2 (JannisX11) | Imports MagicaVoxel `.vox` |
| `optimize` | Optimize 1.2.8 | Hides concealed faces |
| `animation_to_java` | Animation to Java Converter 1.2.0 | Blockbench anim → vanilla 1.19+ `AnimationDefinition` code (the format TFC uses) |
| `easings`, `animated_platforms` | | Animation helpers |
| `mod_utils` | Mod Utils 1.7.1 | Tabula import, VoxelShape export |
| `minecraft_entity_wizard` | 1.8.3 | Bedrock entity starters from vanilla presets ([MS Learn](https://learn.microsoft.com/en-us/minecraft/creator/documents/minecraftentitywizard)) |

### 2.2 Headless / CLI

- **No official CLI or headless mode.** Request [issue #1764](https://github.com/JannisX11/blockbench/issues/1764) asks for headless conversion. I found no implementation.
- **Workable headless route [Verified locally]:** drive the **web app** in Playwright Chromium. In this environment `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers` (chromium-1194) and the global npm `playwright@1.56.1` are available. Launch flags `--use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader` give WebGL. Steps that worked:
  1. Wait for `Codecs.project` → `Codecs.project.load(json, {path})`.
  2. Read `Cube.all/Group.all/Animation.all`.
  3. `Codecs.project.compile({raw:true})` returns `meta.format_version: "5.0"`.
  4. Select the animation, `Timeline.setTime(t)`, `Animator.preview()`, then `Preview.selected.loadAnglePreset({position,target})`, `render()`, `canvas.toDataURL()`.
  5. For Java blocks: `newProject(Formats.java_block)` → `Codecs.java_block.parse(json)` → `compile()`.

  Each run downloads the web app (needs network through the proxy). [Opinion] Pin it by self-hosting a Blockbench build (GPL-3.0) for reproducibility.
- Third-party converters:
  - **gltf2bbmodel** (Rust CLI): glTF → bbmodel 5.0 with cube detection, groups, animations. No skinning or shape keys. License not stated. ([codeberg/zff.dev](https://zff.dev/PROMETHIA/gltf2bbmodel))
  - **bbmodel2bam** (bbmodel → Panda3D, new) ([ecosyste.ms](https://awesome.ecosyste.ms/projects/github.com%2Fthetestgame%2Fbbmodel2bam))
  - **bench-3d** (npm, "modeling engine extracted from Blockbench"; headless use unverified) ([jsdelivr README](https://cdn.jsdelivr.net/npm/bench-3d@1.0.0/README.md))

### 2.3 MCP servers for Blockbench

| Project | License | How it works | Notes |
|---|---|---|---|
| [jasonjgardner/blockbench-mcp-plugin](https://github.com/jasonjgardner/blockbench-mcp-plugin) | GPL-3.0 | Desktop plugin (HTTP `localhost:3000/bb-mcp`, unauthenticated) **and a headless stdio server** that edits `.bbmodel` without Blockbench | ~532 stars. Headless tools include `bbmodel_create`, `bbmodel_edit`, `bbmodel_validate`, `bbmodel_validate_animations`, `bbmodel_sample_pose`, `bbmodel_render`, `bbmodel_contact_sheet`, `bbmodel_export_java_block`, `bbmodel_export_bedrock_geometry`. Run with `npx -y github:jasonjgardner/blockbench-mcp-plugin --root ./models`. The renderer needs **Node ≥ 23.6 and a GPU** (WebGPU/Dawn, ~130 MB download). Bezier is treated as linear and Molang channels are skipped in checks. Writes `ai_used`/`ai_agents` fields unless `--no-ai-disclosure`. ([headless README](https://raw.githubusercontent.com/jasonjgardner/blockbench-mcp-plugin/main/headless/README.md)) A companion skills repo: [blockbench-mcp-project](https://github.com/jasonjgardner/blockbench-mcp-project) |
| [sosadly/blockbench-mcp](https://github.com/sosadly/blockbench-mcp) | MIT | Bridge plugin in **desktop** Blockbench ≥4.8 (needs Node `net`, so the web app is unsupported), stdio server | 70 tools: geometry, rigging, UV/pack, animation, `screenshot`/`screenshot_views`, `check_model`, `execute_script`. Formats: Java block/item, Bedrock, GeckoLib, Generic |
| [adhi-jp/minecraft-blockbench-mcp](https://github.com/adhi-jp/minecraft-blockbench-mcp) | MIT | stdio adapter + desktop plugin over loopback WebSocket | Java block/item + GeckoLib. Offscreen `capture_screenshot`, `capture_geckolib_animation_frame`. Very new (0 stars at fetch) |
| [sigee-min/bbmcp](https://github.com/sigee-min/bbmcp) | MIT (per sidebar) | Listed on [mcpmarket](https://mcpmarket.com/server/bbmcp) as a Blockbench plugin with UV atlas automation and PNG previews | **The GitHub URL now shows "Ashfox"**, an "assets as code" DSL compiling `.ashfox` → GLB/PNG/WAV/resource packs. Repurposed or renamed; treat as unstable |
| [enfp-dev-studio/blockbench-mcp](https://github.com/enfp-dev-studio/blockbench-mcp) | MIT | Node server + plugin | One commit. Effectively a prototype |

**Security:** jasonjgardner's HTTP endpoint has no auth and exposes a `risky_eval` (arbitrary JS) tool that can be disabled ([repo](https://github.com/jasonjgardner/blockbench-mcp-plugin)).

### 2.4 Format conversion

- Blockbench: *File → Convert Project* (e.g. to Generic Model). Documented by Figura, which requires Generic ([Figura docs](https://docs.figuramc.org/start_here/BlockBench)). The same can be scripted inside the headless web app. [Unverified] Exact API call: `Format.convertTo()` exists in the codebase family, but I did not test it.
- BetterModel reads TFC's `modded_entity`/`bedrock` files as-is per the local report. Converting to `free` is optional.
- Box UV vs per-face: box UV forces the vanilla skin-style layout and one texture size per model. Per-face allows per-face placement and textures. ([Figura wiki](https://wiki.figuramc.org/index.php/Blockbench))

### 2.5 Animated Java

Animated Java renders animated models in **vanilla** Java via item/block/text display entities, exporting a data pack plus an optional resource pack, with Molang and variants ([repo](https://github.com/animated-java/animated-java), [animated-java.dev](https://animated-java.dev)). Version 1.11.0, desktop-only, min Blockbench 5.1.4 ([plugins.json](https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins.json)). License not visible on the fetched page. **[Opinion] Not needed here.** BetterModel already plays bbmodel animations at runtime from a Paper plugin, and Animated Java's datapack-function runtime would duplicate it.

---

## 3. MCP servers for 3D in general

- **Blender MCP** ([ahujasid/blender-mcp](https://github.com/ahujasid/blender-mcp), MIT)
  - Architecture: Blender addon socket server on `localhost:9876` (no auth; runs arbitrary Python) plus a Python MCP server via `uv`. Needs Blender ≥3.0 and Python ≥3.10.
  - Tools: `execute_blender_code`, `look`, `get_scene_info`, `generate_3d` (Hyper3D Rodin / Hunyuan3D / Tripo), `search_assets`/`import_asset` (Poly Haven CC0, Sketchfab, Poly Pizza ~69% CC-BY).
  - Telemetry: anonymous usage telemetry is on by default (`DISABLE_TELEMETRY=true`). Content collection is opt-in, and the [terms](https://raw.githubusercontent.com/ahujasid/blender-mcp/master/TERMS_AND_CONDITIONS.md) grant the author a perpetual license to that data when enabled.
  - **[Opinion]** Useful only for mesh→voxel experiments, not for Minecraft authoring.
- **Unity:** [CoplayDev/unity-mcp](https://glama.ai/mcp/servers/@CoplayDev/unity-mcp) (MIT; version numbers conflict between listings). **Godot:** several community servers, e.g. Funplay Godot MCP ([listing](https://claudemarketplaces.com/mcp/funplayai/funplay-godot-mcp)) and VberAI ([listing](https://mcpservers.org/pt-BR/servers/vberai/godot-mcp)). Neither is relevant for Minecraft asset authoring.
- **Minecraft gameplay MCPs:**
  - [yuniko-software/minecraft-mcp-server](https://github.com/yuniko-software/minecraft-mcp-server): a Mineflayer bot, tested 1.21.8/1.21.11. It places blocks in a world, which is not useful for models.
  - Possibly useful for **in-game visual QA**: an agent bot could stand next to a spawned animal. [Speculation] Mineflayer cannot take rendered screenshots by itself.
- **Pixel-art MCP:** the [PixelLab MCP](https://mcpservers.org/servers/pixellab-code/pixellab-mcp) generates pixel-art characters, animations and tilesets (commercial API token). [Unverified] Fitness for UV-mapped textures. It targets 2D sprites.

---

## 4. Text-to-3D / image-to-3D and voxelization

### 4.1 Current generators (verified existence; versions as reported)

| Tool | Status / version | License / access | Source |
|---|---|---|---|
| Meshy | Meshy-6 GA 2026-01-18 (secondary). **Has a dedicated Text-to-Voxel API** returning **GLB + `.vox`**. `art_style` deprecated for Meshy-6 on 2026-01-22 | Commercial API, credits (Text-to-voxel listed at 10 credits in API intro) | [Text to Voxel docs](https://docs.meshy.ai/api-text-to-voxel), [changelog](https://docs.meshy.ai/en/api/changelog), [API intro](https://docs.meshy.ai/api-introduction), [3daistudio review](https://www.3daistudio.com/blog/best-3d-model-generation-apis-2026) |
| Tripo | v3.1 (secondary). **Stylize** post-process with styles `lego`, `voxel`, `voronoi`, `minecraft` and `block_size` (default 80) per third-party client code | Commercial API | [ComfyUI tripo_api.py mirror](https://huggingface.co/spaces/multimodalart/wan-2-2-first-last-frame/blob/43f723e4cdaa81bd80f041a7d0741fe441cc984c/comfy_api_nodes/apis/tripo_api.py), [Tripo stylization](https://www.tripo3d.ai/zh-Hant/features/ai-model-stylization) |
| Hyper3D Rodin | Gen-2.5. **Sources conflict on the date** (Nov 2025 vs May 2026). GLB/FBX/OBJ/USDZ/STL | Commercial API | [Scenario help](https://help.scenario.com/articles/3791851266-rodin-hyper3d-gen-2-5-models-the-essentials), [80.lv](https://80.lv/partners/hyper3d) |
| Microsoft TRELLIS.2 | 4B params, image→3D, GLB with PBR. ≥24 GB NVIDIA GPU, Linux | **MIT** | [GitHub](https://github.com/microsoft/TRELLIS.2), [HF card](https://huggingface.co/microsoft/TRELLIS.2-4B/blob/main/README.md) |
| Tencent Hunyuan3D 2.1 | Released 2025-06-13, shape (3.3B) + PBR paint (2B). 10/21/29 GB VRAM | **Tencent Hunyuan 3D 2.1 Community License: "DOES NOT APPLY IN THE EUROPEAN UNION, UNITED KINGDOM AND SOUTH KOREA"** | [GitHub](https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1), [LICENSE](https://raw.githubusercontent.com/Tencent-Hunyuan/Hunyuan3D-2.1/main/LICENSE) |
| Hunyuan 3D 3.0 | Announced 2025-09-16, hosted engine/API. Open weights unconfirmed | Tencent Cloud | [Tencent](https://www.tencent.com/en-us/articles/2202235.html), [Notebookcheck](https://www.notebookcheck.net/Tencent-s-new-Hunyuan-3D-3-0-brings-realistic-AI-model-generation-to-developers-and-creators.1116964.0.html) |
| Stability Stable Fast 3D / SPAR3D | Image→3D | Stability Community License (free under US$1M revenue) | [SF3D card](https://huggingface.co/cocktailpeanut/sf3d), [The Decoder](https://the-decoder.com/stability-ais-new-3d-model-aims-for-real-time-generation/) |

**Licensing note for an EUPL project [Opinion]:** Hunyuan3D's licence excludes the EU/UK/KR, so avoid it for an EU-licensed open-source project. TRELLIS.2 (MIT) is the cleanest open option. Commercial APIs (Meshy/Tripo/Rodin) need their output-ownership terms checked before assets enter an EUPL repo. [Unverified] I did not read their ToS.

### 4.2 Mesh → Minecraft cuboid model: pipelines

1. **Mesh → voxels:** `trimesh.voxel.creation.voxelize(mesh, pitch, method='subdivide'|'ray'|'binvox')` ([trimesh docs](https://trimesh.org/trimesh.voxel.creation.html)). The interior fill assumes watertight meshes, and TRELLIS.2 output may have small holes ([3daistudio](https://www.3daistudio.com/blog/is-trellis-2-free-pricing-how-to-get-started)). Meshy's Text-to-Voxel skips this step by emitting `.vox` directly.
2. **Voxels → boxes:** greedy box decomposition (grow along x, then y, then z over filled, unvisited voxels) gives disjoint axis-aligned boxes ([arXiv 2607.06311 description](https://arxiv.org/pdf/2607.06311)). Greedy *quad* meshing ([block-mesh](https://docs.rs/block-mesh), [binary-greedy-meshing](https://github.com/cgerikj/binary-greedy-meshing)) merges faces, not volumes, so it is the wrong primitive for bbmodel cubes. Merge per *colour region* if each voxel colour must stay a texture pixel.
3. **Boxes → bbmodel:** one cube per box. Per-face UV pointing at a generated atlas (1 px per voxel face), or a solid colour per box. Then **segment into bones** (legs/head/tail) so it can animate. The automatic route has no notion of joints, so a human or LLM step is needed.
4. **Ready-made:**
   - **glTF to Minecraft** Blockbench plugin (2026-08): converts cube-like parts, replaces non-box shapes by bounding boxes, and reports the "approximated share" (≥30% = poor fit) ([about.md](https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins/gltf_to_minecraft/about.md))
   - **Voxel Importer** for `.vox` (plugins.json)
   - **ObjToSchematic** (OBJ → world structures, not models; unmaintained) ([ecosyste.ms](https://awesome.ecosyste.ms/projects/github.com%2FLucasDower%2FObjToSchematic))
   - **No ready-made OBJ → Java block-model-JSON cuboid tool found.**

**[Opinion] Verdict:** a voxelized AI mesh at entity scale (~32 px tall) gives hundreds of irregular boxes, staircase silhouettes and no joints. That is far from the clean 10–40-cube, box-UV style of TFC/vanilla mobs. BetterModel costs one display per *bone*, not per cube, but the pack size and visual noise still grow. For blocks (anvil, quern, barrel) generated meshes are useless: these are 3–15 hand-placed boxes. **Use generators only as a 3D reference** (render the mesh from 4 views, then let Claude author cubes matching the silhouette), or for one-off organic props.

### 4.3 "AI Minecraft model" tools

Searches for "AI blockbench model generator" / "minecraft model AI" returned only the MCP servers above, skill packs (e.g. [hairyf minecraft-model skill](https://www.skills.sh/hairyf/skills/minecraft-model), [jasonjgardner blockbench-modeling skill](https://www.vibeindex.ai/skills/jasonjgardner/blockbench-mcp-project/blockbench-modeling)), Meshy Text-to-Voxel, Tripo's `minecraft` stylize option, and human freelancers. **I found no dedicated, verifiable "text → rigged Minecraft entity bbmodel" service.**

---

## 5. Textures for 3D models

- **Box UV:** each cube's texture footprint is the vanilla skin "cross" layout at `uv_offset`, width `2(w+d)`, height `d+h`. Easy to paint, but one texture size per model and no meshes ([Figura wiki](https://wiki.figuramc.org/index.php/Blockbench)). All 44 TFC models use box UV (local scan).
- **Per-face UV:** arbitrary rectangles per face, multiple textures. Better for blocks (CraftEngine models reference vanilla-style 16×16 block textures by face).
- **Feeding an image model** (the imagegen MCP, see the `image-generation` skill) **[Opinion]:**
  1. Claude renders a **UV template PNG** at the final size with each face rectangle filled with a flat, distinct colour and labelled (e.g. "head-front"), plus an upscaled (×16) copy for the image model.
  2. The image spec (`art/raw/<id>/notes.md`) includes the template, the face list, and a palette drawn from existing TFC textures of similar animals.
  3. After generation, Claude **downscales with nearest-neighbour or area averaging to the exact size**, quantizes to the palette (≤ ~32 colours), and re-imposes alpha outside UV islands.
  4. Claude re-renders the model headlessly and inspects for seams.
- **OpenAI image API facts** ([developers.openai.com image guide](https://developers.openai.com/api/docs/guides/image-generation), fetched 2026-10-16):
  - Lists models `gpt-image-2.5-sunburst` and `gpt-image-2.5-flare`.
  - `background: "transparent"` with png/webp.
  - Custom sizes must be multiples of 16, total **655,360–8,294,400 px**.
  - Reference-image editing and masks (masks are prompt-guided, not exact).
  - **Consequence:** generate at e.g. 1024×512 for a 64×32 texture (×16) and downscale. Do not expect pixel-exact UV adherence. Template-guided edits work better than free generation. [Speculation, untested here]
- **Pixel-art constraints [Opinion]:**
  - Match TFC's texel density: 1 texel per model unit, never mixed sizes within a model (BetterModel issue #371).
  - Hard edges; no anti-aliasing at island borders.
  - Leave 1 px of island padding or duplicate edge pixels to avoid bleeding at mipmaps.
- Pixel-art specific generators: the PixelLab MCP (2D sprites, above).

---

## 6. Animations

### 6.1 Data model

- Keyframes: `animators[<bone uuid>].keyframes[{channel, time, data_points[{x,y,z}], interpolation}]` (§1.1).
- Bedrock-style semantics: `loop` (bool or `"hold_on_last_frame"`), `animation_length` defaults to the last keyframe, units of 1/16 block ([bedrock.dev Schemas](https://bedrock.dev/docs/stable/Schemas.md), [Animations 1.17](https://bedrock.dev/docs/1.17.0.0/1.17.0.2/Animations)).
- **Molang:** `query.anim_time` is the current time. Trig functions use **degrees**. Variables use `v.x = …;`. glTF/FBX exports bake expressions to keyframes. Bedrock/GeckoLib keep them ([Blockbench Animation Expressions](https://www.blockbench.net/wiki/guides/animation-expressions), [Bedrock Wiki math animations](https://wiki.bedrock.dev/visuals/math-based-animations)).
- BetterModel supports Molang keyframes, with "some features don't work". Interpolation types: linear, catmull, bezier, step (local report §4).

### 6.2 Porting TFC's Java animation code (local findings)

- `scratchpad/src/tfc/src/main/java/net/dries007/tfc/client/model/entity/` contains 70+ model classes, with **122 `AnimationDefinition` constants across 40 files**. Example: `MooseModel.MOOSE_RUN = AnimationDefinition.Builder.withLength(0.958f).looping().addAnimation("neck", new AnimationChannel(Targets.ROTATION, new Keyframe(0.25f, degreeVec(...), LINEAR)…`.
- This is the vanilla 1.19+ keyframe API that the Blockbench **Animation to Java Converter** plugin emits (plugins.json). **Porting is a parser job, not an art job:** regex or JavaParser over the builder chain gives `(bone, channel, time, vec, interpolation)`. Emit animators keyed by group uuid, matching bone names to the bbmodel groups.
- **Axis/sign caveat:**
  - Vanilla `KeyframeAnimations.degreeVec` / `posVec` and Blockbench differ in sign on some axes. bbmodel 5.0 changed the X-position and X/Y-rotation inversion ([bbmodel docs](https://blockbench.net/wiki/docs/bbmodel)).
  - **[Opinion] Validate empirically:** take one TFC model that has *both* an embedded bbmodel animation and the Java constant, convert, and diff sampled poses before batch-converting.
- **Procedural `setupAnim`** (non-keyframe), e.g. legs `xRot = cos(limbSwing*0.6662)*1.4*limbSwingAmount`:
  - `limbSwing` advances ~ distance walked. At a nominal walking speed, `limbSwing ≈ k·t`. The 0.6662 rad/unit cycle gives period `2π/0.6662 ≈ 9.43` limbSwing units.
  - Express as Molang `math.cos(query.anim_time * F) * 80.2 * A` (1.4 rad ≈ 80.2°, `A` = chosen amplitude ≈ 0.6–1.0, `F` in degrees/s), or sample into ~8 linear keyframes per cycle.
  - [Opinion] Prefer sampled keyframes: they are easier to preview, validate and edit, and avoid Molang edge cases in BetterModel.
  - Phase rules: diagonal legs in phase (FL with HR), offset π.
  - Head look and pitch are handled by BetterModel's `h_` tag, not animation.
- **New animations** (eat/sleep/attack) [Opinion]: write them as a *parametric library* over the standard quadruped rig:
  - `eat`: neck/head pitch +40–60° with a 2–3 Hz jaw/head bob, then return
  - `sleep`: body lowered by leg height, legs folded ±80–90°, head rotated sideways, hold
  - `attack`: 0.2 s wind-up, 0.1 s strike, recovery
  - `idle`: tail sway and ear flick with 4–6 s period sines

  Tune per species via bone-name maps and amplitudes.

### 6.3 Procedural walk cycles: rules of thumb [Opinion]

Use a loop length equal to one stride (0.8–1.2 s for livestock), legs ±25–35°, and a body bob of ~0.5 px at 2× stride frequency. Head counter-bobs slightly. Ensure the first and last keyframes are equal for seamless loops. Validate that no foot goes below y=0 (the jasonjgardner `bbmodel_validate_animations` performs exactly this "sinking/detaching" check).

---

## 7. Validation & preview

### 7.1 Renderers usable headlessly

1. **Blockbench web app in Playwright** — [Verified locally], see §2.2. It is the most faithful, because it is the same code that authored the format, and it covers bbmodel, Java block JSON, animations and Molang. Proof-of-concept files:
   - `.../scratchpad/bbtest/gen.py`: Python → bbmodel 5.0 with box-UV cubes, 7 groups, embedded PNG, `walk` animation incl. a Molang channel
   - `.../scratchpad/bbtest/render.mjs`: load, count, round-trip compile, pose at time *t*, PNG
   - `.../scratchpad/bbtest/jb.mjs`: Java block model parse/compile
   - Output images `cow_walk.png`, `cow_t0.png` (legs visibly swung at t=0)
2. **jasonjgardner headless `bbmodel_render` / `bbmodel_contact_sheet`:** WebGPU, needs a GPU and Node ≥23.6 ([README](https://raw.githubusercontent.com/jasonjgardner/blockbench-mcp-plugin/main/headless/README.md)). Our Node is 22.22, so it is likely unusable here. [Unverified]
3. **deepslate** (misode, WebGL; renders block structures using resource packs) for vanilla block/item JSON ([GitHub](https://github.com/misode/deepslate)). **minecraft-render-py** / **block-model-renderer** are alternatives ([hexdoc-dev](https://github.com/hexdoc-dev/minecraft-render-py), [block-model-renderer docs](https://cdn.jsdelivr.net/npm/block-model-renderer@2.1.0/docs/node.md)).
4. **Plain three.js** in the same headless Chromium (custom loader for bbmodel cubes). [Opinion] Only worth it if the Blockbench web dependency becomes a problem.

### 7.2 Automated checks (implement in `tools/datagen`, Python) [Opinion]

- **Schema:** `meta.format_version` == "5.0"; every element type `cube`; every cube referenced exactly once in the outliner; **no root-level cubes**; unique bone names; names `[a-z0-9_]`; no BetterModel hitbox or seat tags.
- **Animations:**
  - every animator name/uuid resolves to a group
  - required set present (`idle`, `walk`, + project names)
  - loop animations have equal first and last poses
  - sampled poses keep the lowest foot at y≈0 ±0.5 px
  - joint rotations within plausible limits
- **Geometry:** model bounding box vs the carrier mob hitbox (±20%); symmetric pairs mirror within 0.01; no zero-thickness cubes unless intended (planes); `inflate` within ±1.
- **UV/texture:**
  - box-UV footprints inside the texture
  - **no overlapping UV islands** (rasterize footprints into a bitmap and count overlaps), except intentional mirrors
  - texture is power-of-two where used, matches `resolution`, all textures the same size (issue #371), integer UV sizes (#386)
  - opaque pixels inside islands; none outside unless intentional
- **Vanilla JSON:**
  - `from`/`to` in −16..32
  - rotation single-axis, angle ∈ {−45, −22.5, 0, 22.5, 45} (if <1.21.6 clients are supported)
  - item element angles within ±45
  - every `#texture` resolves; face `uv` within 0..16
- **Visual review:**
  - contact sheet: front, side, back, top, 3/4 view
  - walk frames at t = 0, ¼, ½, ¾
  - side-by-side with the TFC reference model or a reference image
  - Claude must *look* at the sheet (Read the PNG) before calling a model done
  - [Opinion] Keep the sheets as CI artefacts for human review

---

## 8. Recommended pipeline for our project [Opinion]

**Principle:** Claude is the modeler. Code generators are its tools. Blockbench (headless web) is the compiler and renderer. Image models do the paint. Generative 3D is optional reference material.

### 8.1 What Claude does itself
- Writes and maintains `tools/datagen/models/` containing:
  1. a **rig library** (quadruped, biped-bird, fish, serpentine) with standard bone names
  2. **per-species parameter files** (sizes, offsets, extra parts like horns, humps, antlers)
  3. the **animation library** (§6.2) and the **TFC Java `AnimationDefinition` → bbmodel converter**
  4. the **validators** (§7.2)
- Imports the 44 TFC bbmodels unchanged except animation renames and tags (as the `bettermodel` skill already specifies). Converts TFC's 122 Java keyframe animations into them where missing.
- Authors block/item models (anvils, querns, barrels, tool racks) as vanilla JSON with a small Python box DSL. These are low-cube-count shapes where an LLM with visual feedback does very well.

### 8.2 What to add externally
- **Required:** nothing beyond Playwright Chromium (present) and network access to `web.blockbench.net`. Optionally vendor a pinned Blockbench web build (GPL-3.0, used as a tool, not shipped in the plugin).
- **Optional, for humans with the desktop app:** `sosadly/blockbench-mcp` (MIT, rich toolset with screenshots) or `jasonjgardner/blockbench-mcp-plugin` (GPL-3.0; headless mode useful if a GPU and Node ≥23.6 are available). Let a human in desktop Blockbench interactively touch up models Claude produced.
- **Optional reference generation:** Meshy Text-to-Voxel or Tripo stylize (`voxel`/`minecraft`) for silhouettes of unfamiliar animals; TRELLIS.2 (MIT) if a 24 GB GPU is available. Not Hunyuan3D (licence excludes the EU). Check commercial ToS before committing any derived asset.
- **Not recommended:** Blender MCP (wrong abstraction, telemetry defaults), Animated Java (BetterModel covers runtime), Unity/Godot MCPs.

### 8.3 Step-by-step workflow per new animal

1. **Research:** read TFC's `XxxModel.java` (part sizes from `CubeListBuilder`, `PartPose` offsets) and any similar TFC bbmodel. Collect 2–3 real-animal reference images (or ask the image workflow for an orthographic concept sheet: side/front/top at a 1:1 pixel grid).
2. **Parametric build:** fill a species parameter file. The generator emits the `.bbmodel` with box UV auto-packed, plus a **UV template PNG**.
3. **Render and review loop** (max ~5 iterations): headless Blockbench contact sheet, then validators, then Claude inspects images against the references, then adjusts parameters.
4. **Texture request:** generate it with the imagegen MCP (`image-generation` skill) from a spec with exact size, UV template, face labels, palette references and the "pixel art, hard edges, no AA" constraints.
5. **Texture integration:** downscale, palette-snap, mask to islands, embed in the bbmodel (`source` data URL) and re-render. Reject and re-request if seams or misplacement show.
6. **Animations:** convert TFC `AnimationDefinition`s if present. Otherwise instantiate library animations (`idle`, `walk`, `run`, `eat`, `sleep`, `attack`, + `swim` for aquatic) with species tuning. Render walk frames at ¼-cycle steps; check feet and loop seams.
7. **In-engine check:** run-paper self-test spawns each animal (see the `plugin-testing` skill). A human eyeballs it in the client; BetterModel issues (scale, pivots, Molang) surface only there.
8. **Commit** generator inputs and outputs together. Regenerate the whole set in CI and fail on validator errors.

### 8.4 Per block/item model
Python box DSL → vanilla JSON → validator (rotation rules per supported client range) → headless Blockbench `java_block` render (or deepslate) → texture request (16×16 per-face textures, prefer reusing TFC textures via the `tfc-asset-migration` skill) → CraftEngine wiring.

### 8.5 Quality-control loop summary
`generate → validate (machine) → render contact sheet → Claude visual critique vs reference → fix params → repeat (≤5) → texture → re-render → in-game smoke → human sign-off on a sample`.

---

## 9. Open questions / unverified

- jasonjgardner headless renderer on CPU-only / Node 22: untested; docs say a GPU and Node ≥23.6 are required.
- Blockbench `Format.convertTo` scripting call and Java-block export syntax for older targets: not tested.
- Exact sign conventions between vanilla `KeyframeAnimations` and bbmodel 5.0 keyframes: verify on one model before the batch conversion.
- Tripo stylize style values come from third-party client code, not official docs. Meshy Text-to-Voxel parameter names were not fully readable (the official page returned 404 to the fetcher at one point).
- Rodin Gen-2.5 release date: sources conflict.
- Animated Java and gltf2bbmodel licenses: not visible on fetched pages.
- OpenAI image model names are as shown on the docs page on 2026-10-16. Third-party pages also mention `gpt-image-2`; naming may differ by account or tier.
- Commercial 3D generator output-ownership terms: not reviewed.

---

## 10. Sources

**Blockbench and format**
- https://blockbench.net/wiki/docs/bbmodel
- https://raw.githubusercontent.com/JannisX11/blockbench/master/js/formats/bbmodel.js
- https://www.blockbench.net/wiki/docs/plugin · https://web.blockbench.net/docs
- https://www.blockbench.net/wiki/blockbench/formats
- https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins.json
- https://raw.githubusercontent.com/JannisX11/blockbench-plugins/master/plugins/gltf_to_minecraft/about.md · https://github.com/MopicMP/gltf-to-minecraft
- https://github.com/JannisX11/blockbench/issues/1764
- https://www.blockbench.net/wiki/guides/animation-expressions
- https://zff.dev/PROMETHIA/gltf2bbmodel
- https://awesome.ecosyste.ms/projects/github.com%2Fthetestgame%2Fbbmodel2bam
- https://cdn.jsdelivr.net/npm/bench-3d@1.0.0/README.md
- https://docs.figuramc.org/start_here/BlockBench · https://wiki.figuramc.org/index.php/Blockbench

**Blockbench MCP servers**
- https://github.com/jasonjgardner/blockbench-mcp-plugin · https://raw.githubusercontent.com/jasonjgardner/blockbench-mcp-plugin/main/headless/README.md · https://github.com/jasonjgardner/blockbench-mcp-project
- https://github.com/sosadly/blockbench-mcp · https://lobehub.com/mcp/sosadly-blockbench-mcp
- https://github.com/adhi-jp/minecraft-blockbench-mcp · https://mcpmarket.com/server/minecraft-blockbench
- https://github.com/sigee-min/bbmcp · https://mcpmarket.com/server/bbmcp
- https://github.com/enfp-dev-studio/blockbench-mcp
- https://www.skills.sh/hairyf/skills/minecraft-model · https://www.vibeindex.ai/skills/jasonjgardner/blockbench-mcp-project/blockbench-modeling

**BetterModel / Animated Java**
- https://github.com/toxicity188/BetterModel · https://mintlify.com/toxicity188/BetterModel/guides/creating-models · https://hangar.papermc.io/toxicity188/BetterModel/versions/3.0.0-SNAPSHOT-488
- https://github.com/animated-java/animated-java · https://animated-java.dev
- Local: `.claude/skills/bettermodel/SKILL.md`, `.claude/skills/bettermodel/references/research-report.md`

**Minecraft model format / Bedrock animation**
- https://minecraft.wiki/w/Model
- https://docs.fabricmc.net/develop/blocks/block-models
- https://bedrock.dev/docs/stable/Schemas.md · https://bedrock.dev/docs/1.17.0.0/1.17.0.2/Animations
- https://wiki.bedrock.dev/visuals/math-based-animations · https://wiki.bedrock.dev/concepts/molang.html

**General 3D MCPs**
- https://github.com/ahujasid/blender-mcp · https://raw.githubusercontent.com/ahujasid/blender-mcp/master/TERMS_AND_CONDITIONS.md
- https://glama.ai/mcp/servers/@CoplayDev/unity-mcp · https://claudemarketplaces.com/mcp/funplayai/funplay-godot-mcp · https://mcpservers.org/pt-BR/servers/vberai/godot-mcp
- https://github.com/yuniko-software/minecraft-mcp-server
- https://mcpservers.org/servers/pixellab-code/pixellab-mcp

**Generative 3D**
- https://docs.meshy.ai/api-text-to-voxel · https://docs.meshy.ai/en/api/changelog · https://docs.meshy.ai/api-introduction
- https://www.3daistudio.com/blog/best-3d-model-generation-apis-2026 · https://www.3daistudio.com/blog/is-trellis-2-free-pricing-how-to-get-started
- https://huggingface.co/spaces/multimodalart/wan-2-2-first-last-frame/blob/43f723e4cdaa81bd80f041a7d0741fe441cc984c/comfy_api_nodes/apis/tripo_api.py · https://www.tripo3d.ai/zh-Hant/features/ai-model-stylization · https://comfy.icu/node/TripoStylizeModel
- https://help.scenario.com/articles/3791851266-rodin-hyper3d-gen-2-5-models-the-essentials · https://80.lv/partners/hyper3d
- https://github.com/microsoft/TRELLIS.2 · https://huggingface.co/microsoft/TRELLIS.2-4B/blob/main/README.md
- https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1 · https://raw.githubusercontent.com/Tencent-Hunyuan/Hunyuan3D-2.1/main/LICENSE
- https://www.tencent.com/en-us/articles/2202235.html · https://www.notebookcheck.net/Tencent-s-new-Hunyuan-3D-3-0-brings-realistic-AI-model-generation-to-developers-and-creators.1116964.0.html
- https://huggingface.co/cocktailpeanut/sf3d · https://the-decoder.com/stability-ais-new-3d-model-aims-for-real-time-generation/

**Research papers**
- https://arxiv.org/abs/2411.09595v1 (LLaMA-Mesh) · https://arxiv.org/abs/2406.08751v1 (Minecraft buildings via LLMs) · https://arxiv.org/abs/2502.08884 (ShapeLib) · https://arxiv.org/abs/2503.15475 (Roblox Cube)

**Voxelization / meshing**
- https://trimesh.org/trimesh.voxel.creation.html
- https://arxiv.org/pdf/2607.06311 (greedy box decomposition description)
- https://docs.rs/block-mesh · https://github.com/cgerikj/binary-greedy-meshing
- https://awesome.ecosyste.ms/projects/github.com%2FLucasDower%2FObjToSchematic

**Textures / image generation**
- https://developers.openai.com/api/docs/guides/image-generation
- https://docs.apiyi.com/en/live/2026-08/gpt-image-2-transparent-background (third-party)

**Renderers**
- https://github.com/misode/deepslate · https://github.com/hexdoc-dev/minecraft-render-py · https://cdn.jsdelivr.net/npm/block-model-renderer@2.1.0/docs/node.md
