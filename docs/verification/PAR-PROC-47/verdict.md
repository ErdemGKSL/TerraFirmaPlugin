# PAR-PROC-47 verdict

Date 2026-11-25, HEAD 14fd4e2, Paper 1.21.11, vanilla 1.21.11 client. PARTIAL, findings in PAR-PROC-53.

| Step | Result | Evidence |
|---|---|---|
| place tfp:wood/sluice/oak: both halves, model drawn | PASS (upper half `tfp:wood/sluice/oak[facing=south,upper=true]`; the model is a ramp) | sluice-ramp-upper-half.png |
| water appears at the output two ahead and one down | PASS only after a neighbour update (water that arrives after the placement is not noticed): output water in the pit | sluice-output-water.png |
| thrown deposits are taken; loot every 5 s | PARTIAL: thrown items leave the ground, but no loot appeared in 25 s and the device state did not advance | |
| water removed stops it; break one half removes the other, output water and drops soil | NOT RUN | |
| selection outline per half, model alignment per facing | NOT CHECKED | |

Verdict: not passed. Follow-up PAR-PROC-53.
