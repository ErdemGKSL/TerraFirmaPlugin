# PAR-INFRA-35 verification: CraftEngine hit sound of template-built terrain blocks

- Date: 2026-11-06. Base commit 51356d0 plus this item's change (dist jar built from the working tree).
- Servers: Paper 1.21.11 and Paper 26.3, CraftEngine 26.9.2, PacketEvents 2.14.0, TFC world (`TFP_WORLD=1`, priming
  start then restart, `docs/testing/headless.md`), `EXTRA_JVM=-Dtfp.blocksound.log=true`. Both servers log
  `Block sound table: 1358 CraftEngine blocks` at startup (before this item: the devices only).
- Client: one headless vanilla client of the same version, `Bot`, pack applied (TFC HUD drawn).
- Steps, per block (difficulty peaceful): a vanilla stone floor at y 68 by RCON `fill`, Bot in creative standing on it
  looking straight down, `/tfp debug place <state>` from Bot's chat (replaces the floor block under Bot), Bot back to
  survival, `xdotool mousedown 1` with an iron pickaxe (granite) or iron shovel (dirt, sand) until the block breaks.
  Blocks: `tfp:rock/raw/granite[axis=y]` (TFC `SoundType.STONE`), `tfp:dirt/alfisol` (`SoundType.GRAVEL`),
  `tfp:sand/yellow` (`SoundType.SAND`); all (1.0, 1.0), so TFC's client hit is 0.25/0.5.
- Evidence: `1.21.11-sound-log.txt`, `26.3-sound-log.txt` (our `[tfp-sound] SET|DROP` lines with the commands
  around them, ANSI codes stripped).

| Criterion | 1.21.11 | 26.3 |
|---|---|---|
| Raw granite hit while mining: CraftEngine 0.5/0.5 set to TFC 0.25/0.5 | pass: 9 `block.stone.hit 0.5/0.5 -> 0.25/0.5` | pass |
| Dirt (gravel sounds) hit: 0.25/0.5 | pass: `block.gravel.hit 0.5/0.5 -> 0.25/0.5` | pass |
| Sand hit: 0.25/0.5 | pass: `block.sand.hit 0.5/0.5 -> 0.25/0.5` | pass |
| Breaker hears one break sound (the duplicate dropped) | pass: `replaced.block.stone.break`, `replaced.block.gravel.break`, `block.sand.break` 1.0/0.8 dropped for the breaker | pass |

On 26.3, after the sand and the dirt broke, Bot fell onto the generated terrain below the floor and kept digging; those
`block.stone.hit` SET lines are worldgen-placed TFC raw rock (the hole walls show TFC dirt, the drops were rock pieces),
so blocks placed by our chunk generator are covered too.

The 26.3 log also shows `/tfp debug place tfp:dirt/loam` with no sound lines: that id does not exist (TFC soils are
alfisol, andisol, ...), nothing was placed; the dirt step was repeated with `tfp:dirt/alfisol`.

Harness notes (not this item): the first 1.21.11 start used the same port for RCON and CraftEngine's pack server, so
the pack was not hosted; `ce reload all` rebuilt and uploaded it before the measured session. A `tp` into the air
kicks the client ("Flying is not enabled"), so the scene stands Bot on the floor.

Verdict: pass on both versions.
