# PAR-INFRA-16 verification: ore overlay runtime

Date 2026-11-06. Base commit 06d6535 plus this item's change (jar built from the worktree). Paper 1.21.11 build 132 with
the 1.21.11 client, Paper 26.3 with the 26.3 client (headless, `docs/testing/headless.md`), CraftEngine 26.9.2,
PacketEvents 2.14.0, shipped look `host_rock`. Ores were placed with `tfp debug oreoverlay place <x> <y> <z> <state>`
(console-friendly; `debug place` needs an aiming player) into vanilla stone boxes high above spawn; every judgement below
was made by looking at the screenshots in this folder.

| # | Criterion | 1.21.11 | 26.3 |
|---|---|---|---|
| 1 | Buried ores look like rock; no overlay | pass (`1.21.11-buried.png`; `debug oreoverlay show` = "none (buried)") | pass (first frame of `26.3-expose-frames-50ms.png`) |
| 2 | Exposure in the same frame as the hole | pass: frame 1 stone, frame 2 already hole + specks, never a plain-rock hole (`1.21.11-expose-frames-50ms.png`, 50 ms) | pass (`26.3-expose-frames-50ms.png`) |
| 3 | Covering in the same frame | pass: the overlay vanishes in the frame the stone appears (`1.21.11-cover-frames-50ms.png`) | pass (`26.3-cover-frames-50ms.png`) |
| 4 | TFC orientation of the specks | pass: south face sampled to 16x16 equals TFC's `normal_native_gold.png` texel for texel (`1.21.11-orientation-south-vs-tfc.png`: in game, TFC rock+overlay, TFC overlay); east/top faces on the floating ore (`1.21.11-floating-ore-vs-granite.png`) and gold/malachite/diamond/hematite (`1.21.11-five-ores-1m.png`) | pass (malachite, gold) |
| 5 | Cracks never hidden under the specks | pickaxe: pass, the cracked overlay variant draws the crack pattern over specks and rock alike, stage by stage, until the break (`1.21.11-mining-cracks-frames.png`). Hand: **failed in the first run** (review: the vanilla cracks showed only between the veins in the 10 s / 20 s hand-hold frames). Two causes, both fixed in the repair: Paper reports stage 0 as progress 0.0 and the overlay treated it as uncracked; and CraftEngine runs its own dig progress for custom blocks and sends `BLOCK_DESTRUCTION` itself, so no `BlockBreakProgressUpdateEvent` fires (seen in `BukkitServerPlayer.broadcastDestroyProgressVanilla` of the CE 26.9.1 jar). The overlays now also follow that packet. Rerun 2026-11-06, Paper 1.21.11 + client 1.21.11, empty hand held on normal_native_gold/granite for 18 s: the crack pattern crosses the gold specks from the first frame (1.5 s) to the last (`1.21.11-hand-hold-cracks-frames.png`, 0/1.5/4.5/7.5/12/16.5 s; `1.21.11-hand-hold-1.5s-18s-zoom.png`): pass | pickaxe: pass (`26.3-mining-cracks-break-frames.png`); hand rerun on 26.3 in PAR-INFRA-37 |
| 6 | Rock-coloured break particles, ore item drop | pass: pink granite particles, the native gold ore item drops (`1.21.11-mining-break-frames.png`) | pass |
| 7 | Mining time / tool tier equal to raw granite | not measured: unchanged by this item (the visual is the rock; CE's server-side mining, PAR-INFRA-32); the bronze pickaxe broke the ore in about the same time as the stone after it (by eye). Hand / correct / wrong tool timing is recaptured in PAR-INFRA-37 | PAR-INFRA-37 |
| 8 | Light noon / midnight / torch | pass: specks follow the rock, never black or full-bright (`1.21.11-light-noon-midnight-torch.png`) | pass (`26.3-light-noon-midnight-torch.png`; the 26.3 midnight/torch frames were not usable on review: PAR-INFRA-37) |
| 9 | Underwater | pass with the expected tint: rock and specks both take the water colour, gold reads olive through water (`1.21.11-underwater-vs-air.png`, same camera with air) | not repeated |
| 10 | No z-fighting, no seam 1-48 m | pass (`1.21.11-five-ores-1m.png`, `1.21.11-outcrop-16-32-48-64-96m.png`) | not repeated |
| 11 | Outline and hitbox | pass: full-cube outline (crosshair frames); the block is the rock visual, unchanged | same |
| 12 | Leftovers after TNT, `/fill` air and refill | pass: surviving ores keep their faces, nothing floats after the blast, the fill or the refill (`1.21.11-leftovers-tnt-fill-refill.png`); self-test `ore.overlay` counts the viewer's ids = the table's faces after an explosion | itest on Folia |
| 13 | Distance: cutoff and outcrops | pass: faces that are surface outcrops stay visible to 64 m (full range); after a roof made them cave faces (re-placed ores) they vanish between 40 and 64 m while the stale-outcrop ones stay (`1.21.11-cave-cutoff-40m-64m.png`): the spherical client cutoff works; the outcrop flag only refreshes on a neighbour change (ledger divergence) (the 16-96 m distance set, the down-face shot and the 200-block cave walk were not usable in this run: PAR-INFRA-37; the down-face orientation is unit-tested in PAR-INFRA-15 OverlayModelTest) | PAR-INFRA-37 |
| 14 | Budget: build time | `tfp debug oreoverlay stats` near a fresh spawn: 265 tables, avg 0.40-0.54 ms CPU per chunk build, region-thread copy 0.17-0.23 ms (26.3 run); the S1 gate numbers (entities per player) are PAR-WORLD-17/19's open owner question, not changed here (the review found no fresh-world capture behind these numbers: recaptured in PAR-INFRA-37) | PAR-INFRA-37 |
| 15 | Self-test `ore.overlay` | PASSED on Paper 1.21.11 and Folia 1.21.11 (`:itest:runSelfTest`); no thread-ownership finding in the Folia log. Other failures of those runs (`worldgen.terrain_generates` on Paper's first start, `inst.suppression` on both) are not touched by this item | — |
| 16 | Falling ore keeps its overlay | deferred check tracked in PAR-INFRA-36 (needs PAR-PHYS-01) | |
| 17 | Pick-block, block item icons | PAR-INFRA-34 (host-rock datagen check) | |

Found and fixed during the run: Paper 26.3 has no `UnsafeValues#nextEntityId` (NoSuchMethodError in the handler, and the
patch was skipped behind it). Ids now come from the server counter when it exists, else from our own range from
0x70000000; the block-change handler queues the authoritative patch before the fast path.

Repair run 2026-11-06 (review REJECT, PAR-INFRA-16 repair): hand-dig cracks fixed and rechecked in game (row 5). The
console `tfp debug oreoverlay place` now runs inline when the console owns the region: replies in about 30 ms, no 2 s main
thread stall and no Watchdog report in the run (the general `TfpCommands.reply` wait is PAR-INFRA-38). The Folia
`inst.suppression` failure belongs to M1-INST-04.

Verdict: **pass** for this item; the falling-ore step is PAR-INFRA-36; the missing shots (rows 7, 8 on 26.3, 13, 14) are
PAR-INFRA-37.
