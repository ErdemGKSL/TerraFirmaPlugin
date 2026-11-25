# PAR-PROC-47 verdict

Date 2026-11-25 (second run), HEAD 0b20422, Paper 1.21.11 + vanilla client 1.21.11, headless. PARTIAL: Pending in-game check (deposit intake and loot, water removal, break). The first run is in PAR-PROC-53's evidence.

| Step | Result | Evidence |
|---|---|---|
| `tfp:wood/sluice/oak` placed on a floor block facing the player's look direction: both halves appear, the model is a two-block ramp that descends toward the output pit | PASS | sluice-ramp-both-halves-facing-pit.png |
| Water placed after the sluice (a 7-cell stone channel, flowing level 1 in the input cell above and behind the upper half): the output water appears in the pit two ahead and one down, with no poke | PASS (RCON `execute if block` confirmed water in the pit) | output-water-after-source-placed.png |
| Throw 4 deposits onto the upper half: each is taken after about 1 s | PARTIAL: the first two thrown vanished (taken); later throws were either picked up again by the creative Bot standing on the drop point or lay on the ramp; the loot of 12 deposits (small cassiterite with chance 0.5 per cycle) was not seen at the output | deposits-on-ground-not-taken.png |
| Remove the water source: the countdown stops; break one half: the other half and the output water go, soil is dropped | NOT RUN (the break click missed the block) | |
| Selection outline of both halves against TFC | NOT CHECKED beyond PAR-PROC-53's by-eye note | |

Verdict: not passed. The remaining steps need a controlled setup (drop from a hopper-less dispenser or a thrown stack from a distance, with the player away from the drop point).

## Third run (2026-11-25, with PAR-PROC-58's code fix)

Sluice rebuilt (placed first, source water last): the output water appears (PASS again). Deposits thrown from 4 blocks away landed on the upper half and were not taken: within a second they were carried over the output pit and came to rest 3.4 blocks past the upper half (RCON position, deposits-swept-past-the-pit.png in PAR-PROC-58), so no loot. Cause hypothesis: the input water spreads over the ramp and sweeps the items; follow-up in PAR-PROC-58. Water removal and break: not run. Still pending in-game check.
