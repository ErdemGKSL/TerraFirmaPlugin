# PAR-INFRA-17 verification

- Date: 2026-11-05. Tree: `3c8eccf` (the item) plus its review fix (sound volume/pitch, this evidence pass).
- Servers: Paper 1.21.11 and Paper 26.3, flat world, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents 2.14.0, `TfpSpikeS2` (spike plugin, not shipped).
- Clients: 1.21.11 and 26.3 under Xvfb + Mesa llvmpipe, GUI scale 2, subtitles on, OpenAL null device; review-fix shots
  with F1 (no HUD, no crosshair, no block outline).
- Steps: `docs/spikes/S2-destroy-stages.md` §2 (scene, mining with xdotool, `[S2]` logs). `tfp debug place` and
  `tfp debug breaktime` do not exist; the spike's `s2 place` and its break-tick log stand in for them.
- Content: the plan ships no family (PAR-DECO-09 owns `tfp:metal/block/copper`); the spike block
  `tfps2:metal/block/copper` is generator output (`S2Spike`) that calls the generated `tfp:block/rendered_cube`.

## Review-fix scene (both versions)

Floor of stone at y -61; copper renderer cubes at (2|3, -60, 3); vanilla granite rock at (5, -60, 3); spike overlay ore
(native gold on granite) at (6, -60, 4), so the rock's east face and the ore's north face form an inner corner at x 6,
z 4. Bot with `tfp:metal/pickaxe/bronze` in slot 1 and an empty slot 2.

- Light: Bot at (4.0, -60, -1.5) yaw 0 pitch 35; a vanilla copper ingot item entity at (5.5, -60, 1.5) as an entity
  control; `time set midnight`, shot 5 s later; `setblock 3 -60 1 torch` (2 blocks in front of the cubes, in frame),
  shot 5 s later.
- TNT: `summon tnt 3.5 -60 2.5 {fuse:60}` (the block in front of copper cube (3, -60, 3), touching it), shots 1 s after
  the summon and 6 s after it; then `execute if block <pos> minecraft:air` for every row block.
- Seam: Bot at (3.0, -60, 1.2) yaw 0 pitch -5 (crosshair on the sky, no outline). Corner: Bot at (8.5, -60, 1.0) yaw 40
  pitch -5. 8x crops are 40x40 px of the 1280x720 shot.
- Distance: Bot at (4.5, -60, 4 - d + 0.5) yaw 0 pitch 4 for d = 16, 32, 48, 64; 1 m: Bot at (3.0, -60, 2.0) pitch 50.

## Verdicts

| Criterion | 1.21.11 | 26.3 | Evidence |
|---|---|---|---|
| S2 doc has a verdict per feel item on both versions | pass | pass | `docs/spikes/S2-destroy-stages.md` §3 |
| Cracks track progress on the renderer cube (destroy_stages), correct tool | pass: stages 1..9 advance over 12 frames 100 ms apart, the break and particle burst follow | pass (same) | `*-cracks-correct-tool-frames.png` (15 of 30 frames, 100 ms apart from mouse down) |
| Break particles of the material | pass: copper-coloured item particles | pass | `*-cracks-correct-tool-frames.png`, 8x crop in `*-seam-corner-particles-x8.png` |
| Break sound of the material, hit/step/place sounds are TFC's | ids pass; volume/pitch partial: place 1.0/1.2, step 0.15/1.5, fall 0.5/1.125 and the breaker's `block.metal.break` 1.0/1.2 are TFC's; hit 0.5/0.5 (TFC 0.25/0.75) and a second break packet `replaced.block.metal.break` 1.0/0.8 come from CraftEngine's hard-coded paths | same | `[S2] sound <id> volume= pitch=` log lines; ledger divergence, follow-up PAR-INFRA-33 |
| Mining time hand / correct / wrong tool vs TFC | partial: bronze pickaxe 22 ticks this run (formula 21; first run 23-27), hand 582 in the first run (formula 500); wrong tool not measured | partial: 22 ticks this run (first run 35-38) | §3 item 4; divergence row (CE counts swing ticks), PAR-INFRA-32 |
| Light at noon, midnight, torch | pass: mean luminance cube top / floor next to it: noon 124 / 124, midnight 37 / 38, midnight + torch 103 / 116; the cube is never brighter than the floor | pass: noon 126 / 125, midnight 37 / 38, torch 102 / 114 | `*-light-noon-midnight-torch.png` (torch and ingot entity in frame) |
| Per-face shading | partial: entity lighting, top : north face = 1 : 0.77 (vanilla 1 : 0.8), no AO | same | ledger divergence row (AO and face shading) |
| No z-fighting 1-48 m | pass: none at 1 m; 8x crops at 32, 48, 64 m show clean faces | pass | `*-zfight-1m-distance-x8.png` |
| No seam between adjacent cubes | pass: 8x crop shows the two textures' own bevels meeting, no floor or sky pixel between | pass | `*-seam-corner-particles-x8.png` |
| Overlay ore: no gap at the ore/rock inner corner | pass: 8x crop, the overlay face meets the rock face with no gap | pass | `*-seam-corner-particles-x8.png` |
| Outline and hitbox of a full block | pass (outline crop; stand height floor + 1.0) | pass (stand height -59.0) | `1.21.11-seam-and-outline-x8.png` (outline only, not seam evidence) |
| No leftover display after /fill and TNT | pass: TNT next to the row destroys both copper cubes and the rock (`execute if block ... air` passes), nothing floats; /fill as before | pass: also the ore 3 blocks away went | `*-tnt-adjacent.png`, `*-leftovers-fill-tnt.png` |
| Blast resistance | pass: resistance 6.0 (TFC's), the cubes break next to TNT like an iron block | pass | `*-tnt-adjacent.png` |
| Creative pick-block gives the block's item | pass | pass (`craftengine:id` checked) | `1.21.11-pick-block.png` |
| No square ring at distance | pass after the fix (cube `view_range` 4.0); fail before (`view_range` 0.75 left a hole at 48 m) | pass | `1.21.11-distance-before-fix-view-range-0.75.png`, `*-distance-16-96m.png`, `*-zfight-1m-distance-x8.png` |
| Overlay ore: no crack hidden (cracked variant), 10-frame capture | pass: 7 frames 0.75 s apart show cracks growing over the specks, then the rock particle burst | pass | `*-overlay-ore-crack-frames.png` |
| Overlay ore: rock particles and stone sounds | pass: grey rock particles; `block.stone.break` 1.0/0.8, `block.stone.hit` | pass | `*-overlay-ore-crack-frames.png`, `[S2] sound` lines |
| Overlay ore: cutoff pop at 48 m acceptable on native gold | pass (judged): specks visible at 32 m (block 15 px tall), gone at 48 m (block 7 px tall, specks 1 px); the rock stays, so the pop changes 1 px specks only | pass (same) | `*-zfight-1m-distance-x8.png` |
| Templates generated and used by the spike block without CE load issues | pass | pass | server logs (below) |

Server log lines (26.3; 1.21.11 the same): `[CraftEngine] Loaded pack: tfp. Default namespace: tfp`,
`[CraftEngine] Loaded pack: tfps2. Default namespace: tfps2`; every `Found N issue(s) in file` line names
`configuration/ore/*.yml`, `configuration/soil/*.yml` or `configuration/sand.yml` (the existing ore/soil budget issues of a
flat test world), none `templates/rendered_block.yml`, `rendered_block_items.yml` or `tfps2`.

## Notes on the rejected run

- Light on 26.3: this pass measured the cubes at the floor's brightness at midnight on both versions (37 against 38).
  The vanilla copper-ingot item entity next to them is equally dark, so the client lights entities by the block they are
  in; the full-bright report was not reproduced. With the torch 2 blocks away the cubes and the item are lit warm, which
  is the torch's own light.
- TNT: the earlier `tnt-before` shots had the TNT too far from the row; summoned touching the row it destroys it.
- Mining time of the overlay ore (155 ticks by hand) is vanilla granite's (hardness 1.5); TFC's ore is
  `Ore.java` `rock.category().hardness(6.5)`, resistance 10. Not a parity figure: the real ores come with PAR-INFRA-15/16.

Mining-time note: CraftEngine's progress formula is TFC's, but it only advances on ticks with a client arm swing; the
headless clients (llvmpipe) sometimes miss swings. The gap is recorded as a divergence (PAR-INFRA-32).
