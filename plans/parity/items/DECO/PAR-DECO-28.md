---
id: PAR-DECO-28
title: "Sign runtime, second part: editor, text display, wax and dye, carriers, wall hanging survival (follow-up of PAR-DECO-25)"
area: DECO
status: open
wave: 48
deps: [PAR-DECO-25]
size: L
aliases: ["split from PAR-DECO-25: the placement rotation shipped there"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCStandingSignBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCWallSignBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCCeilingHangingSignBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCWallHangingSignBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCSignBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCHangingSignBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/sign/**"
  - "core/src/test/java/dev/tfp/core/sign/**"
  - "paper/src/main/java/dev/tfp/paper/sign/**"
  - "paper/src/test/java/dev/tfp/paper/sign/**"
  - "paper/src/main/java/dev/tfp/paper/integration/craftengine/sign/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/sign/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/sign/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/sign/oak; place on the floor facing four directions and on a wall; right-click, write four lines on both sides, dye, glow ink, wax; screenshot GUI scale 2 and 3"
  - "ce item give Bot tfp:wood/hanging_sign/copper/oak; place under a block and on a wall; write; screenshot"
plan:
  - "Wall hanging signs survive when a sturdy block is on either side of the beam (TFCWallHangingSignBlock.canSurvive); a sign survives on a sign below (TFC's explicit StandingSignBlock check). A CraftEngine behaviour in the integration adapter."
  - "Editor: right-click opens the vanilla sign editor with a ClientboundOpenSignEditor packet for the position (the client builds the editor from any block state), the update packet is read in the packet layer, SignText.sanitize limits it, SignText is kept in the chunk PDC; dye, glow ink, honeycomb wax and axe-stripped wax like vanilla; the editing side is SignGeometry.isFacingFront."
  - "Text display: text_display entities (packets only) on each written side, 4 lines of the sign's colour, glow flag as full-bright, centred as vanilla (line height 10, scale 1/ 2 of the board), shown to the viewers of the chunk and rebuilt on chunk send; unwritten signs spawn nothing."
  - "Carriers: the 400 sign blocks are hitbox-only on jungle_button states (PAR-DECO-03), a tiny outline that is hard to aim at. Add shape rows for the four vanilla sign outlines to TfcShapes and HitboxDemands, let the selector pick better free carriers (no vanilla sign state: the client draws a board for it), and switch SignBlocks to AllocatedCarriers like TorchBlocks."
  - "Wall hanging signs: the carrier facing so the outline lies along the beam; the plank collision (TFC wall hanging signs have none)."
tests:
  - "core: SignText and SignGeometry golden values from vanilla; paper: MockBukkit editor update, PDC round trip, text display packets; datagen: carriers from the allocation"
done_criteria:
  - "Writing on an oak TFC sign shows text on the TFC texture, on both sides, in the dye colour and glowing with glow ink; waxed signs refuse edits; signs survive and drop like TFC's"
---

# PAR-DECO-28: Sign runtime, second part (follow-up of PAR-DECO-25)

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-25 shipped `SignPlacement` and `SignPlacementListener`.
