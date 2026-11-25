---
id: PAR-PHYS-04
title: "Support beams: blocks, connections, collapse prevention"
area: PHYS
status: blocked
wave: 22
deps: [PAR-INFRA-10, PAR-WORLD-10, PAR-INFRA-13]
size: M
aliases: ["M3-PHYS-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/HorizontalSupportBlock.java"
  - "src/generated/resources/data/tfc/tfc/support"
files_owned:
  - "core/src/main/java/dev/tfp/core/beam/**"
  - "core/src/test/java/dev/tfp/core/beam/**"
  - "core/src/test/resources/dev/tfp/core/beam/**"
  - "paper/src/main/java/dev/tfp/paper/beam/**"
  - "paper/src/test/java/dev/tfp/paper/beam/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/beam/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/beam/**"
  - "content/generated/content/craftengine/tfp/configuration/beam/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/support/oak 8"
  - "build two poles and a span; screenshot; break a pole"
plan:
  - "Blocked (2026-11-25): the CraftEngine beam blocks and the support item cannot be generated inside files_owned. Every explicit appearance state in generated CE YAML must come from the hitbox selector (GeneratedCarrierTest: each state equals HitboxAllocation.agreed of a HitboxDemands entry, and the exact block ids are pinned), and BlockCoverageTest fails once this item is done while datagen coverage/BlockCoverage.java and material/ItemCoverage.java still name PAR-PHYS-04 as owner of tfc:wood/(horizontal|vertical)_support/* and tfc:wood/support/*. Unblock (owner/manager): add to files_owned tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java, hitbox/TfcShapes.java, tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json, tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java (shared: own rows and pins only, as PAR-CLIM-04), and the two coverage owner lines (or let the generated blocks retire them). Then: tfc-shapes rows tfc:wood/vertical_support[north,east,south,west] (box 5,0,5-11,16,11 plus the 10..16 arms) and tfc:wood/horizontal_support[...] (box 5,10,5-11,16,11 plus arms), demands per wood (likely hitbox-only with TFC's models as item displays, like the barrels); datagen/beam writing configuration/beam/*.yml: 40 blocks with boolean north/east/south/west (TFC strength 1.0, wood sounds, axe, flammableLikeLogs, noOcclusion, loot = one tfp:wood/support/<wood>, survives_explosion), the 20 support items as plain items with no block placement (BeamListener places them; a CE block-item behaviour would race it), TFC's support models/textures; then the in-game steps."
  - "Done here (runtime, in files_owned plus the landslide hand-off the manager asked for): core/beam BeamRules.settle (all updateShape cases at once) and BeamPlacement (vanilla StandingAndWallBlockItem + orderedByNearest/getNearestLookingDirections, TFC setPlacedBy of both beams); paper/beam BeamBlocks (ids), BeamState, BeamShapeRule, BeamWorld (air over a beam drops its support item), BeamService (own NeighbourRunner per world), BeamListener (support item click: snapshot, CPU plan, region apply; BlockPhysicsEvent, broken block sides), BeamModule + service-file line. Collapse: CollapseSupports already matches tfp:wood/horizontal_support/* from TFC's data. Landslide (outside files_owned, on the manager's instruction and the ledger row): LandslideModule hands a CollapseSupports to LandslideService, whose snapshot adds the 9x5x9 beam reach read as kinds only (LandslideWorld.kind, LandslideBlocks.supportCell)."
  - "Pending in-game check (container cannot run client)"
  - "Per-wood vertical/horizontal beams with connection states from BeamRules via the neighbour runner (provisional state on place); drops when unsupported; support data feeds the collapse SupportTable."
tests:
  - "core: BeamGoldenTest exists"
  - "paper: place/break chain converges"
done_criteria:
  - "A beam spanning 5 blocks connects; removing a pole drops the span"
---

# PAR-PHYS-04: Support beams: blocks, connections, collapse prevention

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
