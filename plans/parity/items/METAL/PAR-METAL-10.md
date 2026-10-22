---
id: PAR-METAL-10
title: "Charcoal forge visual verification: night, neighbouring full blocks, animation frames, lit-display flicker"
area: METAL
status: open
wave: 3
deps: []
size: S
aliases: ["owner: forge verification", "ledger divergence: charcoal forge"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/charcoal_forge"
  - "src/main/resources/assets/tfc/textures/block/devices/charcoal_forge"
files_owned:
  - "docs/verification/charcoal-forge/**"
ui: true
in_game_steps:
  - "tfp time set noon; tfp debug place tfp:charcoal_forge[heat_level=4]"
  - "fill stone around (fill ~-1 ~ ~-1 ~1 ~ ~1 stone keep, then re-place the forge)"
  - "tfp time set midnight"
  - "record 3 s video or 10 frames 100 ms apart for the animation"
  - "tfp debug place tfp:charcoal_forge[heat_level=7] then [heat_level=0] while watching (flicker)"
plan:
  - "Capture (cheap model) and judge (Opus): heat_level 0, 4 and 7 at noon and at midnight; the forge surrounded by stone (full blocks) on 4 sides: no x-ray strip, no z-fighting of the 1.002 display over the block model; the animated glow strips cycle all frames (TFC mcmeta frametime); no flicker of the item display when the state changes (CE morph) or when walking in/out of view; luminance lights surroundings client-side."
  - "File any defect as a new item (datagen ForgeDevices fix) with the screenshots."
tests:
  - "Screenshots + verdict file in docs/verification/charcoal-forge"
done_criteria:
  - "Verdict pass or follow-up items created"
---

# PAR-METAL-10: Charcoal forge visual verification: night, neighbouring full blocks, animation frames, lit-display flicker

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
