---
id: PAR-REL-05
title: "Metal run self-test"
area: REL
status: open
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
tests:
  - "itest"
done_criteria:
  - "Green on all targets"
---

# PAR-REL-05: Metal run self-test

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
