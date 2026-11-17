# PAR-HEAT-29 verification

- Date: 2026-11-17
- Code under test: the PAR-HEAT-29 commit (on top of 0034eb7 "Claim PAR-HEAT-29"): widened vanilla-shapes dump, carrier policy,
  selector carriers `minecraft:torchflower_crop[age=1]` (dead standing torch) and
  `minecraft:big_dripleaf[facing=<f>,tilt=full,waterlogged=false]` (dead wall torch, TFC's model turned by the blockstate y)
- Server: Paper 1.21.11 build 132 headless (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second
  start), CraftEngine 26.9.2 self-host on 127.0.0.1, BetterModel, BetterHud, PacketEvents; Paper 26.3 build 253 for the 26.3
  load check
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), 1280x720, GUI scale 2 and 3 (`MC_OPTIONS=guiScale:N`),
  `debug-profile.json` with `minecraft:light_levels`, `looking_at_block`, `player_position`, `game_version` in the F3 overlay
- Who ran it: this session has no subagent tool, so the CLAUDE.md / PROTOCOL 5.3 delegation of server and client runs to a
  Haiku agent could not be done; the item worker drove the runs and judged every frame itself.
- Scratch tools kept outside the repo (session scratchpad): the state dumper (`Dump.java`, run on the four Paper jars against
  their Mojang-mapped classes; it reproduces every committed row of the old tables exactly), the analysis and table-extension
  scripts, the RCON + xdotool driver, a spike CraftEngine pack (`tfpspike`, never shipped).

## 1. Owner request first: a torch-shaped carrier with luminance 0 (CraftEngine light system)

Spike pack: `tfpspike:soul_dead_torch` on `minecraft:soul_torch` and `tfpspike:soul_dead_wall_torch` on
`minecraft:soul_wall_torch[facing]`, both `luminance: 0`, TFC's `dead_torch` / `dead_wall_torch` models
(`block.light-system.enable: true`, the default). Placed in a closed one-wide stone tunnel (no sky light).

| check | result | evidence |
|---|---|---|
| Selection outline equals the real torch's | pass by construction (the soul torch states have vanilla's torch shapes in all four versions, dump) | - |
| No flame | **fail**: the client draws the soul flame and smoke particles on both carriers whatever model is drawn (`TorchBlock.animateTick` / `WallTorchBlock.animateTick` add SMOKE and the block's flame particle unconditionally; bytecode checked in the 1.21.11 and 26.3 jars). The burnt-out torch looks lit | `01-gui2-soul-carrier-spike-dark-flames.png` |
| F3 client block light 0 in the carrier block | pass at placement: `Client Light: 0 (0 sky, 0 block)` standing in the standing spike | `02-gui2-soul-carrier-spike-f3-block0.png` |
| Dark cave stays dark, no change when a neighbour changes | **fail**: the stone around the soul wall carrier is drawn lit in the dark tunnel (client light from the soul torch's emission 10), before and after a stone block was placed next to the standing spike | `01-...`, `03-gui2-soul-carrier-spike-lit-walls-after-neighbour.png` |

Verdict: rejected. The particles are client code keyed to the vanilla block, so no server setting removes them; the copper
torch (1.21.9+, light 14, copper flame, crafted from a vanilla recipe) and `minecraft:torch` (the lit torch's carrier) fail
the same way. The 26.3 in-game repeat was not run because the failing behaviour is version independent (same
`animateTick` bytecode in 26.3). The light part of the idea is noted for PAR-HEAT-28 (below).

## 2. Widened candidate dump and the new carriers

Every state of the small no-collision wall and floor blocks near the torch outline was dumped from the Paper 1.21.4,
1.21.11, 26.1.2 and 26.3 jars and added to `vanilla-shapes-*.json` (soul and copper torches, lever, tripwire hook, cocoa,
vine, glow lichen, sculk vein, hanging roots, dead bush, the small flowers, the ten coral fans and wall fans, wall signs and
wall hanging signs, wall banners, big dripleaf and its stem, torchflower crop) with a reasoned policy each
(`carrier-policy.json`, TFC recipes and worldgen checked in /opt/tfc, our worldgen by `CarrierPolicyWorldgenTest`). The
selector (weights unchanged) then picks:

| demand | old carrier | new carrier | outline | IoU with TFC | score |
|---|---|---|---|---|---|
| `tfp:dead_torch` | `red_mushroom` | `torchflower_crop[age=1]` | 6 x 10 x 6 | 0.44 | 0.67 (was 0.53) |
| `tfp:dead_wall_torch[facing]` | `oak_button[face=wall,powered=true]`, hitbox-only + item display | `big_dripleaf[facing,tilt=full,waterlogged=false]`, TFC's model as a block model | 6 x 13 x 6 from the block floor, one pixel off the wall | 0.39 (was 0.08) | 0.55 (was 0.39) |

Closer outlines are all ruled out: torch-shaped states (light and client flame particles, or crafted: unlit redstone
torches), the wall lever (6 x 8 x 6, score 0.73: TFC keeps the vanilla recipe and makes vanilla cobblestone from lava and
water), the tripwire hook (TFC recipe), cocoa (collision). Runner-up: `big_dripleaf_stem` (6 x 16 x 6, 0.54), shot in the
first run (`09-gui2-runner-up-dripleaf-stem-wall.png`). Adding the blocks moves no other carrier (`only_class` torch /
wall_torch on every new usable block; `addingTheTorchesMovesNoOtherCarrier` and the pinned carrier tests pass).

## 3. In-game steps (Paper 1.21.11)

Stone platform at y 100 (1105..1135, -3165..-3130), stone pillars P1 (1120, -3145) and P2 (1126, -3145), P3 (1110, -3145), a
closed one-wide tunnel along x at z -3160.

| step / criterion | result | evidence |
|---|---|---|
| Survival, thirst 100, hunger 20; `tfp:torch` on the floor and on each face of P1 after the chunks loaded | done; the lit torches show vanilla's torch with flame | (`a-lit` WebP) |
| Creative, `tfp time add days 4`, tp 300 blocks away, 10 s, tp back, survival | all five dead, facing kept, on the new carriers (second run: P3 wall torch and a floor torch, same steps) | `06-gui3-...burnt-north`, `07`/`08` (burnt floor) |
| Reference `minecraft:wall_torch` / `minecraft:torch` next to them; selection outline from about 1.5 blocks in front and from left and right | pass: the dead wall torch outline is as wide as the torch's (6 vs 5 px), ends where the torch ends (13 px) and reaches down to the block floor (TFC: from 3 px); the dead floor torch outline is 6 x 10 x 6 around the 2 x 10 model (TFC 4 x 10 x 4). The model sits where TFC's does. Front, left and right at GUI 2 and 3 | `04-gui2-...`, `05-gui3-...`, `06-gui3-...`, `07-gui2-...`, `08-gui3-...`, `10-gui2-floor-ref-mushroom-torchflower.png` (old mushroom outline vs the new one) |
| Dead torches give no light | pass: `0 block` in F3 standing in the dead wall torch block and the dead floor torch block right after the chunk-load burnout, no relog (the PAR-HEAT-21 run read 14 here, PAR-HEAT-28) | `11-gui3-chunk-load-burnout-f3-block0.png` |
| One-wide tunnel lined with dead wall torches at head and foot height (both walls, x 1111..1116) | pass: holding W walked from x 1110.5 to 1123.8 on both carriers (stem in run 1, big dripleaf in run 2) | `13-gui3-tunnel-passable.png` |
| Held `tfp:torch` on each dead torch relights it, the held torch is not placed | pass: both lit (flame), held count stays 8 | `12-gui3-relit-pair.png` |
| No flicker when a neighbour changes | pass: a stone placed beside the torchflower-crop carrier in daylight and under a big-dripleaf carrier: frames at +0, +0.1, +0.4 and +1 s show the dead torch unchanged, no break particles (a client-predicted change to air is skipped on the client, `Block.updateOrDestroy`) | `14-gui2-torchflower-neighbour-no-flicker.png`, `15-gui3-dripleaf-neighbour-below-no-flicker.png` |
| Break | pass: one click breaks the dead wall torch, a stick dropped (TFC's 50 % table) | `16-gui3-broken-dead-torch-stick.png` |
| Relog | pass: the client was restarted between runs; the dead torches kept their look and outline | `05`, `06` |

## 4. Paper 26.3

The same jar loads `configuration/light/torch.yml` on Paper 26.3 build 253 with no CraftEngine issue (second start after
the priming start), and the carriers exist with the same shapes in 26.1.2 and 26.3 (dump, `TorchCarrierTest`). The 26.3
client could not be started here: `Failed to create window for OpenGL context: Couldn't find matching GLX visual` and
`Vulkan ... doesn't implement the VK_KHR_surface extension` on two fresh Xvfb displays (one with `+extension GLX`) (the same failure PAR-INFRA-13
recorded). The 26.3 visual check stays with PAR-INFRA-23's pending 26.x carrier checks.

## Verdict

Pass on 1.21.11 for the done criteria: the dead torches use the closest free outlines of the widened dump (test), no
collision, no light, TFC's dead look next to a vanilla torch reference, a lined one-wide tunnel is passable. The owner's
torch-shaped light-0 carrier was spiked first and rejected (client flame and smoke particles, client-lit surroundings).
