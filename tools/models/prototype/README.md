# Headless Blockbench prototype (proof of concept)

These are throwaway scripts from the 3D-model research (`docs/research/3d-model-generation.md`). They show that Claude can generate, load and render models without the Blockbench desktop app.

- `gen.py <out.bbmodel>`: generates a test quadruped `.bbmodel` (format 5.0). It has box UV, a bone hierarchy, an embedded texture and a `walk` animation.
- `render.mjs <in.bbmodel> <out.png>`: loads the model in the Blockbench web app (web.blockbench.net) inside Playwright Chromium (SwiftShader WebGL), poses the first animation and saves a PNG render.
- `jb.mjs`: round-trips a vanilla Java block model through Blockbench's `java_block` codec.
- `probe.mjs`: prints the Blockbench version and the available codecs/formats.

`createRequire('/opt/node22/lib/node_modules/')` assumes the cloud environment's global Playwright install. Adjust the path elsewhere. They need network access to web.blockbench.net. The real pipeline (rig library, species params, validators, animation converter) goes in `tools/datagen/models/`, following §8 of the report.
