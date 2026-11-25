# PAR-MECH-19 verdict (PAR-MECH-12 in-game check)

Date 2026-11-25, HEAD 14fd4e2 plus the PAR-MECH-24 content fix, Paper 1.21.11, vanilla 1.21.11 client.
Setup of PAR-MECH-16 at speed step 8.

| Criterion | Result | Evidence |
|---|---|---|
| the axles are drawn as shafts, in a network at 30 rpm | PASS: the x line and the z axle are drawn (line-overview in docs/verification/PAR-MECH-16) | |
| the shaft turns | PASS qualitatively: eight consecutive screenshots of the z axle seen end-on show it at eight different angles | z-axle-frames-end-on.png |
| a quarter turn in 10 ticks, opposite hands either side of the gearbox, step 0 stops them, relog sees them turning, break removes the display | NOT MEASURED: screenshots are not tick-aligned (about 0.3 s apart) so rate and hand cannot be read from them; step 0, relog and break not run | |
| 26.3 client/server | NOT RUN | |

Verdict: partial. The spinning display works; speed, hand and 26.x stay Pending in-game check (a tick-accurate capture needs a frame recorder
or a debug readout of the display's rotation).
