# PAR-PROC-08 / PAR-PROC-32 verification: barrel blocks and brining in game

- Date: 2026-11-13
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at 70683a3 plus the PAR-PROC-32 change (the commit that adds this
  file: `configuration/barrel/barrel.yml` from `tools/datagen` `barrel/BarrelBlock`).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md`. Log: the module list contains `barrel`; no CraftEngine
  config issue and no exception from our code.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale auto (2 at 1280x720); the pack applied 3 s after the join.
- Run by the worker session itself: it had no subagent tool to delegate the run.

## Steps

1. `spikes/client/testroom.sh` at (880, 73, -1648); a stone block on the floor at (880, 74, -1645); Bot looks at it.
2. `/tfp debug place tfp:wood/barrel/oak` (typed by Bot): `Placed tfp:wood/barrel/oak at world 880 74 -1645`.
3. `ce item give Bot tfp:bucket/brine`, `ce item give Bot tfp:food/beef`; right click the barrel with the brine bucket. Bot
   was still in creative for the first clicks, so the bucket was kept (creative rule) and the tank reached 3000 mB of
   `tfp:brine` (`/tfp debug device`); no brine was placed in the world: `BucketListener` (PAR-FLUID-02) leaves the click
   to the device (brine has no world block, so `useFluidBucket` passes). A vanilla water bucket on the brine barrel did
   not mix and went into the item slot (PAR-PROC-08's no-GUI rule), and came back out with an empty hand.
4. `gamemode survival Bot`; right click with the beef (into the item slot); sneak + empty hand: sealed
   (`sealed=true`, `recipeTick=6420`: the brining recipe matched).
5. `tfp time add hours 5` (5000 ticks; TFC's brining is 3600); sneak + empty hand: unsealed; the tank went from 3000 to
   2875 mB (TFC's 125 mB per item); empty hand: the beef came out; `E`, hover it.

## Evidence

- `01-unsealed-oak-barrel.png`: the unsealed oak barrel (HUD hidden): TFC's open barrel model drawn by the item display
  over the transparent hitbox carrier, hollow top, oak planks and the hoops.
- `02-sealed-oak-barrel.png`: the same barrel sealed: TFC's lid model.
- `03-brined-beef-tooltip.png`: the beef's tooltip after the unseal: `Raw Beef`, size/weight line, expiry, nutrition and
  the `Brined` trait line.

## Criteria

| Criterion | Result |
|---|---|
| Brining meat in a sealed barrel adds the Brined trait, in game (screenshot of the tooltip) | Pass (03): the beef sealed with brine for 5 in-game hours shows `Brined`; the tank lost 125 mB |
| Sealed and unsealed barrel screenshots | Pass (01, 02): TFC's per-wood upright models, the selection outline is the carrier's (cactus, 14x16x14 against TFC's 12x16x12, see the ledger) |
| The bucket's fluid is not placed in the world when the bucket is used on the barrel | Pass (step 3) |
