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
