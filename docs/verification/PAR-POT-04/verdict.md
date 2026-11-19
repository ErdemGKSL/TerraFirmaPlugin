# PAR-POT-04 in-game verdict

Date 2026-11-19, base HEAD b205837 plus the uncommitted PAR-POT-04 change, Paper 1.21.11 + 1.21.11 client, CraftEngine pack applied (headless recipe, `docs/testing/headless.md`). The run was done by the worker itself (no subagent tool in this session); screenshots judged by the worker.

Steps: give `tfp:ceramic/vessel`; `/tfp debug vessel Bot putn 5 tfp:ore/normal_native_copper` (debug insert); `/tfp debug vessel Bot open`; `/tfp debug heat Bot 1100` (writes the heat record; `debug warm` cannot heat a fired vessel, see below); wait for the sweep (2 s); hover the item.

| Criterion | Result |
|---|---|
| Right-click/open shows the four-slot vessel screen with the contents | PASS: `screen.png` (plain chest, 2 x 2 slots in the middle, the 5 ores in slot 3, title "Small Vessel") |
| Contents melt lazily when hot and persist on the item | PASS: after the heat record at 1100 C the stack held `tfp:fluid` copper 125 mB (5 x 25) and empty slots, no reopen needed |
| Tooltip shows the alloy | PASS: `tooltip.png`: dark green "Contents:" then "Copper 125 mB" |
| Heat tooltip line on the vessel | NOT SHOWN: fired vessels have no `item_heat` data (TFC gives them the pottery capacity as a default component), so the item heater and heat tooltip treat them as unheatable; ledger divergence + follow-up PAR-POT-11 |
| Textured screen art | Deferred to PAR-POT-10 |
