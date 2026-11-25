# PAR-PROC-50 verdict

Date 2026-11-25 (second run, after PAR-PROC-54's fix), HEAD 0b20422, Paper 1.21.11 + vanilla client 1.21.11, headless, GUI scale 2.
The first run (HEAD 14fd4e2) failed at the first step; the screen did not open (PAR-PROC-54).

| Step | Result | Evidence |
|---|---|---|
| Place `tfp:wood/scribing_table/oak`, right click with an empty hand: an anvil screen titled "Rename Items" (TFC's en_us name of the screen) opens | PASS | screen-opens.png |
| Iron ingot (wrought iron) in slot 1, a name typed, no dye in slot 2: the result shows, clicking the result does nothing (ingots stay, no dye used) | PASS | no-dye-result-click-does-nothing.png |
| Black dye in slot 2, name "Gold": result shown with no level cost; shift-click takes it: both ingots carry `custom_name` "Gold" and the one dye is gone | PASS | ink-in-slot-2-result.png, result-taken-ingots-renamed.png |
| Blank text: the result is the unnamed item (the custom name is removed; it needs ink again) | PASS | blank-name-result-unnamed.png |
| Closing the screen returns the items to the inventory | PASS (Escape with the ingots in slot 1: both back in the hotbar) | |
| Creative: no dye needed | PASS (creative, no dye in the inventory: result taken, ingots named "Creative") | creative-no-dye-taken.png |

TFC's screen art (`scribing_table.png`) is not drawn: the screen is the vanilla anvil background. TFC's screen is a custom one with a text field and ink-level icons, so the art needs the anvil title-glyph technique and its own layout: follow-up PAR-PROC-56.
Verdict: renaming works as TFC in game.
