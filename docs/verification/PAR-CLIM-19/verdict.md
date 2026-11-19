# PAR-CLIM-19 verdict

Status: PENDING IN-GAME CHECK (container cannot run a client).

The item owns only `docs/verification/PAR-CLIM-19/**` and lists no tests, so no automated case can be added here. The
server-side counts (0 -> 60 layers in January, 60 -> 5 after the July melt) are already recorded in
`docs/verification/PAR-CLIM-03/verdict.md`. PAR-CLIM-04 (snow piles, ice piles) is done.

Still to run on a machine with a client: the `in_game_steps` of `plans/parity/items/CLIMATE/PAR-CLIM-19.md`
(seed 1234 world, room at chunk 0,-124, shots 00 to 03, F3 targets on a snow layer, the pile over short_grass and the pool).
Pass or fail is judged against the item's `done_criteria`; store the PNG crops next to this file.
