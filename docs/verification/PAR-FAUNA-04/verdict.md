# PAR-FAUNA-04 verification
2026-11-20, HEAD fe8551d, fresh world (TFP_WORLD=1), Paper 1.21.11.
- census_1.png: PASS. `tfp debug census 128` at spawn after 90 s: 13 TFC animals of 4 species (cow 9, deer 2, duck 1, sheep 1), stats: new chunks 764, planned 1187, no place 188, gate refused 987, spawned 12, top-up passes 24. A second census 3 minutes later had the same counts (the 8 per chunk cap and the climate gate hold).
- animals_near.png: animals standing in the terrain (sheep, drake, deer on ledges).
- Offline check (core, 2,000 chunks per biome and climate): about 0.08 to 0.12 planned animals per chunk pass TFC's gate, species follow the climate (polar-only species never appear at 18 C, hyena and lion do).
- The first in-game runs found 0 to 3 animals because the placement refused leaves and plants and the top-up was one attempt per 600 ticks; fixed (look through leaves, 12 attempts per 400 ticks).
