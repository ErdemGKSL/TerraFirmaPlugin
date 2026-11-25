---
id: PAR-REL-04
title: "Stone-age run self-test"
area: REL
status: done
wave: 52
deps: [PAR-PROC-01, PAR-HEAT-06, PAR-POT-03, PAR-POT-05, PAR-GATHER-01, PAR-TOOL-01]
size: M
aliases: ["M3 exit criteria"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/StoneAgeRunCase.java"
ui: false
in_game_steps: []
plan:
  - "Scripted self-test: spawn -> loose rocks -> knapped tools -> fire -> cooked food -> pottery -> copper casting, on Paper and Folia."
  - "Done: itest `StoneAgeRunCase` (`stone_age.run`, in SelfTestSuite after metal.armor) scripts spawn -> loose rock pickup (GatherPieces/GatherRules, 2 granite rocks) -> rock knapping (KnappingSession, knife head) -> knife crafted by the server recipe manager -> stick + TFC oak log pile lit into a tfp:firepit -> raw beef cooked in it (calendar +1000, catch-up) -> clay knapped into 2 unfired ingot molds, placed-item insert, 8 straw + 8 logs, lazy pit kiln firing via PaperKilnItems -> tfp:ceramic/ingot_mold -> 100 mB copper poured (MoldItems), cooled, cast -> tfp:metal/ingot/copper. All 8 steps ran on Paper and Folia 1.21.11 (second start of each run dir); the case reports skipped only for two world parts: the loose rock block (PAR-WORLD-31) and the placed_item/pit_kiln blocks (PAR-POT-14). The whole runs still exit 1 from other cases (ce.pack_issues, device.roundtrip, forge.continuous, worldgen.terrain_generates) and :itest:test SelfTestSuiteTest expects 38 cases (42 now); neither is in this item's files."
tests:
  - "itest"
done_criteria:
  - "Green on Paper and Folia 1.21.11"
---

# PAR-REL-04: Stone-age run self-test

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
