---
id: PAR-INFRA-54
title: "Datagen summary counts CraftEngine block states (ce_states) and the hitbox report lists hide mappings sorted"
area: INFRA
status: done
wave: 14
deps: [PAR-INFRA-13]
size: S
aliases: ["split from PAR-PROC-37 (review of ddbeb5b, PROTOCOL 3): the datagen fixes its in-game run needed outside its files_owned"]
tfc_refs: []
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/Report.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxReport.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/ReportTest.java"
ui: false
in_game_steps: []
plan:
  - "Owns the two datagen fixes PAR-PROC-37's in-game run found (they landed with ddbeb5b before this item existed; this item owns them from now on, PAR-PROC-37 depends on it). (1) Report.countCeStates: the server states every CraftEngine block entry under craftengine/<pack>/configuration/ defines (per block the product of its property sizes), written as counts.ce_states in reports/datagen-summary.json by the one Datagen.generate line `report.ceStates(Report.countCeStates(out.files()))` (a shared-hub line this item owns). The installer sizes CraftEngine's block.serverside-blocks from it: with the charcoal pit blocks our 1611 states plus CraftEngine's default pack (446) no longer fit in the default 2000, and the block count still asked for 2000. (2) HitboxReport prints the hide mappings sorted (Map.copyOf order made verifyContent flip between runs once a third mapping existed)."
  - "Done (2026-11-14): ReportTest (8 cases: a block without properties, suffixed blocks# sections and sections that do not count, listed values, axis with and without values, quoted and unquoted ranges and booleans, variants and appearances under states adding no properties, the file overload reading only configuration YAML, ce_states in the summary only when set). The real-tree check stays GeneratedCarrierTest.theSummaryCountsEveryCraftEngineState (the counter equals a YAML parse of every generated entry). No code change was needed: the cases pass on ddbeb5b's counter."
tests:
  - "datagen: ReportTest"
done_criteria:
  - "counts.ce_states equals the states of every generated CraftEngine block entry (GeneratedCarrierTest) and ReportTest pins the YAML shapes the counter reads"
---

# PAR-INFRA-54: Datagen summary counts CraftEngine block states (ce_states) and the hitbox report lists hide mappings sorted

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
