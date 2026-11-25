---
id: PAR-PROC-46
title: "Panning in-game verification: deposit pickup, water hint, 6 s pan, small ores; deposit loot items"
area: PROC
status: done
wave: 33
deps: [PAR-PROC-12, PAR-WORLD-31]
size: S
aliases: ["moved from PAR-PROC-12 (2026-11-25, PROTOCOL 6.5): the deposit CE blocks and the small ore items come with PAR-WORLD-31"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/EmptyPanItem.java"
  - "src/main/java/net/dries007/tfc/common/items/PanItem.java"
files_owned:
  - "docs/verification/PAR-PROC-46/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:pan/empty; place tfp:deposit/native_copper/granite in a river; right-click the deposit with the pan: the block disappears and a filled pan (showing the deposit model) is in the hand slot's inventory"
  - "Hold the filled pan on dry land and right-click: action bar 'You need to stand in water to be able to pan.' and no use animation; stand in water and hold right-click 6 s: the brush sound plays every 0.8 s, the pan becomes empty and small native copper / loose granite / topaz drop into the inventory (tfp:ore/small_native_copper must exist: PAR-WORLD-31)"
  - "Creative: the empty pan is not used up by taking a deposit; off-hand use of the filled pan does nothing"
plan:
  - "Verification of PAR-PROC-12 once the deposit blocks and the small ores exist. Check the loot lines of data/tfp/loot_table/deposit/* log no 'unknown item' warning."
  - "Done: in-game verification 2026-11-25 on Paper 1.21.11 (pickup, dry-land hint, 6 s pan, small native copper; sound and off-hand not checkable headless). Evidence and verdict in docs/verification/PAR-PROC-46/."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-46/"
done_criteria:
  - "Panning a native copper deposit yields small ores; the water hint, 6 s duration and the empty pan returned match TFC"
---

# PAR-PROC-46: Panning in-game verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
