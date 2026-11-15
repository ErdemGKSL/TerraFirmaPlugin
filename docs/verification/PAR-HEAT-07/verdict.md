# PAR-HEAT-07 / PAR-HEAT-20 verification: firepit screen art, thermometer, opened from the block

- Date: 2026-11-15
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at 58ca1fa plus the PAR-HEAT-20 change (datagen
  `firepitgui/FirepitScreenArt`, `FirepitDevice` screen step, `FirepitScreenLink`, `FirepitModule` wiring).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md`, CraftEngine pack self-hosted on 127.0.0.1. No warning or
  error from our plugin in the log.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then a rejoin with `MC_OPTIONS="guiScale:3"`; the pack
  applied 3 s after each join. No "Chat messages can't be verified" toast showed in any capture (offline-mode server).
- Run by the worker session itself: it had no subagent tool to delegate the run.

## Steps

1. `op Bot`; as Bot `/tfp debug firepitgui 0`, `400`, `1200` at GUI scale 2 (01 to 03), `0` and `1200` at scale 3 (06, 07).
2. A stone floor around Bot; `/tfp debug place tfp:firepit[lit=true]` (placed at world -800 73 -206).
3. Right-click the block with an empty hand (04: opened from the block).
4. Put a stick bundle (`tfp:stick_bundle`, a TFC firepit fuel; the TFC oak log item is not a CraftEngine item yet, wood
   families are skipped by datagen) into the top fuel slot: it drops to the bottom slot (TFC `cascadeFuelSlots`); raw beef
   (`tfp:food/beef`) into the input (05). `/tfp debug device` on the block: slot 0 = the bundle, slot 4 = the beef.
5. Rejoin at scale 3, right-click the block again: the stored contents come back (08, mouse over the cold thermometer);
   a second bundle into the top slot cascades to fuel slot 1 (09).

## Evidence

- `01-debug-0C-gui2-before-width-marker-fix.png`: the first capture: art, thermometer column, arrows right, but the
  "Firepit" label drawn 15 px left of the window. Cause: the client measures a bitmap glyph's advance from its rightmost
  non-transparent column, and frame 0 of the thermometer strip (cold) was fully transparent, so the title pen moved 1 px
  instead of 16. Fixed in datagen (`FirepitScreenArt.keepAdvance`: an alpha-1 marker pixel in the empty frame's corner,
  as `PlayerTabArt`); 02 (400 C, frame 12) already had the label in place, which confirmed the cause.
- `02-debug-400C-gui2.png`, `03-debug-1200C-tooltip-gui2.png`: the indicator moves up (frame 12, then 38); hovering the
  thermometer at 1200 C shows TFC's heat tooltip ("Yellow**").
- `04-block-open-gui2.png`: opened from the block, cold: label "Firepit" at the window's title position, one title only,
  no literal `<image>` text, TFC's frames under the seven slots, plain panel over the 25 slots TFC does not have.
- `05-block-fuel-beef-gui2.png`: the bundle cascaded to the bottom fuel slot, the beef in the input.
- `06-debug-0C-gui3.png`, `07-debug-1200C-gui3.png`: scale 3, cold (label in place after the fix) and 1200 C.
- `08-block-reopened-cold-hover-gui3.png`: reopened from the block at scale 3 with the stored contents; the cold
  thermometer column shows only the vanilla slot hover box (ledger divergence of PAR-HEAT-07), no tooltip.
- `09-block-second-fuel-cascade-gui3.png`: two bundles in fuel slots 0 and 1, the top slot free again.
- `10-art-tfp-gui-firepit-4x.png`: the generated `tfp:gui/firepit` at 4x.

## Criteria

| Criterion | Result |
|---|---|
| Title resolved by CraftEngine (no literal `<image>` text, no duplicate title) | Pass (02 to 09; 01 shows the advance bug fixed in this item) |
| Unused chest slots covered by the art | Pass (04 to 09: plain panel, no slot frames; clicks there are refused by `FirepitScreens`) |
| Thermometer indicator visible and moving with the temperature | Pass (02, 03, 07: frames 12 and 38; 04, 06, 08: none when cold, as TFC) |
| No toast in the captures | Pass |
| Aligned screenshot of the firepit GUI at GUI scale 2 and 3, opened from the block | Pass (04, 05, 08, 09) |

Not covered here: lighting the placed firepit in game. A sneaking firestarter / flint and pyrite use did not light it
(the firestarter set vanilla fire instead); fire starting on a firepit is PAR-HEAT-05's path and is covered by the
self-test `firepit.cook`. The block's thermometer therefore stayed cold in 04 to 09; the moving indicator is shown on the
debug screen, which uses the same layout, art and `FirepitScreens` code.
