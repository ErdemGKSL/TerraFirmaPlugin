---
name: generate-required-images
description: Generate the 2D pixel-art images (Minecraft/TerraFirmaCraft-style textures, GUI backgrounds, HUD art, icons) requested in IMAGES.md, post-process them to exact size/palette with tools/images/postprocess.py, save them to their target paths and update IMAGES.md statuses. Use when the user runs /generate-required-images, $generate-required-images, or asks to generate the requested/required images or art for this repo.
---

# Generate required images

Follow the procedure in the repository root `AGENTS.md` → "Command: `/generate-required-images`". Summary:

1. Read `IMAGES.md` and take every `Status: requested` entry, in order.
2. Per entry: set it to `in-progress`; study the reference textures; generate pixel art that follows the entry and the global style guide; keep the raw files in `art/raw/<id>/`; run `python3 tools/images/postprocess.py <raw> <target> --size WxH [--palette-from refs...] [--colors N] [--upscale K] [--trim] [--translucent]`; verify size, alpha and the absence of text/anti-aliasing; set it to `generated` and record the prompt and command in `Notes`.
3. Never set `approved`. Leave unclear entries as `requested`, with a question in `Notes`.
4. Only write images, `art/raw/**` and the `IMAGES.md` status/notes. Don't touch anything else.
