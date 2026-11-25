# PAR-PROC-48 verdict

Date 2026-11-25, HEAD 14fd4e2, Paper 1.21.11, vanilla 1.21.11 client, Bot creative.

| Step | Result | Evidence |
|---|---|---|
| place tfp:wood/loom/oak; it faces the player (`facing=north`, Bot looked south) | PASS | loom-placed.png |
| 16 tfp:wool_yarn, one per right-click | PASS: slot 0: 16 yarn after 16 clicks | |
| push with an empty hand: progress counts, cloth appears | PASS: LoomState progress 4, then 9, then the 16th accepted push gave the cloth item to the hand (pushes closer than about 1.5 s apart are ignored) | cloth-in-hand-after-16-pushes.png |
| weave sound | NOT CHECKED (headless client has no sound) | |
| sneak + empty hand takes the yarn back | NOT RUN | |
| the in-progress cloth is visible on the loom | FAIL (expected: not implemented; the loom shows only its frame while weaving) | loom-placed.png |

Verdict: weaving works; the in-progress visual is still to do, so the item stays open.
