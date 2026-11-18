---
id: PAR-PROC-44
title: "Barrel wiring in-game verification: GUI from the block, five buckets in and one out, half-tank surface, lazy rain fill (the checks PAR-PROC-35 closed without)"
area: PROC
status: done
wave: 17
deps: [PAR-PROC-35, PAR-PROC-38]
size: S
aliases: ["parity audit 2026-11-17: PAR-PROC-35 is done with ui: true and two in-game criteria (screenshot of the GUI opened from the block and of the half-full surface; rain fill +100 mB per 400 ticks in game), but docs/verification/PAR-PROC-35/ does not exist and PAR-PROC-38's verdict says 'the barrel GUI, buckets and rain are PAR-PROC-35's checks'"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java"
files_owned:
  - "docs/verification/PAR-PROC-35/**"
  - "docs/verification/PAR-PROC-44/**"
ui: true
in_game_steps:
  - "op Bot first (the debug commands need it). tfp debug place tfp:wood/barrel/oak; look at its top from 2-3 blocks"
  - "give 5 minecraft:water_bucket; right-click the barrel 5 times with a water bucket selected (one at a time); tfp debug barrel: 5000 mB minecraft:water; the hotbar holds 5 empty buckets; no water block next to the barrel (screenshot the block)"
  - "right-click the barrel with an empty hand: the barrel screen opens from the block; screenshot the GUI (the tank half full) at GUI scale 2 and 3"
  - "close; screenshot the placed barrel from above at an angle: the tinted fluid surface at half height"
  - "right-click once with an empty bucket: the hand holds a water bucket, the tank reads 4000 mB, no water placed in the world"
  - "rain: tfp debug barrel fill 0, then tfp debug barrel rain on (the barrel under open sky); wait 400 ticks (20 s); tfp debug barrel: +100 mB minecraft:water (+-4 mB for the tick phase); tfp debug barrel rain off"
plan:
  - "Run PAR-PROC-35's in-game steps (copied above) on Paper 1.21.11 with the headless client (docs/testing/headless.md, CraftEngine self-host on 127.0.0.1) through a Haiku agent; Opus judges each shot against PAR-PROC-35's criteria. Write verdict.md in docs/verification/PAR-PROC-44/ and copy the crops into docs/verification/PAR-PROC-35/ so that item's evidence path resolves."
  - "A failure is fixed through a follow-up item owning the failing files (paper/barrel/**); do not reopen PAR-PROC-35 unless one of its criteria fails."
  - "Done: in-game run at fd00cbb on Paper 1.21.11 + client 1.21.11 (GUI scale 2 and 3): 5 water buckets -> 5000 mB, 5 empty buckets, no world water; screen from the block with the half tank; surface plane at y 0.5078; empty bucket -> 4000 mB; forced rain 115 mB over 460 ticks. All five checks pass. Evidence and verdict.md in docs/verification/PAR-PROC-44/, crops copied to docs/verification/PAR-PROC-35/. Note: the debug rain switch is `rain true|false`, not `on|off`. No tests (in-game check only)."
tests:
  - "none (in-game check only)"
done_criteria:
  - "Five water buckets give 5000 mB with five empty buckets back and no world water; the GUI opens from the block showing the half tank; the placed barrel shows the surface at half height; an empty bucket takes 1000 mB back; rain adds 100 mB per 400 ticks"
  - "verdict.md with HEAD commit, versions, steps and pass/fail per criterion in docs/verification/PAR-PROC-44/"
---

# PAR-PROC-44: Barrel wiring in-game verification

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
