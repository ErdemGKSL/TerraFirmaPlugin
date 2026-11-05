# PAR-INFRA-17 verification

- Date: 2026-11-05. Tree: `78f5399` plus this item (spike S2).
- Servers: Paper 1.21.11 and Paper 26.3, flat world, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents 2.14.0, `TfpSpikeS2` (spike plugin, not shipped).
- Clients: 1.21.11 and 26.3 under Xvfb + Mesa llvmpipe, GUI scale 2, subtitles on, OpenAL null device.
- Steps: `docs/spikes/S2-destroy-stages.md` §2 (scene, mining with xdotool, `[S2]` logs). `tfp debug place` and
  `tfp debug breaktime` do not exist; the spike's `s2 place` and its break-tick log stand in for them.

| Criterion | 1.21.11 | 26.3 | Evidence |
|---|---|---|---|
| S2 doc has a verdict per feel item on both versions | pass | pass | `docs/spikes/S2-destroy-stages.md` §3 |
| Cracks track progress on the renderer cube (destroy_stages) | pass | pass | `1.21.11-cracks-renderer-cube.png`, `26.3-cracks-particles-subtitles.png` |
| Break particles of the material, break sound of the material | pass (copper item particles; `block.metal.break`) | pass | `1.21.11-break-particles-subtitles.png`, `26.3-cracks-particles-subtitles.png`, `[S2] sound` log lines |
| Hit, step, place sounds are TFC's (`SoundType.METAL`) | pass (`block.metal.hit/step/place` packets) | pass | `[S2] sound` log lines |
| Mining time hand / correct / wrong tool vs TFC | partial: TFC formula (21 / 500 ticks); measured 23-27 / 582 on this client, vanilla control 18-19 | partial: 35-38 with the tool (vanilla control 17-20) | §3 item 4; divergence row (CE counts swing ticks) |
| Light at noon, midnight, torch | pass (no black, no full-bright faces) | pass | `*-light-noon-midnight-torch.png` |
| No z-fighting 1-48 m, no seam between adjacent cubes | pass | pass | `*-distance-16-96m.png`, `1.21.11-seam-and-outline-x8.png` |
| Outline and hitbox of a full block | pass (outline crop; stand height floor + 1.0) | pass (stand height -59.0) | `1.21.11-seam-and-outline-x8.png` |
| No leftover display after /fill and TNT | pass | pass | `*-leftovers-fill-tnt.png` |
| Creative pick-block gives the block's item | pass | pass (`craftengine:id` checked) | `1.21.11-pick-block.png` |
| No square ring at distance | pass after the fix (cube `view_range` 4.0); fail before (`view_range` 0.75 left a hole at 48 m) | pass | `1.21.11-distance-before-fix-view-range-0.75.png`, `*-distance-16-96m.png` |
| Overlay ore: no crack hidden (cracked variant), rock particles and stone sounds, cutoff pop at 48 m acceptable on native gold | pass (cracked variant; granite particles; `block.stone.break`; pop between 48 and 64 m judged acceptable) | pass | `*-overlay-ore-cracked-variant.png`, `*-distance-16-96m.png` |
| Templates generated and used by the spike block without CE load issues | pass (no issue for `templates/rendered_block*.yml` or `tfps2`; the other reported issues are the existing ore/soil budget ones of a flat test world) | pass | server logs |

Mining-time note: CraftEngine's progress formula is TFC's, but it only advances on ticks with a client arm swing; the
headless clients (llvmpipe, about 15 fps) miss swings, real clients at 20 ticks/s should land within a few ticks. The
gap is recorded as a divergence and is the one criterion not shown as an exact match.
