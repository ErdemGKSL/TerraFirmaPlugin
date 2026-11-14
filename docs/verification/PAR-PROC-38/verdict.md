# PAR-PROC-38 verification: barrel rack, sideways barrel and tap

- Date: 2026-11-14. Base commit 4c5fc13 plus the PAR-PROC-38 change (content regenerated with
  `./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc`, `:dist:shadowJar`).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud and PacketEvents as installed by
  `spikes/client/server.sh` (default world), priming start then restart. No `Found N issue(s)` line after the restart, no
  SEVERE line or exception from the plugin in any run.
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, GUI scale default, pack applied after
  3 s.
- Run by the worker session itself (no cheap-model agent could be started from this session); the worker judged every
  screenshot.

## Steps

1. A smooth quartz floor at y 99 (x 100..108, z 100..108) with a wall behind it. Per lane X (104, 102, 106): glass at
   (X, 99, 103), Bot looking at it, `/tfp debug place tfp:wood/barrel/oak` (the target, upright, flush with the floor);
   glass at (X, 100, 104), `/tfp debug place tfp:barrel_rack` (the rack, one block south of the tap's block). Screenshot.
2. `ce item give Bot tfp:wood/barrel/oak 1`, Bot at (X.5, 100, 107.5) looking north at the rack's top, a real right
   click (xdotool). Screenshot.
3. Bot looking at the racked barrel: `/tfp debug barrel fill 1000` (water). Wait about 15 s; Bot looking at the target:
   `/tfp debug barrel`. Screenshots of both readouts.
4. A side view from the west of lane 102 (after a restart with the corrected carrier, see Findings).

## Results

| Check | Evidence | Result |
|---|---|---|
| The rack shows TFC's model (four legs, top) | `rack.png` | **Pass** |
| A barrel item used on the rack lays the barrel on its side, racked, no screen opens, the item is used up (survival) or kept (creative, lane 106) | `barrel-laid-on-rack.png` (lane 102 left, lane 104 right) | **Pass** |
| The barrel faces the player's direction: north, its tap at the north end over the target | `sideways-tap-over-barrel.png`, readout `facing: north, rack true` in `source-filled-tap-open.png` | **Pass** |
| The tap opens when filled: `tap clock 123492 (open)`, 1000 mB, `rain: dry above` | `source-filled-tap-open.png` | **Pass** |
| The target below the tap gains exactly 1 mB per tick: 340 mB at clock 123832, 340 ticks after the tap opened | `target-after-340-ticks.png` | **Pass** |
| The pour is lazy across a restart: lane 102's target holds the whole 1000 mB after the server was stopped and started | `target-after-restart.png` | **Pass** |

| Criterion | Result |
|---|---|
| A barrel placed on a rack lies sideways (screenshot) | **Pass** |
| A racked barrel pours from its tap into the fluid handler below | **Pass** (340 mB in 340 ticks; the source's 1000 mB moved in full by the next access) |

## Findings fixed during the run

- **The barrel screen opened after laying a barrel on the rack.** The listener cancels the main-hand event; Paper then
  fires the off-hand event, which reached the new barrel through CraftEngine. Fix: the other interactions of that player
  in the same tick are cancelled (`BarrelRackListener.onInteract`, tested). Re-run: no screen.
- **The ground showed through beside a barrel on its side.** TFC's side rows took a `farmland` carrier (hitbox-only); its
  full bottom face makes the client cull the top face of the block below, so the gaps beside TFC's 12 wide side model and
  between the rack's legs showed the sky through the floor. Fix: the side demands borrow the sealed upright row and share
  its transparent `cactus[age=10]` carrier (ledger divergence; `GeneratedCarrierTest` pins it). Re-run
  (`sideways-tap-over-barrel.png`): no gap.

## Not checked here

The rack and unrack clicks, a broken racked barrel leaving its rack, the support rule and the explosion drop are covered
by `BarrelRackMockBukkitTest`; the barrel GUI, buckets, surface and rain are PAR-PROC-35's checks.
