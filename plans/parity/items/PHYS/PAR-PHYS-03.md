---
id: PAR-PHYS-03
title: "Landslides and gravity soils"
area: PHYS
status: open
wave: 17
deps: [PAR-PHYS-01, PAR-INFRA-10]
size: M
aliases: ["M3-PHYS-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/LandslideRecipe.java"
  - "src/generated/resources/data/tfc/recipe/landslide"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/landslide/**"
  - "core/src/test/java/dev/tfp/core/landslide/**"
  - "core/src/test/resources/dev/tfp/core/landslide/**"
  - "paper/src/main/java/dev/tfp/paper/landslide/**"
  - "paper/src/test/java/dev/tfp/paper/landslide/**"
ui: true
in_game_steps:
  - "place TFC dirt on a 1-block ledge; remove the support; screenshot"
plan:
  - "Neighbour updates on #can_landslide blocks enqueue LandslideRules.decide with the 2-tick delay; falls and sideways slides through PAR-PHYS-01."
tests:
  - "core: LandslideGoldenTest exists"
  - "paper: queue/delay test"
done_criteria:
  - "Dirt on a ledge slides when its support is removed"
---

# PAR-PHYS-03: Landslides and gravity soils

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
