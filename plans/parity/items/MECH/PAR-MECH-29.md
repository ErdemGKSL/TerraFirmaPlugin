---
id: PAR-MECH-29
title: "Water wheel and windmill spin: the wheel and the blades on the spin wire at the source's speed, the per-slot blade kind and dye colour, TFC's rustic blade model"
area: MECH
status: open
wave: 55
deps: [PAR-MECH-18]
size: M
aliases: ["follow-up of PAR-MECH-18 (2026-11-25): the spinning shafts (paper/rotation/spin, RotationService.shafts) know only axles and bladed axles, one display per position; PAR-MECH-18 does not own paper/rotation"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/render/blockentity/WaterWheelBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/WindmillBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/client/model/entity/WindmillBladeRusticModel.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "paper/src/main/java/dev/tfp/paper/windmill/**"
  - "paper/src/test/java/dev/tfp/paper/windmill/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/power/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/power/**"
  - "content/generated/content/craftengine/tfp/configuration/power/**"
  - "docs/verification/PAR-MECH-29/**"
ui: true
in_game_steps:
  - "a water wheel in a river with an axle line (as PAR-MECH-03's step): the wheel turns about its axis in step with the axles; screenshots 1 s apart"
  - "a windmill with 5 blades of different kinds (white, red, lattice, rustic, blue): each blade shows its kind and colour and they all turn; with 3 blades they are 120 degrees apart; screenshots"
plan:
  - "PAR-MECH-18 generates the blocks with static displays (configuration/power/): the wheel is TFC's WaterWheelModel baked into tfp:block/power/water_wheel (render items tfp:render/power/water_wheel/<wood>, drawn at scale 4), the windmill draws the axle's shaft and count white default blades (tfp:render/power/windmill_blade, scale 4, blade i at 360 / count * i) at rest; tfp:render/power/windmill_blade_lattice exists but is unused; the rustic model (rotations about x and z at 18 and 20 degrees, not one element axis) is not baked."
  - "Spin: let the spinning visuals (SpinShafts or a sibling like SpinCranks) take water wheel and windmill sources: RotationService reports them (MechanicalBlock WATER_WHEEL / WINDMILL source states, their speed and network), the item resolver reads the wheel's render item / the windmill's slots (the device record) on the region thread, and one display per blade turns about the axis with TFC's angle (-getRotationAngle + 2 pi / count * i); the CE appearances then keep only the carrier (as the axle's, PAR-MECH-12)."
  - "Blade looks: 16 dye render items (the default blade with a constant tint of DyeColor.getTextureDiffuseColor, white untinted), lattice, and rustic (WindmillBladeRusticModel: several displays, or a rotation-free approximation recorded as a divergence); TFC's renderWindmillExtras for a full set of 5 identical blades."
tests:
  - "paper: a wheel and a windmill source report spawns one display (wheel) / count displays (windmill) per viewer and segments at the source speed; the blade item per slot picks its render item"
  - "datagen: the dye render items and their tints; the rustic blade model"
done_criteria:
  - "A water wheel and a windmill visibly turn at their network speed, each blade with its kind and colour (screenshots in docs/verification/PAR-MECH-29/)"
---

# PAR-MECH-29: Water wheel and windmill spin

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
