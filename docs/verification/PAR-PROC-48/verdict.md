# PAR-PROC-48 verdict

Date 2026-11-25 (second run), HEAD 0b20422, Paper 1.21.11 + vanilla client 1.21.11, headless. Item stays open: the in-progress cloth is not drawn.

| Step | Result | Evidence |
|---|---|---|
| Place `tfp:wood/loom/oak`; 16 `tfp:wool_yarn`, one per right click | PASS (16 clicks load all 16) | loom-with-16-yarn-nothing-drawn.png |
| Push with an empty hand about every 1.6 s: progress counts, the 16th push finishes, the cloth is taken with an empty hand | PASS (`tfp:wool_cloth` x1 in the inventory) | cloth-in-hand-after-pushes.png |
| Sneak + empty hand takes all 16 yarn back | PASS | |
| The loom faces the player when placed | PASS (first run, loom-placed.png) | |
| Weave sound | NOT CHECKED (no sound in the headless client) | |
| The in-progress cloth is visible on the loom | FAIL (expected): with 16 yarn loaded and during the pushes the loom shows only its frame | loom-with-16-yarn-nothing-drawn.png |

Verdict: weaving works; the item stays open for the in-progress visual.
