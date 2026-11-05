# PAR-INFRA-33 verification: CE block hit and break sound volume/pitch

- Date: 2026-11-05. Base commit f47a3fa plus this item's change (dist jar built from the working tree).
- Servers: Paper 1.21.11 and Paper 26.3, CraftEngine 26.9.2, PacketEvents 2.14.0, flat world, S2 spike pack
  (`spikes/s2-destroy-stages/build-pack.sh` before the first start) and the spike plugin, `EXTRA_JVM=-Dtfp.blocksound.log=true`.
- Clients: two headless vanilla clients of the same version, `Bot` (the breaker, `ALSOFT_DRIVERS=null`,
  `showSubtitles:true`) and `Viewer` (a second player 3-4 blocks away).
- Steps: `docs/spikes/S2-destroy-stages.md` §2 scene; `s2 place tfps2:metal/block/copper` twice and `s2 place tfp:bellows`
  from Bot's chat; `ce item give Bot tfp:metal/pickaxe/bronze`; Bot mines each block with `xdotool mousedown 1`.
- Evidence: `1.21.11-sound-log.txt`, `26.3-sound-log.txt` (the spike's `[S2] sound <id> volume= pitch= to <player>` lines,
  logged at PacketEvents MONITOR after our rewrite, interleaved with our `[tfp-sound] SET|DROP` lines; a dropped packet
  still shows in the `[S2]` log because the spike logs cancelled packets too), `26.3-copper-break-subtitles.png` and
  `26.3-subtitles-x3.png` (Bot's subtitles after a copper break: one "Block breaking", one "Block broken").

| Criterion | 1.21.11 | 26.3 |
|---|---|---|
| Copper (TFC `SoundType.METAL` 1.0/1.5) hit while mining: TFC 0.25/0.75 | pass: every `block.metal.hit` 0.5/0.5 rewritten to 0.25/0.75 | pass |
| Copper break for the breaker: one sound at TFC 1.0/1.2 | pass: CraftEngine's own `block.metal.break` 1.0/1.2 kept, its level-event `replaced.block.metal.break` 1.0/0.8 dropped | pass |
| Copper break for the second viewer: TFC 1.0/1.2 | pass: `replaced.block.metal.break` 1.0/0.8 rewritten to 1.0/1.2 | pass |
| Device (bellows, TFC `SoundType.WOOD` 1.0/1.0) hit: TFC 0.25/0.5 | pass: `block.wood.hit` 0.5/0.5 rewritten to 0.25/0.5 | pass |
| Device break: breaker one sound, viewer one sound, at 1.0/0.8 | pass (breaker's duplicate dropped; viewer's already 1.0/0.8, untouched) | pass |
| Vanilla blocks untouched | pass: stone/dirt `hit`/`break` packets pass unchanged (not in the table) | pass |

Firepit (`NETHER_WART`): not testable in game. Firepit, grill and pot live in the CE pack's `devices/` folder, which
CraftEngine does not load until their behaviour exists (PAR-HEAT-06/08/09); `s2 place tfp:firepit` returns false. Their
values are covered by `DeviceSoundVolumeTest` (NETHER_WART = 1.0/1.0: hit 0.25/0.5, break 1.0/0.8) and the table reads
their files (`CeBlockSoundTableTest.theGeneratedDevicesAreInTheTable`). The bellows is the loaded device used instead.

Verdict: pass on both versions.
