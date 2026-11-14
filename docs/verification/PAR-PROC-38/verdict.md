# PAR-PROC-38 verification: barrel rack, sideways barrel and tap

Redone 2026-11-14 after the review rejection (the cactus top over the upright barrel, the cactus faces fighting the side
model, the iron trapdoor slab under the rack top). Base commit 173354c plus the review fix-up (the surface lookup fix in
`PaperBarrelEffects`, the new datagen guard); the fix-up changes no generated content.

- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents
  2.14.0, installed by `spikes/client/server.sh` in a fresh run folder (`TFP_WORLD=1`). Priming start, waited for
  `Generated resource pack`, stop, worlds deleted, restart; `ce reload all` once after the restart (the PAR-INFRA-55
  workaround: the priming pack misses the blocks CraftEngine could not allocate before `serverside-blocks` grew), then a
  second restart with the fix-up jar. No `Found N issue(s)` line after the restart, no SEVERE line or exception from the
  plugin.
- The pack the client got (`cache/hosted/default/resource_pack.zip`, 211 vanilla blockstate files) holds
  `cactus.json` `"age=10": {"model": "minecraft:block/empty"}` and `iron_trapdoor.json`
  `"facing=east,half=top,open=false,powered=false,waterlogged=false": {"model": "minecraft:block/empty"}`.
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, GUI scale default, pack applied after
  3 s. Run and judged by the worker session.

## Why the rejected run showed the vanilla carriers

The datagen output was right: both carriers carry `transparent: true` and CraftEngine writes the empty model for them
(checked above, and in the earlier worker run's pack). The pack of the rejected run had **no** `assets/minecraft/blockstates`
file at all (not one of the ~210 every built pack has, CraftEngine's own `copper_coil` included). Its priming start was
stopped over RCON at 21:43:32 while CraftEngine was still generating (`Generated resource pack in 3373ms` logged at
21:43:33, after `Stopping server`); the pack written during the shutdown lacks the block overrides, the installer
recorded the rebuild as done, and the restart served that pack. Every hitbox-only carrier then showed its vanilla model.
`docs/testing/headless.md` already says to wait for `Generated resource pack` before stopping; the installer side of a
pack built across a shutdown belongs with PAR-INFRA-55 (restart-time pack rebuild), not with this item.

## Steps

1. A smooth quartz floor at y 99 (x 96..112, z 96..112) with a wall at z 98. Per lane X (106, 108): glass at (X, 99, 103),
   Bot at (X.5, 100, 106.5) yaw 180 pitch 27, `/tfp debug place tfp:wood/barrel/oak` (the target, upright, flush with the
   floor); glass at (X, 100, 104), Bot at (X.5, 100, 107.5) pitch 20.5, `/tfp debug place tfp:barrel_rack`. Screenshot
   (HUD hidden).
2. Survival: `ce item give Bot tfp:wood/barrel/oak 1`, Bot at (X.5, 100, 107.5) pitch 13.4, a real right click
   (xdotool). Screenshots from the south and from the south-east.
3. Bot looking at lane 108's racked barrel: `/tfp debug barrel fill 1000` (water), `/tfp debug barrel`. After about 20 s,
   Bot at (109.75, 100, 103.5) looking down at the target at 62 degrees: `/tfp debug barrel`. After another 30 s the same
   readout, then the source's.

## Results

| Check | Evidence | Result |
|---|---|---|
| The rack shows TFC's model (four legs, 2 px top); no grey trapdoor slab under or beside the top | `01-rack-placed.png`, `01-rack-zoom.png` | **Pass** |
| The upright target between the legs shows its open top and interior, no cactus top | `01-rack-placed.png`, `01-rack-zoom.png` | **Pass** |
| A barrel item used on the rack lays the barrel on its side, racked, no screen opens, the item is used up (survival) | `02-barrel-laid-on-rack.png` | **Pass** |
| The sideways barrel is TFC's `barrel_side_rack` model: tan oak planks end, iron hoops, sheet sides, tap at the north end; no green band, no stripes | `02-barrel-on-rack-zoom.png`, `02-side-view.png` | **Pass** |
| The tap opens when filled: 960 mB at clock 66283, `tap clock 66283 (open)` (opened at 66243) | chat log | **Pass** |
| The target gains 1 mB per tick: 440 mB at clock 66683 (440 ticks after the tap opened) | `04-target-pouring-readout.png` | **Pass** |
| The target shows its water surface near the floor (one surface display, at 440/10000) | `04-target-surface-zoom.png` | **Pass** |
| The whole 1000 mB moved: the target holds 1000 mB, the source is empty, still one surface display (raised to 1000 mB) | `05-target-1000.png`, chat log, entity query | **Pass** |
| The pour is lazy across a restart (earlier run, unchanged code path) | `target-after-restart.png` | **Pass** |

The light outline on the rack top in `02-side-view.png` is the block selection outline (the crosshair is on the rack),
not a carrier model. The oak barrel reads as TFC's tan planks and dark sheet once the cactus overlay is gone (the dark
grey-brown, greenish look of the rejected run was the cactus texture over it).

| Criterion | Result |
|---|---|
| A barrel placed on a rack lies sideways (screenshot) | **Pass** (`02-*`: TFC's side model on an empty carrier) |
| A racked barrel pours from its tap into the fluid handler below | **Pass** (1 mB per tick; 1000 mB moved in full) |

## Findings fixed during this run

- **The fluid surface display was never found again**, so every surface update spawned another plane: after a fill and a
  few tap settles one barrel held four stacked planes (0.14, 0.18, 0.21 and 0.51 block high), and a drained barrel would
  have kept its old planes. The display stands at the block's bottom face with a zero-size box, and the server's entity
  lookup keeps only boxes that overlap strictly, so the block's own box missed it. Fix: `PaperBarrelEffects.surfaceSearch`
  (a box centred on the bottom face), `BarrelSurfaceLookupMockBukkitTest`. Re-run: one display per barrel, moved as the
  tank fills.

## Findings of the first run (kept)

- **The barrel screen opened after laying a barrel on the rack** (the off-hand event reached the new barrel through
  CraftEngine). Fix: the player's other interactions in the same tick are cancelled (`BarrelRackListener.onInteract`).
- **The ground showed through beside a barrel on its side** with TFC's own side rows on a `farmland` carrier (its full
  bottom face, drawn empty, culls the ground's top face). Fix: the side demands borrow the sealed upright row and its
  transparent `cactus[age=10]` carrier (ledger divergence).

## Not checked here

The rack and unrack clicks, a broken racked barrel leaving its rack, the support rule and the explosion drop are covered
by `BarrelRackMockBukkitTest`; the barrel GUI, buckets and rain are PAR-PROC-35's checks.

## Second in-game check: "no surface in the target" (answered)

A checker saw no water in the tap's target at 720 and 1000 mB from about 4.5 blocks south and 1.6 blocks above the rim
(about 20 degrees down). TFC's surface height there is y = 0.140625 + 0.734375 * fill: about 0.194 at 720 mB and 0.214 at
1000 mB, only 0.07 to 0.09 above the inner floor, and that sightline over the near rim meets the far inner wall at about
0.7 of the barrel's height, so the plane is hidden. `05-target-1000.png` (taken from almost straight above, same block,
1000 mB, filled by the tap) shows it. Look at a tap target from a pitch of 60 degrees or more within about 2 blocks, and
add a north-east view to see the tap and the target together.

Regression tests: `BarrelRackMockBukkitTest.theTapPoursOneMbPerTickIntoTheBarrelBelowLazily` now asserts that after the
pour the target holds exactly one surface display at `BarrelFluidVisuals.surface(n, 10000).y()` (120 and 500 mB) and the
barrel on its side none; `BarrelSurfaceLookupMockBukkitTest.successiveUpdatesKeepOnePlaneAtTfcsHeight` asserts two
updates keep one plane. Writing them found that `PaperBarrelEffects` built its locations with `block.getLocation().add`,
which moves MockBukkit's block (its location is shared); it now builds a new `Location` (Paper's `getLocation` returns a
copy, so the server was not affected).
