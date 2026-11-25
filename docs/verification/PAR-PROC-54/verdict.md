# PAR-PROC-54 verdict

Date 2026-11-25, Paper 1.21.11 + vanilla client 1.21.11, headless (spikes/client), HEAD 14fd4e2 plus the fix.

Cause: the screen was built with `checkReachable(true)`. Vanilla's `AnvilMenu.stillValid` requires the block at the menu location to be
an anvil; the scribing table is a CraftEngine block, so the menu closed on its first tick (no log line). A/B on the same world and aim
(crosshair on the tabletop): old jar, no screen (before-fix-no-screen.png); fixed jar, screen opens (screen-opens.png).

Fix: `checkReachable(false)`; the listener checks table-still-there and 8-block reach itself before preparing or taking a result.
Also the title fell back to the raw key `tfc.screen.scribing_table`; it now carries the fallback text "Rename Items" (TFC's en_us).

| Step | Result |
|---|---|
| Right-click tabletop, empty hand, opens anvil screen | PASS |
| Iron ingot + black dye, name typed: result without XP cost (rename-result.png) | PASS |
| Shift-click take: ingot renamed (custom_name), dye 2 -> 1 | PASS |
| Without dye / creative / blank-clears | not run in game (rules covered by ScribingTableTest); stay with PAR-PROC-50 |
