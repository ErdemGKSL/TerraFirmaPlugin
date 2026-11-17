# PAR-INFRA-55 verification

- Date: 2026-11-17
- Commit under test: 9d4c31c (the fix); evidence recorded in the repair commit on top of it
- Server: Paper 1.21.11 headless (`spikes/client/server.sh paper 1.21.11`, TFP_WORLD=1), CraftEngine self-host on 127.0.0.1
- Client: headless client, bot `Bot`, RCON; steps run by a Haiku agent, judged by the main session

| step / criterion | result |
|---|---|
| Priming start raises `block.serverside-blocks` (2000 -> 3000) and logs RESTART REQUIRED; restart with no manual `ce reload` | pass: the restart logged "Rebuilding the CraftEngine resource pack (... serverside-blocks was raised at the previous start ...)", then "Generated resource pack" and "CraftEngine resource pack rebuilt." |
| Hosted pack `plugins/CraftEngine/cache/hosted/default/resource_pack.zip` holds `assets/minecraft/blockstates/iron_trapdoor.json` and `campfire.json` | pass (zip rewritten by the restart, 19945 entries) |
| Sneak-place one oak log on stone: only TFC's one-log model, no vanilla iron trapdoor | pass: `log-pile-on-stone-gui2.png`, `log-pile-on-stone-gui3.png`, `log-pile-on-stone-zoom.png` show one bark-textured log along one edge inside the pile's full-block outline, no trapdoor plate |

Notes: the oak-log item lying on the grass to the left is the drop of a first log that was placed on grass by an aiming error
and removed again; it is not part of the check. The priming start's "Cannot allocate enough real block states" warnings are
the expected symptom this item fixes on the restart.

Verdict: pass.

## Re-run 2026-11-17 (repair round, main session)

The verify stage's tester could not get a sneak click to register and left step 3 unverified (its plain right click placed
a vanilla log cube, which is correct: the pile needs sneaking). Re-run by the main session on HEAD 2c4e2c3 (fix 9d4c31c
unchanged), fresh run dir, Paper 1.21.11, port 25720, PACK_PORT 8271:

1. Priming start (TFP_WORLD=1): `block.serverside-blocks: 2000 -> 3000`, RESTART REQUIRED, 760 "Cannot allocate" refusals.
   Restart without any reload: "Rebuilding the CraftEngine resource pack (first install of our CraftEngine pack;
   CraftEngine block.serverside-blocks was raised at the previous start, so its pack missed the blocks that could not
   allocate then)", "Generated resource pack", 0 refusals. Pass.
2. Hosted `resource_pack.zip` holds `assets/minecraft/blockstates/iron_trapdoor.json` and `campfire.json`. Pass.
3. Stone floor, survival, 64 oak logs, looking down north. Input that works with xdotool: `keydown shift`, wait 1 s, `click
   3`, wait 0.5 s, `keyup shift`. One log was taken (64 -> 63) and the readout (`/tfp debug block`) shows
   `tfp:log_pile[axis=z,count=1]` at -448 74 -446. The client draws TFC's one-log model (one 4x4x16 log on the east edge)
   inside the flat outline, no iron trapdoor plate: `01-gui2-one-log-pile-readout.png` (GUI scale 2),
   `02-gui3-one-log-pile-readout.png` (GUI scale 3, client restarted with `MC_OPTIONS=guiScale:3`). Pass.

Verdict: pass. The temporary PAR-PROC-37 stale-pack divergence row is removed from the ledger.
