---
id: PAR-HEAT-07
title: "Firepit GUI: fuel slots, input, outputs, temperature bar"
area: HEAT
status: open
wave: 14
deps: [PAR-HEAT-06, PAR-INFRA-03]
size: S
aliases: ["M3-HEAT-03 (GUI)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/FirepitContainer.java"
  - "src/main/resources/assets/tfc/textures/gui/fire_pit.png"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/firepit/gui/**"
  - "paper/src/test/java/dev/tfp/paper/firepit/gui/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:firepit[lit=true]"
  - "right-click it"
  - "screenshot"
plan:
  - "Textured GUI per docs/gui/catalogue.md `m3-heat-03-firepit`: fuel 4 at (8, 70-18i), input (80,29), outputs (71,57),(89,57), temperature bar overlay."
tests:
  - "paper: slot predicates (fuel only in fuel slots)"
done_criteria:
  - "Aligned screenshot at scale 2 and 3"
---

# PAR-HEAT-07: Firepit GUI: fuel slots, input, outputs, temperature bar

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
