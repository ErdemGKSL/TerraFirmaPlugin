---
id: PAR-PHYS-03
title: "Landslides and gravity soils"
area: PHYS
status: done
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
  - "Done: paper/landslide (LandslideModule after falling; LandslideListener: BlockPlaceEvent and BlockPhysicsEvent; LandslideService: 2-tick region delay with coalescing, one AsyncDecisionPipeline per world over 3x3x3 SlideState snapshots, LandslideRules.decide/step on the CPU pool with kind tables built from the snapshot; LandslideBlocks: remove, destroy the side block, FallingBlocks.spawn with landslide damage; BukkitLandslideWorld: tfc:landslide recipes from the index, #tfp:can_landslide / #tfp:supports_landslide, sturdy faces, toughness from the fall world); core LandslideGoldenTest exists; paper LandslideMockBukkitTest (12, queue/delay incl.) and LandslideContentDataMockBukkitTest (1); ledger row + 4 divergences (no persistence/coalescing, vanilla slide sound, no beams until PAR-PHYS-04, no enableBlockLandslides toggle). In-game check left to the verify stage (docs/verification/PAR-PHYS-03/)."
tests:
  - "core: LandslideGoldenTest exists"
  - "paper: queue/delay test"
done_criteria:
  - "Dirt on a ledge slides when its support is removed"
---

# PAR-PHYS-03: Landslides and gravity soils

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
