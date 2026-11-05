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
| Device break: breaker one sound, viewer one sound, at 1.0/0.8 | not captured on 1.21.11 (the log after 13:24:55 holds two digs' `block.wood.hit` lines only); same version-independent code path as 26.3, and the breaker drop is shown on 1.21.11 with copper | pass (log lines 89-93: breaker's duplicate dropped; viewer's already 1.0/0.8, untouched) |
| Vanilla blocks untouched | pass: stone/dirt `hit`/`break` packets pass unchanged (not in the table) | pass |

Firepit (`NETHER_WART`): not testable in game. Firepit, grill and pot live in the CE pack's `devices/` folder, which
CraftEngine does not load until their behaviour exists (PAR-HEAT-06/08/09); `s2 place tfp:firepit` returns false. Their
values are covered by `DeviceSoundVolumeTest` (NETHER_WART = 1.0/1.0: hit 0.25/0.5, break 1.0/0.8) and the table reads
their files (`CeBlockSoundTableTest.theGeneratedDevicesAreInTheTable`). The bellows is the loaded device used instead.

The `replaced.block.grass.break` 1.0/0.8 lines to both clients (1.21.11 13:24:05, 26.3 13:42:31) have no `[tfp-sound]`
line next to them: the rewrite passed them untouched because no CE block is tracked at that position. They are
CraftEngine's level-event translation of a vanilla grass break near the scene, not a misfire.

Verdict: pass on both versions (the 1.21.11 bellows break was not captured; see the table).

## Independent run at d97267c (2026-11-05)

A second headless run (Paper 1.21.11 + client 1.21.11, Paper 26.3 + client 26.3, `Bot` breaker and `Watcher` viewer, a
vanilla iron pickaxe because its `/ce item give tfp:metal/pickaxe/bronze` had no player argument) reproduced the copper half on both versions: `block.metal.hit` 0.25/0.75 to Bot, `block.metal.break` 1.0/1.2 to
Bot, `replaced.block.metal.break` 1.0/1.2 to Watcher (the 1.0/0.8 `replaced.` line to Bot is the dropped duplicate, which
the spike logs anyway). The run's device step named the firepit, which has no item and is not a loaded CE block
(`tfp:firepit is not a loaded custom block.`), so it could not run. The item's step now names the bellows (checked above);
the firepit/grill/pot NETHER_WART check is a pending step and criterion in PAR-INFRA-23 (PROTOCOL 6.5). Harness note, not
this item: `session.sh` on 26.3 waits for `Reloading ResourceManager: vanilla, server` while 26.3 logs
`vanilla, vanilla, server/...`, a false "pack not applied" result.

## Answer to the in-game REJECT of 1669969 (2026-11-05)

1. "Two break sounds to the breaker, `replaced.block.metal.break` 1.0/0.8": that packet is cancelled. Our handler returns
   `PacketDecision.CANCEL` (`PacketEventsLayer` sets `event.setCancelled(true)`); the spike's `SoundLog` listens at
   PacketEvents MONITOR without checking `isCancelled()`, and PacketEvents' `EventManager.callEvent` calls every listener
   regardless of cancellation, so the dropped packet is still printed. In both logs here each such line follows a
   `[tfp-sound] DROP minecraft:replaced.block.metal.break 1.0/0.8 -> 0.0/0.0 to Bot` line, and
   `26.3-copper-break-subtitles.png` shows one "Block broken". The rejecting run did not set `-Dtfp.blocksound.log=true`,
   so it had no `DROP` lines to pair with. `BlockSoundRewriteTest.theBreakerHearsOneBreakSound` covers the drop. The
   `[tfp-sound]` log now names the player (`to Bot (<uuid>)`; the logs above were written when it printed the UUID, which
   was mapped to the client name by hand) and marks a dropped packet `(cancelled; a MONITOR listener may still log it)`.
   Making `SoundLog` print `cancelled=` is a harness change outside this item.
2. Hit values: confirmed by that run (0.25/0.75 on both versions).
3. Firepit: `tfp:firepit` is not a loaded CraftEngine block (the `devices/` folder loads with PAR-HEAT-06/08/09), so no
   placement path (CE API, setblock or item) can place it today. The bellows above is the loaded device; the NETHER_WART
   check is the pending step and criterion in PAR-INFRA-23 (PROTOCOL 6.5).
4. Bronze pickaxe: `tfp:metal/pickaxe/bronze` exists (generated `metal.yml`, base material `iron_pickaxe`, so the dig
   log's `tool=IRON_PICKAXE` is the CE item too). CraftEngine's command is `/ce item give <player> <id>`; the rejecting run
   left out the player. The step now spells the command out.
5. Visuals: the item's evidence is the SoundLog; the screenshots only show one "Block broken" subtitle. The purple glyphs
   and the slime deaths are in that run's frames only (its screenshots are not in this folder) and were not reproduced
   here; they do not affect a sound value. The step now asks for `difficulty peaceful`; the next capture should check
   where the glyphs come from.
6. `session.sh` on 26.3 (`vanilla, server` vs `vanilla, vanilla, server/...`): harness follow-up outside this item.

## Unblock (2026-11-05): the dropped packet is now marked in the SoundLog itself

The REJECTs of the "one break sound per break" criterion all came from one root cause: `SoundLog` printed the cancelled
duplicate like any other packet. `SoundLog` now ends every line with `cancelled=<bool>` (`event.isCancelled()` at MONITOR,
after our NORMAL-priority `PacketEventsLayer` bridge has cancelled it), and the item's in-game step requires
`-Dtfp.blocksound.log=true`. In the logs above, every breaker-side `replaced.block.*.break` 1.0/0.8 line directly after a
`[tfp-sound] DROP` line would read `cancelled=true`; the next capture shows it without pairing lines by hand. No behaviour
change in the plugin.
