---
id: PAR-WORLD-24
title: "Noise and noodle caves: TFC's density caves (cave cheese, cave layer, spaghetti, pillars, noodles) in the terrain fill, and the surface over carved blocks"
area: WORLD
status: open
wave: 21
deps: [PAR-WORLD-06, PAR-WORLD-07]
size: M
aliases: ["parity audit 2026-11-08: ledger divergence 'TFC's terrain is a per-block density ... noise and noodle caves interpolated over 4 x 8 x 4 cells' says 'Not ported: the noise and noodle caves' with revisit M2-GEN-09, but PAR-WORLD-06 (M2-GEN-09) ports only the cave and canyon carvers"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java"
  - "src/main/java/net/dries007/tfc/world/noise/NoiseSampler.java"
  - "src/main/java/net/dries007/tfc/world/noise/TrilinearInterpolator.java"
  - "src/main/java/net/dries007/tfc/world/noise/ChunkNoiseSamplingSettings.java"
  - "src/generated/resources/data/tfc/worldgen/density_function/noise_caves.json"
  - "src/generated/resources/data/tfc/worldgen/density_function/noodle_caves.json"
  - "src/generated/resources/data/tfc/worldgen/density_function/noodle_thickness.json"
  - "src/generated/resources/data/tfc/worldgen/density_function/noodle_ridge_a.json"
  - "src/generated/resources/data/tfc/worldgen/density_function/noodle_ridge_b.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/density/NoiseCaves*.java"
  - "core/src/main/java/dev/tfp/core/worldgen/density/NoodleCaves*.java"
  - "core/src/main/java/dev/tfp/core/worldgen/density/VanillaDensity*.java"
  - "core/src/main/java/dev/tfp/core/worldgen/density/CellInterpolator*.java"
  - "core/src/test/java/dev/tfp/core/worldgen/density/NoiseCaves*"
  - "core/src/test/java/dev/tfp/core/worldgen/density/NoodleCaves*"
  - "core/src/test/java/dev/tfp/core/worldgen/density/VanillaDensity*"
  - "core/src/test/resources/dev/tfp/core/worldgen/noisecaves/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcTerrain*"
ui: true
in_game_steps:
  - "Fresh world created after this item; spectator mode; tp Bot to the cave sample positions printed by the itest case (y 0..50 under a plains and a mountain chunk)"
  - "time set noon; effect give Bot minecraft:night_vision 600; screenshot a cheese cave chamber, a spaghetti tunnel and a noodle cave"
plan:
  - "Current (audit 2026-11-08): the terrain decides every block with TFC's per-block density (`SurfaceHeights.Column.solid`), but TFC's `ChunkNoiseFiller` also subtracts its noise caves and carves noodle caves (`noiseCaves`, `noodleToggle`, `noodleThickness`, `noodleRidgeA/B`, interpolated over 4 x 8 x 4 cells by `TrilinearInterpolator`). These are TFC's main underground caves; PAR-WORLD-06 adds only the carver tunnels and canyons, so the large cheese chambers, spaghetti tunnels, pillars and noodles are missing and ores, cave groundcover and underground features (PAR-WORLD-20) see far less open rock than in TFC."
  - "Port, platform-free in core/worldgen/density, the vanilla 1.21.1 density-function nodes the five TFC density files use (noise with xz/y scale, add, mul, min, max, clamp, square, abs, cube, range_choice, y, the referenced vanilla noises cave_cheese, cave_layer, cave_entrance, spaghetti_2d*, spaghetti_3d*, spaghetti_roughness*, pillar*, noodle* with their NormalNoise parameters and the world-seed positional random), reading the exported JSON rather than hard-coding the graph. Keep TFC's interpolation exactly: sample at the 4 x 8 x 4 cell corners and trilinearly interpolate as `TrilinearInterpolator`, and TFC's combination order in `ChunkNoiseFiller` (noise caves with the terrain density, then the noodle test: toggle >= 0, thickness map 0.05..0.1, ridges `abs(1.5 * r) - thickness`)."
  - "Wire into `TfcTerrain.fill`: the carved positions go through the existing aquifer decision (air, water or lava as TFC's aquifer) and mark carved columns for the carvers and decorations; decide async on the worldgen thread like the rest of the fill."
  - "Surface under overhangs: where a cave or shore density opens blocks under the top, run TFC's surface pass on the real column (the first solid block below air), so cave mouths and overhang floors get the soil/sand TFC gives them (ledger row 'surface builder covers the terrain top')."
  - "Rerun PAR-WORLD-19's ore census only if PAR-WORLD-19 is still open; otherwise record the new exposure numbers in docs/spikes/S1-ore-overlay.md through a follow-up item."
  - "Ledger: replace the 'not ported: the noise and noodle caves' part of the density row with the implemented behaviour; add a divergence row for anything kept different (for example the beardifier, which stays out because there are no structures)."
tests:
  - "core: VanillaDensityGoldenTest: each ported node and the vanilla noises equal vanilla 1.21.1's DensityFunction values from a scratch oracle (1,000 positions per noise, 1e-9)"
  - "core: NoiseCavesGoldenTest: solid/open masks of 20 chunks (full height) equal TFC's ChunkNoiseFiller output from the oracle on the pinned checkout"
  - "core: JMH or timing guard that the cave sampling keeps the worldgen chunk budget of PAR-WORLD-03"
  - "itest: worldgen.caves case: open-block fraction below y 50 per biome sample within TFC's range"
done_criteria:
  - "Noise and noodle caves generate with golden-exact masks; the density ledger row is updated; chunk budget kept"
  - "Screenshots of a cheese chamber, a spaghetti tunnel and a noodle cave pass review"
---

# PAR-WORLD-24: Noise and noodle caves: TFC's density caves (cave cheese, cave layer, spaghetti, pillars, noodles) in the terrain fill, and the surface over carved blocks

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
