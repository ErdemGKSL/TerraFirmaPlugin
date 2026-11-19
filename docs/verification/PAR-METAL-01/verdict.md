# PAR-METAL-01 in-game verdict

Date 2026-11-19, base HEAD a96d0e3 plus the uncommitted PAR-METAL-01 change, Paper 1.21.11 + 1.21.11 client, GUI scale 2, CraftEngine pack applied (headless recipe, `docs/testing/headless.md`; ports 30575/8223, display :96). Run by the worker itself (no subagent tool); screenshots judged by the worker.

Steps: give `tfp:ceramic/vessel` to Bot; `/tfp debug alloy Bot put copper 900` and `put tin 100` (the vessel melt still writes one fluid, follow-up PAR-METAL-22, so the mix is written by the debug command into `tfp:alloy`); open the inventory, hover the vessel.

| Criterion | Result |
|---|---|
| A vessel with 90% copper 10% tin shows 'Bronze' | PASS: `bronze.png`: green "Contents:", "Bronze 1000 mB", "Copper 900 mB (90.0%)", "Tin 100 mB (10.0%)" |
| A mix no recipe makes shows 'Unknown' | PASS: `unknown.png`: copper 500 + wrought iron 500 gives "Unknown 1000 mB" and 50.0% lines |
| Single metal shows one line | Covered by the paper test (`AlloyTooltipTest`) |

Finding: the old `tfp:fluid` line is not shown for these items because they carry no `tfp:fluid`; once a vessel carries both entries the two contributors would both print (handled by PAR-METAL-22).
