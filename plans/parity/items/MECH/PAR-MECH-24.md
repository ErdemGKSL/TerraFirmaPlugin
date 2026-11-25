---
id: PAR-MECH-24
title: "Axle, encased axle, clutch and creative motor cannot be placed by item: CraftEngine's hardcoded axis behaviour casts the string property"
area: MECH
status: open
wave: 54
deps: [PAR-MECH-01]
size: S
aliases: ["found by PAR-MECH-16 (2026-11-25) in game"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/AxleBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceProperty.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rotation/**"
ui: false
in_game_steps: []
plan:
  - "Finding: right-clicking a block with the tfp:creative_motor / tfp:wood/axle/<wood> / encased_axle / clutch item logs `Could not pass event PlayerInteractEvent to CraftEngine` + `ClassCastException: Direction$Axis$1 cannot be cast to String` at HardcodedPropertyBlockBehavior.updateStateForPlacement (BlockItemBehavior.place) and nothing is placed. CraftEngine puts its hardcoded property behaviour on every block property named `axis` and expects CraftEngine's `axis` property type; DeviceProperty writes `type: string` for it (see its class comment). /tfp debug place worked because it does not run that path."
  - "Verified fix (in a scratch worktree, jar run on Paper 1.21.11 with the real client, PAR-MECH-16): in DeviceProperty.yaml() STRING case write `type: axis` (no `values`) when `name.equals(\"axis\") && values.equals(List.of(\"x\", \"y\", \"z\"))` (the full set is exactly CraftEngine's axis type, so no state is added); everything else stays `string`. With it motor, 5 axles, gearbox, encased axle and axle are placed by item with the right axis, and the network reads 30.00 rpm."
  - "Still open: the two-value (x,z) `axis` properties (firepit, grill, charcoal forge, channel...) are `type: string` too; if any of them is placed by a CraftEngine block item (not by our own placement) it will hit the same cast. Check with a real placement; CraftEngine's axis type has y as a third value, so a horizontal axis needs either the `axis` type plus a placement rule or a rename of the property (e.g. `tfp_axis`) in datagen and the paper code that reads it. Update the golden tests that pin `type: string` for axis (CharcoalBlocksTest, FirepitTest, GrillTest if touched)."
  - "Regenerate content and rerun the PAR-MECH-16 placement: place the motor east face of a stone, axles by clicking the previous block's east face."
tests:
  - "datagen: DeviceProperty yaml for axis x/y/z is `type: axis` without values; every other names property stays string"
done_criteria:
  - "The five rotation items are placed by right-click in game with no ClassCastException in the server log"
---

# PAR-MECH-24: Rotation blocks cannot be placed by item

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
