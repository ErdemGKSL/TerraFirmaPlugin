---
description: Generate every image with Status "requested" in IMAGES.md (TerraFirmaPlugin art queue)
---
<!--
Codex loads custom prompts from ~/.codex/prompts/ (user level), invoked as /prompts:generate-required-images.
Install once:  mkdir -p ~/.codex/prompts && cp .codex/prompts/generate-required-images.md ~/.codex/prompts/
The repo-level skill .agents/skills/generate-required-images (invoke with $generate-required-images) and
the "/generate-required-images" command defined in AGENTS.md do the same thing without installation.
-->
Run the `/generate-required-images` procedure defined in AGENTS.md at the repository root: process every `Status: requested` entry in IMAGES.md in order, generate the pixel art, post-process it with tools/images/postprocess.py to the exact size and palette, save it to its target path (raw files under art/raw/<id>/), and update each entry to `generated` with notes. Don't modify anything except images, art/raw/** and IMAGES.md status/notes. End with a summary.
