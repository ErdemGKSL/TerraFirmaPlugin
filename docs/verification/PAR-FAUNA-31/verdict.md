# PAR-FAUNA-31 verdict

Date: 2026-11-25. Rendered headlessly with tools/models/render/render.mjs (Blockbench web 5.x in Playwright Chromium); no server or client (container cannot run the client, so the in-game steps are pending, tracked in PAR-FAUNA-40).

| Criterion | Result |
|---|---|
| Penguin `play_dead` lies on its back (core -90 degrees about x) | pass: `penguin_play_dead_side.png` (flat) against `penguin_walk_side.png` (upright) |
| Turtle `play_dead` draws head and four legs in | pass: `turtle_play_dead.png` (shell and plastron only) against `turtle_idle.png` |
| validate.py clean on both models | pass (`--lenient`) |
| In game: hit penguin and hit turtle | pending: needs the published models (PAR-FAUNA-40) and a client |
