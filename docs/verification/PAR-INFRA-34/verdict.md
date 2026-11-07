# PAR-INFRA-34 verification: ore overlay datagen in game

Date 2026-11-07. HEAD 93248fc (jar built from it with `:dist:shadowJar`, no code change in this item). Paper 1.21.11
build 132 with the 1.21.11 client and Paper 26.3 build 196 with the 26.3 client, headless (`docs/testing/headless.md`),
CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents as cached by run-paper. Shipped look `host_rock`
(`tools/datagen/.../oreoverlay/mode.properties`). Every judgement below was made by looking at the screenshots in this
folder; the 8x crops were compared with TFC's textures from `/opt/tfc` (commit pinned in the ledger).

Evidence chain: the jar is from 93248fc; the item's commit sits on bc2cec7. The two commits in between (ae2f598
PAR-PROC-04 plan note, bc2cec7 PAR-FLUID-01 core fluid registry) touch no ore, rock, overlay, visual or datagen file, so
the evidence applies unchanged to this commit. An independent re-capture for review could not start (another run held
the single Paper server slot); it can be repeated when the slot is free, together with a 26.3 run of the level-3a
warning under the `visuals.profile."26"` key, which this run checked on 1.21.11 only.

## Steps run (both versions)

1. Fresh run directory (no `plugins/TerraFirmaPlugin`, no CraftEngine pack, flat world, `gamemode=creative`), first start
   (installer, `RESTART REQUIRED`, pack rebuilt), clean stop, second start; Bot joined with `guiScale:2`, pack applied.
2. Startup logs: `*-startup-log.txt`. On 1.21.11 a third start with `visuals.profile."1.21": level-3a` in `config.yml`.
3. `tfp time set noon`, clear weather. Three 5x3x5 boxes (x 0-4 granite, 7-11 gabbro, 14-18 shale; y -58..-56; z 5-9)
   built cell by cell with `tfp debug oreoverlay place` (console-friendly form of `debug place`), then the ores:
   `normal_native_gold/granite` and `rich_hematite/granite` on the north face, on the top and buried at z 7;
   `diamond/gabbro` and `normal_malachite/shale` on the north face, on the top and buried at z 7. Every place reply read
   the state back (`now: tfp:ore/...`).
4. Buried check: two-deep tunnels `setblock x -57 5|6 air` in each box's north face down to the buried ore at z 7 (camera
   in front of the tunnel, `show` lists `north (outcrop)` sent to Bot), then the tunnel's inner cell refilled with
   `tfp:rock/raw/granite` (`show` = `none (buried)`).
5. Shale top: camera above the shale box at pitch 55.
6. Item icons: `ce item give Bot` the four ores plus `poor_hematite/granite`, `normal_hematite/granite`, raw granite and
   raw shale; hotbar crop at GUI scale 2 scaled to 8 screen px per texel.
7. Pick-block: `clear Bot`, empty slot selected, middle click on the rich hematite on the granite north face and on the
   malachite on the shale top; `data get entity Bot SelectedItem` after each.

## Criteria

| # | Criterion | 1.21.11 | 26.3 |
|---|---|---|---|
| 1 | Startup: `visuals 1.21/level-2/nether-end-off` (26: `26/level-2/nether-end-off`), solid free = pool minus other packs, no warning | pass: `Visual allocation: 1.21/level-2/nether-end-off`, `1254 solid state(s) free ... pool 1289 states, 35 used by other packs, 0 needed`; no TerraFirmaPlugin WARN on the second start (`1.21.11-startup-log.txt`) | pass: `26/level-2/nether-end-off`, `1454 free ... pool 1489, 35 used by other packs, 0 needed`; no WARN (`26.3-startup-log.txt`) |
| 2 | `level-3a` migration warning | pass: `visuals.profile."1.21": level-3a was removed: ores are drawn as overlays and grades no longer cost visual states; using level-2`, then `1.21/level-2/nether-end-off` (log excerpt; a console line has no in-game frame, so the evidence is the log text) | not repeated (same code path, profile key "26") |
| 3 | Buried ores look exactly like their host rock | pass: the box shots show plain rock everywhere except the faces that touch air; the buried gold/hematite/diamond/malachite at z 7 are invisible (`1.21.11-boxes-buried-and-surface-ores.png`); opening a tunnel shows the ore at its back, refilling the cell in front gives a plain granite recess again (`1.21.11-buried-revealed-then-covered.png`: diamond, malachite, gold exposed, gold covered) | pass (`26.3-boxes-buried-and-surface-ores.png`, `26.3-buried-revealed-then-covered.png`) |
| 4 | Exposed faces carry the overlay | pass: north-face and top-face ores show gold, hematite, diamond, malachite specks on the host rock; the tunnel back faces too | pass |
| 5 | Shale ore keeps the columnar end texture | pass: the malachite on the shale top sits on the shale end (`shale_top`) pattern, continuous with the raw shale around it; side faces keep the horizontal side texture. Compared with TFC `shale_top`, `shale_top`+`normal_malachite` (TFC's `overlay_end` is the same overlay), `shale`+`normal_malachite` (`1.21.11-shale-top-vs-tfc.png`, bottom row also granite+gold, granite+rich hematite, gabbro+diamond) | pass (`26.3-shale-top-vs-tfc.png`) |
| 6 | Block item icons: rock + ore overlay, grades distinguishable | pass: slots 1-8 gold/granite, rich hematite/granite, diamond/gabbro, malachite/shale, poor and normal hematite/granite, raw granite, raw shale; each ore icon is the rock cube with the overlay on all three visible faces, the raw rocks without (`1.21.11-hotbar-gui2-8x.png`). Poor, normal, rich hematite read as rising amounts of red, as TFC's three textures do (`1.21.11-hematite-grades-vs-tfc.png`: in game poor/normal/rich above, TFC granite + overlay below) | pass (`26.3-hotbar-gui2-8x.png`) |
| 7 | Pick-block gives the ore's own block item, not the rock | pass: empty hotbar, middle click on the hematite: slot 1 holds "Rich Granite Hematite" (`item_model tfp:ore/rich_hematite/granite`); on the shale-top malachite: "Normal Shale Malachite" (`tfp:ore/normal_malachite/shale`) (`1.21.11-pick-block.png`: before, after, second pick). Names equal TFC's `en_us` | pass (`26.3-pick-block.png`) |

Verdict: **pass** on both versions. No divergence found beyond the rows already in the ledger (the composite item model,
ledger row "The ore block item renders TFC's block model"); no follow-up item.
