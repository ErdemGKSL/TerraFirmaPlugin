# PAR-PROC-51 verdict

Date 2026-11-25, HEAD 0b20422, Paper 1.21.11 + vanilla client 1.21.11, headless, GUI scale 2 only (scale 3 not run).
Items: `tfp:wool_cloth` x3, `tfp:burlap_cloth` x4, string x3, `tfp:bone_needle`; the recipe `tfc:sewing/globe_banner_pattern` (32 squares, 16 stitches). Clicks driven with xdotool on the slot centres.

| Step | Result | Evidence |
|---|---|---|
| Place `tfp:wood/sewing_table/oak`, right click: a six-row screen titled "Sewing Table" opens, the four buttons on slots 50 to 53, no literal `<image>` text in the title | PASS | screen-opens-gui2.png |
| String in slot 45, needle in 46, wool cloth 47, burlap cloth 48 (pick up and place by click) | PASS | inputs-placed.png |
| Burlap and wool buttons, then the 32 squares of the globe pattern | PASS: the 8 x 4 grid shows the pattern | squares-globe-pattern.png |
| Needle button, then the 16 stitches | PASS: the 9 x 5 view shows the 16 stitches and the result (globe banner pattern) appears in slot 49 | stitches-and-result.png |
| Used cloth, yarn and needle cannot be taken before the result is taken | PASS (a click on the cloth slot did nothing) | input-slot-click-blocked.png |
| Taking the result: globe banner pattern in the hotbar; wool cloth 3 to 1, burlap cloth 4 to 1, string 3 to 2, the needle shows wear | PASS (consumption as computed by the ported rules; not re-derived against TFC's by hand) | result-taken.png |
| Closing the screen returns the items | PASS (Escape: string, needle, wool cloth and burlap cloth back in the inventory) | |
| TFC's sewing.png art behind the grid, at GUI scale 2 and 3 | NOT DONE: still the vanilla chest background with item icons. Follow-up PAR-PROC-57 | |

Verdict: sewing a banner pattern works in game; the screen art moved to PAR-PROC-57.
