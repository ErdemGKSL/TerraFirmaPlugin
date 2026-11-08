# PAR-FLUID-09 in-game check: fill a wooden bucket from a river and place it

- Date: 2026-11-08
- Code: branch HEAD `3a863ae`, jar built from this worktree (`:dist:shadowJar`, shared generated content). The bucket code
  (`core/bucket`, `paper/bucket`, `tools/datagen/bucket`) is unchanged since PAR-FLUID-02.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents
  2.14.0, TFP world generator (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted; seed
  `-8040600912916311988`). Log: `Feature modules: ... worldwater, bucket, ...`; no errors from our plugin.
- Client: vanilla 1.21.11 under Xvfb, 1280 x 720, GUI scale 2 (`spikes/client/session.sh`), pack applied 3 s after the join.
- Run and judged by the worker session itself (no subagent tool was available in this session).
- `/tick freeze` was used around the fill and the place clicks so the frame shows the world change before vanilla fluid
  ticks act on it (players still interact while the game is frozen); `/tick unfreeze` right after.

## Steps

1. River found with `locate biome #minecraft:is_river` (the stand-in key of every TFC lake and river biome), a surface-water
   scan over RCON and `/tfp debug water Bot` (typed by Bot): `Bot at -2370 63 -581: river water, river flow sw, biome river`.
   Bot in survival on the stone bank at (-2365.5, 65, -587.5), facing south, pitch 45; the crosshair on the river source
   (-2366, 63, -585), 3.5 blocks away.
2. `ce item give Bot tfp:wooden_bucket`: empty bucket in hotbar slot 1 (`01-river-before-empty-bucket.png`,
   `10-hotbar-empty-x4.png`).
3. Right-click (`xdotool click 3`): RCON `execute if block -2366 63 -585 minecraft:water[level=0]` passed before and
   failed after; the hole shows at the crosshair; the held bucket and the hotbar icon show the blue water layer
   (`02-river-filled-source-gone.png`, `11-hotbar-water-x4.png`). The item: `max_stack_size` 1, `custom_model_data`
   flag 1. Tooltip: `Wooden Bucket` / `Medium / Large` / `1000 / 1000 mB of Water` (`03-river-tooltip-water.png`).
   After `/tick unfreeze` the hole refilled within 2 s: vanilla water converts a hole between two sources back to a source,
   as the vanilla water fluid type of TFC's river water does.
4. Pitch 50 towards the bank (yaw -45), right-click on the stone top: a level-7 flowing water block at (-2365, 65, -587)
   (RCON `water[level=7]` passed), the bucket empty again, `max_stack_size` back to 16, no `custom_model_data`
   (`04-placed-flowing-water-bucket-empty.png`, `12-hotbar-emptied-x4.png`). 2 s after unfreeze the block is air
   (`05-flowing-water-dried-up.png`).
5. Ocean: `locate biome minecraft:deep_ocean`, `/tfp debug water Bot`: `Bot at -3279 63 -3214: salt water, river flow none,
   biome ocean_ridge, surface height 24`. A barrier under Bot at (-3279, 63, -3214), a new wooden bucket, right-click on
   (-3279, 63, -3213) (source removed): tooltip `1000 / 1000 mB of Salt Water` (`06-ocean-tooltip-salt-water.png`).
6. Lava: `setblock -2365 64 -587 minecraft:lava` in the bank, `ce item give Bot tfp:metal/bucket/red_steel`
   (`07-lava-source-red-steel-empty.png`), right-click: the block is air, the red steel bucket shows the orange lava layer
   (`08-lava-picked-up-red-steel-filled.png`, `13-hotbar-lava-x4.png`), tooltip `Red Steel Bucket` /
   `1000 / 1000 mB of Lava` (`09-red-steel-tooltip-lava.png`).

## Verdict

| criterion | result |
|---|---|
| Filling a wooden bucket from a river: the source disappears, the bucket shows the blue fluid layer and the tooltip `1000 / 1000 mB of Water` | pass (steps 2-3) |
| Emptying it on the ground places a transient flowing water block and the bucket looks empty again | pass (step 4) |
| Ocean: the tooltip reads Salt Water | pass (step 5) |
| Red steel bucket picks up lava and shows the lava layer | pass (step 6) |
| Tests green | pass: `:core:test :paper:test :tools:datagen:test` (no code changed in this item) |

The filled bucket keeps the name `Wooden Bucket` (TFC: `Water Wooden Bucket`); that is the recorded PAR-FLUID-01
divergence on filled container names, not new.

## Defect found (follow-up PAR-FLUID-13)

With `gamerule advance_time false` (set at first to keep noon light) the filled bucket could not be emptied: every later
right-click was cancelled. `BucketListener.onInteract` guards against the main-hand and off-hand events of one click with
the last use 'tick', taken from `World.getFullTime()`, which is the day time and does not change while the daylight cycle
is off. Setting `advance_time true` made the next click work at once. The checks above ran with the daylight cycle on.
Fix tracked in `plans/parity/items/FLUID/PAR-FLUID-13.md` (BucketListener.java is a PAR-FLUID-02 / PAR-FLUID-10 file).
