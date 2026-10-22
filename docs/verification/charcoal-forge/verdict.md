# PAR-METAL-10 charcoal forge in-game check: verdict

- Date: 2026-10-22
- Code: `4f65c3b` (the jar was built from that tree; this item adds only evidence and records)
- Server: Paper 1.21.11 build 132, Java 25; CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents 2.14.0; vanilla terrain (the forge does not need a TFC world), pack self-hosted on 127.0.0.1
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), GUI scale auto, bot `Bot`; about 7 fps (software GL, the
  machine at load 10 on 4 cores)
- Pack check: `textures/block/devices/charcoal_forge/{1,4,7}.png` and their `.mcmeta` in the generated pack are byte-equal
  to TFC's (`frametime: 2`, frames 0 to 12; the 13 frames hold 6 distinct images, order 0 1 2 3 4 5 5 5 5 4 3 2 0)
- Judged by: the main session, from the PNGs below

## Scene

A stone floor at y 119 around (96, -2060). Forge A at (93, 120, -2060) stands alone. Forge B at (99, 120, -2060) has stone
on all eight neighbours at y 120 (`fill 98 120 -2061 100 120 -2059 stone keep`, then the forge re-placed with
`tfp debug place`), so its 14 px top sits 2 px below the rim. Each heat level was placed on both with
`/tfp debug place tfp:charcoal_forge[heat_level=N]` typed in the client's chat (the command is player-only).

## Steps and results

| # | step | capture | result |
|---|---|---|---|
| 1 | `tfp time set noon` + `time set 6000`, heat 0, 4, 7 on both forges | `01-overview-noon-midnight-heat-0-4-7.png` (left column) | pass: level 0 shows the still cold top, 4 and 7 the glowing tops; sides are TFC's charcoal pile |
| 2 | `tfp time set midnight` + `time set 18000`, heat 0, 4, 7 | `01-...png` (right column), `07-light-pool-midnight-heat7.png` | pass: lit levels stay bright at night, level 0 goes dark like any block; the stone floor around a level 7 forge is lit warm (CE `luminance: 14` reaches the client's light engine), level 4 lights it less, level 0 not at all |
| 3 | forge B in the stone ring, top view at both times | `02-stone-ring-top-heat-0-4-7.png`, `03-stone-ring-rim-8x-noon-heat4.png`, `04-stone-ring-rim-midnight-heat7.png` | pass: the four inner rim faces (2 px of stone above the 14 px top) are drawn on every side; no x-ray strip, no missing faces (the `snow[layers=7]` carrier does not occlude its neighbours) |
| 4 | forge B from grazing angles, front and diagonal | `05-stone-ring-grazing.png` | pass: no gap or see-through line between the forge top and the rim |
| 5 | forge A close up, side faces at a shallow angle, level 7 at midnight | `06-display-over-block-model-no-zfight-midnight-heat7.png` | pass: no z-fighting between the 1.002 item display and the block model on the top or the sides; the display covers the block model completely |
| 6 | animation: 24, 40 and 80 captures of the level 7 top about 250 ms apart | `08-animation-heat7-24-captures.png` | pass: the captures fall into exactly 6 distinct images (TFC's 6), which repeat in TFC's ping-pong order (3 cycles in 80 captures). The cycle took about 5 s of wall time instead of TFC's 1.3 s because the starved client ticked slowly; the mcmeta is TFC's own, so on a normal client the cadence is TFC's |
| 7 | state change while watching forge A: 7, 4, 7, 0 (70 captures) | `09-state-change-7-4-7-0.png` | pass: each change swaps the top directly from one level to the next (captures 16/17, 39/40, 56/57); no capture shows the cold `heat_0` block model or an empty top between two lit levels, and the floor light follows the level |
| 8 | turn away and back; teleport 375 blocks away (chunks and the display entity unloaded), wait 8 s, return | `10-out-of-view-and-back.png` | pass: after turning back the lit top is there in the first capture; after the return the first three captures show unloaded terrain and the fourth the complete lit forge, with no capture of the cold top first |
| 9 | server log of the run (start to `stop`) | - | pass: no error from TerraFirmaPlugin or CraftEngine (only the EULA notice and PacketEvents' update check, HTTP 403 from GitHub) |

## Done criteria

Verdict: **pass**. No defect in the forge's appearance, so no follow-up item.

## Limits of this check

- Capture resolution is one frame per 150 to 290 ms at about 7 client fps: a flicker shorter than that would not show.
  Over 70 captures around three state changes none was seen.
- `tfp time set noon|midnight` moves only the TFC calendar; the world clock (sky light) stayed where it was, so the run
  also used vanilla `time set`. Linking the world day time to the calendar is PAR-CLIM-01 ("doDaylightCycle sync").
