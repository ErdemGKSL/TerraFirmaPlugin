---
name: image-generation
description: How Claude makes 2D art for this project with the imagegen MCP server (codex-imagegen-mcp, the user's ChatGPT/Codex plan, no API key). First checks whether TFC or the project already has a usable texture, then generates the image itself with mcp__imagegen__generate_image / edit_image, keeps the raw output and prompt under art/raw/<id>/, post-processes it to exact size, alpha and TFC palette with tools/images/postprocess.py, verifies it by looking at it, and wires it into CraftEngine/BetterHud/item models. Holds the project's pixel-art style guide, quality bar, placeholder rule, ChatGPT sign-in handling in cloud containers, and the backlog of images still needed. Use this whenever a feature needs a texture, GUI background, HUD sprite, icon, button, pack image, entity texture or any other 2D asset that doesn't exist yet, when reviewing or redoing generated art, or when the user mentions image generation, Codex images or imagegen, even if they just say "we need art for this".
---

# Generating images (imagegen MCP)

Claude generates the project's 2D art itself through the **imagegen MCP server** (`.mcp.json`, package `codex-imagegen-mcp`). It uses the user's ChatGPT plan, the same image service as Codex's built-in image tool. The generic tool rules (parameters, transparency, save paths, errors) are in the `imagegen-mcp` skill; load it too. This skill adds the project rules on top. A human reviews the result in game.

Never draw art by hand-writing pixel arrays or rendering SVG to PNG. Scripted placeholders (section 6) are the one exception.

## 1. Check before generating
1. **TFC already has it?** Search the pinned TFC checkout: `src/main/resources/assets/tfc/textures/{block,item,gui,entity,...}`. TFC covers almost every block/item/GUI. Prefer copying through datagen (`tfc-asset-migration`).
2. **Derivable from an existing texture?** Recolours, overlays, crops and composites are deterministic transforms. Do them in `tools/datagen` (Pillow) instead of generating.
3. **Already made or listed?** Check `art/raw/<id>/` and `references/backlog.md` for the same target path or purpose.

Generate only for plugin-specific needs that TFC never had: the outside-inventory button, HUD frames that BetterHud layouts need, the pack icon, GUI screens that exist only in our plugin, entity textures for new models, and ledger divergences that need a new visual.

## 2. Write the spec first
Before calling the tool, fix the spec (put it in `art/raw/<id>/notes.md`, see section 5). A complete spec lets the image succeed on the first try:
- **id:** kebab-case, unique, prefixed by area (`ui-`, `hud-`, `tex-`, `item-`, `pack-`, `entity-`).
- **Target path:** under `content/art/...`, matching where datagen/our installers pick it up.
- **Size:** exact pixels, plus grid/frame layout for sheets (e.g. `48x24, two 24x24 cells: normal | hovered`). For GUI backgrounds, give slot coordinates if slots must align (vanilla slot = 18×18 including border, item area 16×16).
- **Used by:** which system consumes it (CE `images:` glyph for a container title, BetterHud listener image with `split: 25`, item model texture, BetterModel texture, ...).
- **Description:** what it depicts, states/frames, light direction (TFC: top-left).
- **References:** concrete TFC texture paths to match palette/style. Always at least one. Open them with the Read tool before prompting.
- **Constraints:** transparency, translucency allowed or not, tiling, animation frame count, "no text", palette size.

## 3. Style guide (applies to every image)
- **Minecraft pixel art** that matches TFC's look: flat shading, 1-pixel outlines only where TFC uses them, limited palette, **no anti-aliasing, no gradients/blur, no text** unless the spec says so.
- **Exact pixel size** as specified. Texture art is on a 16×16 grid (or a multiple, as given). The service picks its own resolution, so generate large and downscale with nearest-neighbour/box through `tools/images/postprocess.py`.
- **Transparency:** PNG RGBA. Background fully transparent unless stated. Alpha only 0 or 255 unless the spec allows translucency.
- **Palette:** sample colours from the reference files (`--palette-from`).
- **Never** use the TerraFirmaCraft logo or name in images, and never copy art from other mods or packs.
- Keep one light direction (top-left) and one outline convention across a family of images.

## 4. Generate and post-process
1. **Sign-in check:** call `mcp__imagegen__auth_status`. Cloud containers are ephemeral, so the ChatGPT sign-in (`~/.local/share/codex-imagegen-mcp/`) is usually gone in a new session. If not signed in, call `mcp__imagegen__sign_in` with `method: "device"` (no browser in the container) and give the user the link `https://auth.openai.com/codex/device` and the code verbatim, then wait and re-check. CLI equivalent: `npx -y codex-imagegen-mcp@0.2 login --device --no-browser` (run in the background; it waits for approval). Never commit credentials.
2. **Prompt** (structure from `imagegen-mcp` → `references/prompting.md`): asset type + "Minecraft-style pixel art, NxN grid, hard pixel edges, no anti-aliasing, no text, flat shading, light from top-left", the description, the palette in words, and the background. Use `aspect_ratio` matching the target (1:1 for square textures). For style matching, use `edit_image` with up to 5 TFC reference textures in `images`, upscaled with nearest-neighbour first so the service can see them.
3. **Background:** `background: "transparent"`. If the result is opaque or fringed, regenerate on a flat `#00FF00` backdrop and run `mcp__imagegen__remove_background` (local, no quota).
4. **Save raw** with `output_path: "art/raw/<id>/"`. Never overwrite earlier raws; the tool versions names itself.
5. **Post-process** to the exact spec:
   ```bash
   python3 tools/images/postprocess.py art/raw/<id>/<raw>.png <Target path> --size <WxH> [--trim] \
       [--palette-from <reference pngs...>] [--colors N] [--upscale K] [--translucent]
   ```
   Use `--palette-from` with the reference textures so colours match TFC. Use `--upscale` only when the spec says to draw small and upscale (e.g. pack icon 32→128).
6. **Check the output** by looking at it with the Read tool (also an 8× nearest-neighbour enlarged copy in the scratchpad for small textures): exact size, RGBA, alpha only 0/255 (unless allowed), no stray semi-transparent pixels, no text, readable at 100% zoom, looks like it belongs next to the TFC references. Fix it or regenerate.
7. Every image counts against the user's ChatGPT usage limits: one call per asset, no speculative extras, ask before batches of more than ~6. On a usage-limit error, stop and say when it resets.

If the MCP tools are not loaded in the current session (`.mcp.json` is read at session start), use the CLI with the same engine: `npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 1:1 -b transparent -o art/raw/<id>/`.

## 5. Record it
`art/raw/<id>/notes.md` holds: the spec (section 2), the prompt(s) used, which raw file was picked, the exact post-process command, and anything the reviewer should look at. Raws and notes are committed with the final image so the result can be reproduced or redone. Add a `TODO(image:<id>)` comment where the code/config will consume an image that isn't done yet. When an image is finished, remove its entry from `references/backlog.md`.

## 6. Placeholders
To keep features testable before the art exists, datagen may emit a **placeholder**: a flat-colour PNG at the exact size with a 1-px magenta border, generated by script, never under the final target path's name. Use `content/art/_placeholder/<id>.png` and point the config there until the real image is in. The visual checklist flags any placeholder still in use.

## 7. Wire it in and review
- Replace the placeholder reference, then run the relevant showcase/visual checklist (`plugin-testing`).
- Show the user the final image (and the raw) and say what to look at. Approval is the human's call; if they reject it, update the spec in `notes.md` and regenerate.

## Sheets, GUIs, animations
- Sprite sheets and animation strips: keep frame cells exactly aligned to the requested grid. Generate each cell or frame separately if the service won't keep the grid, then assemble with Pillow.
- GUI backgrounds: keep slot squares at exact pixel positions (they must align with inventory slots). Generate the decorative frame, then composite slots at the computed coordinates in a script.
- Entity textures (`model-authoring`): pass the UV template PNG as an `edit_image` reference together with the face list and TFC palette references, then post-process to the template size.

Images still needed: `references/backlog.md`.
