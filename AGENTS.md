# AGENTS.md: instructions for Codex / ChatGPT

You (Codex or ChatGPT) have **one job in this repository: generating 2D image assets** (Minecraft pixel-art textures, GUI backgrounds, HUD art, icons) requested in `IMAGES.md`. Claude Code does the coding and writes the requests. A human reviews and approves the results.

Don't change Java/Gradle code, skills, design docs or generated content in this repo. The only files you write are image files at the requested paths, raw generations under `art/raw/`, and status/notes fields in `IMAGES.md`.

## Project context (only what you need)
- TerraFirmaPlugin rebuilds the TerraFirmaCraft (TFC) mod as a Minecraft server plugin for **vanilla clients**. All art ships in a resource pack.
- Art must blend in with **TFC's existing textures**: Minecraft-style pixel art, flat colours, limited palettes, no anti-aliasing. Each request lists reference textures. Look at them before drawing.
- The TFC textures are in a TFC checkout (`https://github.com/TerraFirmaCraft/TerraFirmaCraft`, `src/main/resources/assets/tfc/textures/**`). If a reference path isn't available locally, clone that repo outside this repository, read-only.
- Never use the TerraFirmaCraft logo or name in images, and never copy art from other mods or packs.

## Command: `/generate-required-images`
When the user types `/generate-required-images` (or `$generate-required-images`, or asks you to "generate the required images"), do the following:

1. **Read `IMAGES.md`.** Collect every entry with `Status: requested`, in file order. If there are none, say so and stop.
2. For each entry:
   1. Set its status to `in-progress` in `IMAGES.md`.
   2. Open every file listed under **References** and note the palette, outline style, shading direction (TFC uses top-left light) and level of detail.
   3. **Generate the image** with your image generation capability, following the entry's Description/Size/Constraints and the **Global style guide** in `IMAGES.md`. Ask for pixel art at the exact target size, or an integer multiple of it, on a transparent background.
   4. Save the raw result(s) to `art/raw/<id>/<n>.png`.
   5. **Post-process** to the exact spec:
      ```bash
      python3 tools/images/postprocess.py art/raw/<id>/<n>.png <Target path> --size <WxH> [--trim] \
          [--palette-from <reference pngs...>] [--colors N] [--upscale K] [--translucent]
      ```
      Use `--palette-from` with the reference textures so colours match TFC. Use `--upscale` only when the request says to draw small and upscale (e.g. pack icon 32→128).
   6. **Check the output**: exact size, RGBA, alpha only 0/255 (unless translucency is allowed), no stray semi-transparent pixels, no text, readable at 100% zoom. Fix it, or regenerate if needed.
   7. Update the entry: `Status: generated` and append to `Notes` the prompt you used, the raw file kept, the post-process command, and anything the reviewer should look at. Never mark an entry `approved`; only a human does that.
3. If an entry is ambiguous or impossible (contradictory size, missing reference), leave it `requested`, add a `Notes:` question, and continue with the next one.
4. Finish with a short summary: generated ids, skipped ids and why.

If you are ChatGPT without repository write access, do steps 2.2–2.3 per entry. Then give the user each image together with its target path, the post-process command to run, and the exact `IMAGES.md` status/notes edits to apply.

## Quality bar
- It must look like it belongs next to TFC textures in-game. When unsure, simpler is better.
- Sprite sheets and animation strips: keep frame cells exactly aligned to the requested grid.
- GUI backgrounds: keep slot squares at exact pixel positions when the request gives coordinates (they must align with inventory slots).
- Use one consistent light direction and outline convention across a family of images.
