# PAR-INFRA-05 in-game check: verdict

- Date: 2026-10-22
- Code: base `38a96c8` plus the PAR-INFRA-05 repair commit that adds this file (the jar was built from that tree)
- Server: Paper 1.21.11 build 132, Java 25, `-Xmx2G`; CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents 2.14.0; TFC world (`TFP_WORLD=1`), pack self-hosted on 127.0.0.1
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), GUI scale 2 (captures 01-08), 3 (09-10); bot `Bot`
- Judged by: the main session (Opus), from the PNGs below

## Steps and results

| # | step | capture | result |
|---|---|---|---|
| 1 | `ce item give Bot tfp:metal/ingot/copper`, `tfp debug heat Bot 900` right before the hover | `01-ingot-900-bright-red-weld.png` | pass: `Bright Red٭٭٭٭ - Can Weld`, red (#FF5555), not italic; the suffix is white as TFC's `§f - Can Weld` |
| 2 | keep hovering without reopening (the bot had died and sat on the death screen for about a minute just before) | `02-ingot-cooling-after-death-same-hover.png` | pass: `Bright Red٭٭ - Can Work` (844 C read back) |
| 3 | keep hovering until the debug readout says 687 C / DARK_RED | `03-ingot-687c-dark-red-work-same-hover.png` | pass: `Dark Red٭٭٭ - Can Work` in dark red, same open inventory |
| 4 | `ce item give Bot tfp:food/mutton`, `tfp debug heat Bot 200`, hover | `04-mutton-expiry-and-heat.png` | pass: `Expires on: 7:57 July 1, 1000 (in 7 day(s))` in dark green (#00AA00), then `Hot٭٭٭` |
| 5 | `gamemode creative Bot`, hover, pick the mutton up, put it back, hover | `05-creative-before-after-pickup.png` | pass: exactly the same two lines before and after, no duplicate; server stack afterwards has no `minecraft:lore`, no `!minecraft:lore` and no `tfp:lore` marker (`data get`) |
| 6 | heated ingot carried on the cursor | `06-ingot-on-cursor.png` | pass (no tooltip while carried, as vanilla) |
| 7 | chest holding the heated ingot, hover | `07-chest-window-ingot.png` | pass: `Dark Red٭٭٭ - Can Work` in the chest's top inventory |
| 8 | crafting table and player inventory opened | `08-crafting-table-and-inventory.png` | pass |
| 9 | GUI scale 3: hover and HUD hotbar (retake of r-07) | `09-gui3-hover.png`, `10-gui3-hotbar.png` | pass |
| 10 | server log of the final run (`Done` 18:59:49 to stop) | - | pass: no `Tooltip refresh failed`, no `IndexOutOfBoundsException` |

## Done criteria

1. Two contributors render together in-game without duplicating on creative pick-up: **pass** (04, 05).
2. UI defect 2 of plans/gaps.md (magenta italic heat line) closed: **pass** (01, 03, 07, 09: one red/dark-red line, not italic,
   no magenta line).

## Defects found by this run and fixed in the same commit

- **Refresher lost after a death.** The first capture of step 2 kept the 900 C text. Debug logging showed the period task
  running once with the player dead and never again: Paper's entity scheduler ends a repeating task as retired when it fires
  while its entity is out of the world, which a dead player is from the end of the death animation until the respawn.
  `TooltipRefresher` now re-arms the watch on `PlayerRespawnEvent` and `PlayerChangedWorldEvent` (test
  `TooltipRefresherRewatchTest`); capture 02 is the first hover after a death.
- **Creative strip left `!minecraft:lore`.** `unsetComponent(LORE)` on a 1.21.5+ item type (default lore: empty) recorded a
  removal; the stack then differed from a fresh copy. The strip now drops the lore patch (test `CreativeLoreResetTest`).

## Notes

- `ce item give` does not stamp food yet (the `tfp:food_stamp` processor on generated food items is PAR-SURV-01, open), so
  step 4 stamped the mutton with `item modify entity Bot hotbar.0` (`set_custom_data` writing `tfp:food` = `{tfp:v:1,
  tfp:created:<tick>, tfp:traits:[]}`) before the heat command.
- The test server ran at about 6 TPS (other agents' builds on the machine), so cooling followed server ticks, not wall time
  (900 to 687 C took about 2 minutes instead of 35 s).
