# PAR-HEAT-01 verification: item heat glow

- Date: 2026-11-08
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at b3b05cc plus the PAR-HEAT-01 change (the commit that adds this file)
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md`. Log: `Heat glow: 550 heatable items glow from heat level
  index 4`; the CraftEngine pack built and validated with no issue about `heat_glow` files.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, pack applied after 3 s.
- Run by the worker session itself: it had no subagent tool to delegate the run.

## Steps

1. `gamemode survival Bot`, `time set noon`, `weather clear`
2. `ce item give Bot tfp:metal/ingot/copper` (twice), `tfp debug heat Bot 900` (level BRIGHT_RED), then one more
   `ce item give Bot tfp:metal/ingot/copper` (a cold ingot in the next slot for comparison)
3. Screenshot of the hand and hotbar; `E`, screenshot of the inventory
4. Creative return: `gamemode creative Bot`, in the creative screen pick up the hot stack and put it in another hotbar
   slot, then `data get entity Bot Inventory[{Slot:4b}]...`

## Evidence

- `01-hand-900C.png`: the held hot ingot in first person, red.
- `02-hotbar-hot-vs-cold-2x.png`: hotbar, hot stack (slot 1) bright red next to the cold ingot (slot 2).
- `03-inventory-hot-vs-cold-8x.png`: inventory slots at 8x: hot ingot red with the texture's shading kept, cold ingot
  plain copper.
- `04-inventory-window.png`: the inventory window.

## Criteria

| Criterion | Result |
|---|---|
| Copper ingot at 900 C glows bright red in hand | Pass (01, 02) |
| Copper ingot at 900 C glows bright red in an inventory slot | Pass (03, 04) |
| (extra) Creative return leaves the server stack clean | Pass: after the creative move the stack has `item_model` `tfp:metal/ingot/copper`, no `custom_model_data`, no `tfp:heat_glow` marker, count 2 |
