# tools/models: generated 3D models

How to use it: `.claude/skills/model-authoring/SKILL.md`.

| Path | What |
|---|---|
| `tfpmodels/bbmodel.py` | Deterministic `.bbmodel` writer (Blockbench 5 format, stable UUIDs) |
| `tfpmodels/rigs.py` | Parametric rigs with the standard bone-name contract (`quadruped` for now) |
| `tfpmodels/uv.py` | Box-UV shelf packer, UV template PNG |
| `tfpmodels/anims.py` | Procedural animation library (`idle`, `walk`, `eat`) |
| `tfpmodels/generate.py` | species JSON → `.bbmodel` + UV template, then validates |
| `tfpmodels/validate.py` | Machine checks (strict / `--lenient` for imported TFC models) |
| `tfc_anim/parse_java_anims.py` | Parses TFC/vanilla Java `AnimationDefinition` constants |
| `tfc_anim/convert.py` | Merges converted Java animations into a `.bbmodel` (verified axis rule) |
| `render/render.mjs` | Headless Blockbench (web) render to PNGs via Playwright Chromium |
| `species/*.json` | Per-species parameter files |
| `prototype/` | The original proof-of-concept scripts from the research |

`out/` is scratch output and is git-ignored.
