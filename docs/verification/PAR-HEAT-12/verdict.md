# PAR-HEAT-12 verification

- Date: 2026-11-17
- Code under test: 9032fa5 (PAR-HEAT-12: Firebox, stove and stove pot)
- Server: Paper 1.21.11 headless, CraftEngine self-host on 127.0.0.1 (pack applied); client: vanilla 1.21.11 under Xvfb, bot `Bot`
- Who ran it: the verify-stage tester; judged by the item worker in the repair round

## Results

| step / criterion | result | evidence |
|---|---|---|
| `./gradlew check` | pass (core 2978, paper 1336, itest 45, dist 8, integration modules) | - |
| `tfp debug place tfp:firebox[lit=true]` | not loaded, as expected at this commit: "tfp:firebox is not a loaded custom block." Datagen does not run `firebox/FireboxBlock` until PAR-HEAT-31 declares the full-cube pool (`HitboxDemands.FULL_CUBE_POOLS`, outside this item's files) | `01-firebox-not-loaded-chat.png` |
| right-click; screenshot GUI (scale 2 and 3) | not runnable here (no firebox block) | - |

## Verdict

The in-game screen check is deferred under PROTOCOL 6.5: the steps and the criterion are in PAR-HEAT-31 (deps PAR-HEAT-23,
PAR-HEAT-25, PAR-INFRA-13; it does not depend on PAR-HEAT-12), which loads the block and writes its evidence to this folder.
The item's front matter has `ui: false`, no `in_game_steps`, and the criterion "Deferred check tracked in PAR-HEAT-31". The
crucible self-test is deferred the same way to PAR-HEAT-32 (deps PAR-HEAT-31, PAR-POT-01, PAR-METAL-12). The tester's
blocked result is the expected outcome of the stale `ui: true` steps, not a defect of this item.
