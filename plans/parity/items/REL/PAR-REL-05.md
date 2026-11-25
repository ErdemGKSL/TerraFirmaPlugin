---
id: PAR-REL-05
title: "Metal run self-test"
area: REL
status: done
wave: 53
deps: [PAR-METAL-15, PAR-METAL-06, PAR-METAL-13]
size: M
aliases: ["M4 exit criteria"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/MetalRunCase.java"
ui: false
in_game_steps: []
plan:
  - "Extend metal.run_rules to the full chain on real blocks: copper -> bronze -> bloomery wrought iron -> steel -> black steel -> red/blue steel."
  - "Done: itest `MetalRunCase` (`metal.run_rules`, already in SelfTestSuite) keeps the core golden checks and runs the chain on a real tfp:crucible, tfp:bloomery chimney and tfp:metal/anvil/red_steel: 4 native copper ores -> 100 mB copper cast in a fired ingot mold -> copper ingot; it + 7 cu + 1 sn -> 900 mB bronze -> bronze ingot; bloomery 4 cast iron + 8 charcoal -> raw bloom -> refined bloom -> wrought iron ingot (anvil); pig iron -> high carbon steel -> steel; that steel + 4 steel + 2 ni + 2 black bronze -> weak steel ingot, welded with pig iron -> high carbon black steel -> black steel; that black steel (+ stock) -> weak red steel and weak blue steel (10/5/3/2) -> welded with black steel -> red and blue steel. Plans from the anvil recipes (world-seeded targets), fewest steps by BFS, AnvilForger/Welder on the anvil record. Stand-ins: crucible heat runs at 1600 C via CrucibleDevice.heat, working heat written on anvil inputs, stock side metals and pig iron. Results: PASSED on Paper 1.21.11 and Folia 1.21.11 (second start of each run dir; the first start installs the CE content). The whole runs still exit 1 from other cases (config, ce.pack_issues, device.roundtrip, forge.continuous, kiln.fire, husbandry.*), and :itest:test SelfTestSuiteTest expects 47 cases (49 listed; no case added here); none is in this item's files. Follow-up PAR-REL-13: pig iron from a real blast furnace once PAR-DECO-16 adds tfp:reinforced_fire_bricks."
tests:
  - "itest"
done_criteria:
  - "Green on all targets"
---

# PAR-REL-05: Metal run self-test


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
