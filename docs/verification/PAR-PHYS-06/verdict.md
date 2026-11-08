# PAR-PHYS-06 in-game check

- Date: 2026-11-08
- Code: the PAR-PHYS-06 commit (base `7a4a7c7`), jar built from this worktree (`:dist:shadowJar`, shared generated content).
- Server: Paper 1.21.11, CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents 2.14.0, TFP world generator
  (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted). Log: `Feature modules: ... bucket, isolated`; no
  errors from our plugin (the only exception is PacketEvents' update check, HTTP 403).
- Client: vanilla 1.21.11 under Xvfb, 1280 x 720 (`spikes/client/session.sh`), pack applied on join.
- Run by the worker session itself (no subagent tool was available in this session).

## Steps (RCON + `xdotool click 3`, Bot in survival)

1. Stone platform at y 149 near spawn (-1519, 150, -2303), air above; a stone block S at (-1519, 150, -2300);
   `ce item give Bot tfp:rock/raw/granite 4`.
2. Bot right-clicks the top of S: raw granite placed at (-1519, 151, -2300) and stays (`01-placed-on-stone-stays.png`).
3. A torch on the platform at (-1515, 150, -2300) (`02-torch-before.png`); Bot right-clicks the torch top: the raw granite
   placed above it (only neighbour: the torch, no collision) shows break particles 50 ms after the click, the block is
   gone (`03-placed-on-torch-popped-50ms.png`), and one dropped item lies there: `item_model "tfp:rock/raw/granite"`
   (the raw block, not loose rocks) (`04-placed-on-torch-dropped-raw-granite.png`). Hotbar count 4 -> 3.
4. The raw granite on S is still in place minutes later (`05-raw-on-support-before.png`). `setblock -1519 150 -2300 air
   destroy` breaks S (neighbour update): the raw granite pops at once; the drops are cobblestone (from S) and
   one `tfp:rock/raw/granite` (`06-support-broken-popped-raw-granite-drop.png`; `execute if block -1519 151 -2300 air`
   passes).

Screenshots are 960 x 540 centre crops, scaled to 640 x 360.

## Verdict

| criterion | result |
|---|---|
| A floating placed raw rock pops and drops itself (raw granite item, not loose rocks) | pass (steps 3 and 4) |
| A supported raw rock stays | pass (steps 2 and 4) |
| Tests green | pass: `:core:test :paper:test :tools:datagen:test` (core `IsolatedRuleTest` 21, paper `IsolatedMockBukkitTest` 12) |
| Ledger rows updated | pass |

Note: an earlier attempt to place against a poppy replaced the poppy (the client's placement went through it), so the
"flower only" case was checked with a torch; the MockBukkit test covers both.
