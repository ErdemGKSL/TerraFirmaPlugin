# PAR-PROC-53 verdict

Date 2026-11-25, Paper 1.21.11 + vanilla client 1.21.11, headless, HEAD 107b254 plus the fix. Setup: tfp:wood/sluice/oak facing south placed first,
stone channel with a source 7 blocks behind the input cell, pit two ahead of the upper half; the source was placed last.

| Step | Result |
|---|---|
| Water arrives after the sluice: output water appears in the pit with no poke (output-water-appeared.png) | PASS (before: never) |
| Deposits dropped on the upper half are taken and, after the cycle, loot items exist at the output (RCON entity query: items near the output; one `tfp ore topaz` item seen) | PASS (loot seen by entity query, not by screenshot: the pit hides items under water) |
| Outline of both halves: the selection box follows the ramp, the model reads as a sloped board ramp (deposits-taken.png) | looks right; compared with TFC by eye only |

Causes: (1) no trigger when water arrives: new `SluiceWater` listener (BlockFromToEvent, FluidLevelChangeEvent) wakes the up-to-4 sluices that
could have the changed cell as input. (2) `SluiceIntake.isUpperSluice` compared the block id with `tfp:wood/sluice` exactly, while real blocks are
`tfp:wood/sluice/<wood>`, so a real sluice was never found (the MockBukkit test used the bare id); thrown items were not taken by it. Not reproduced:
"taken but no loot": the loot path works once the sluice is found.
