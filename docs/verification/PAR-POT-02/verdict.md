# PAR-POT-02 verdict (2026-11-19)

HEAD 9f76c09; Paper 1.21.11-132, vanilla 1.21.11 client (GUI scale 2), CraftEngine 26.9.2.

Steps run: `ce item give Bot` for unfired_vessel, ingot_mold, vessel, jug, red_glazed_vessel, unfired_large_vessel, unfired_mold_table. All 7 succeeded; inventory opened (`inventory-gui2.png`), icons cropped 8x (`icons-sheet.png`).

- Every ceramic family gives an item: PASS.
- Icons show TFC textures (blue-grey unfired vessel, mold tray, fired vessel, jug with handle, red glazed vessel, unfired large vessel and mold table as small 3D models): PASS.
- Server log: no ERROR/WARN naming tfp, ceramic, model or texture; no "Found N issue(s)" on the served start: PASS.
