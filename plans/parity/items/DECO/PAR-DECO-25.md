---
id: PAR-DECO-25
title: "Sign runtime: placement rotation, text editing, text display, sign carriers (follow-up of PAR-DECO-03)"
area: DECO
status: done
wave: 47
deps: [PAR-DECO-03]
size: L
aliases: ["split from PAR-DECO-03: the sign blocks, models, items and names exist; what needs Paper code, the integration adapters or the hitbox files is here"]
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
  - "Placement: standing and ceiling hanging signs take rotation 0..15 from the placer's yaw (vanilla RotationSegment), the ceiling one sets attached when its neighbour is a full block beside it; the wall hanging sign survives when a sturdy block is on either side of its beam (TFCWallHangingSignBlock.canSurvive); a sign survives on a sign below (TFC's explicit StandingSignBlock check). A CraftEngine behaviour in the craftengine integration adapter."
  - "Editor: right-click opens the vanilla sign editor with a ClientboundOpenSignEditor packet for the position (the client builds the editor from any block state), the update packet is read in the packet layer, SignText.sanitize limits it, SignText is kept in the chunk PDC; dye, glow ink, honeycomb wax and axe-stripped wax like vanilla; the editing side is SignGeometry.isFacingFront."
  - "Text display: text_display entities (packets only) on each written side, 4 lines of the sign's colour, glow flag as full-bright, centred as vanilla (line height 10, scale 1/ 2 of the board), shown to the viewers of the chunk and rebuilt on chunk send; unwritten signs spawn nothing."
  - "Carriers: the 400 sign blocks are hitbox-only on jungle_button states (PAR-DECO-03), a tiny outline that is hard to aim at. Add shape rows for the four vanilla sign outlines to TfcShapes and HitboxDemands, let the selector pick better free carriers (no vanilla sign state: the client draws a board for it), and switch SignBlocks to AllocatedCarriers like TorchBlocks."
  - "Wall hanging signs: the carrier facing so the outline lies along the beam; the plank collision (TFC wall hanging signs have none)."
tests:
  - "core: SignText and SignGeometry golden values from vanilla; paper: MockBukkit editor update, PDC round trip, text display packets; datagen: carriers from the allocation"
done_criteria:
  - "Writing on an oak TFC sign shows text on the TFC texture, on both sides, in the dye colour and glowing with glow ink; waxed signs refuse edits; signs survive and drop like TFC's"
---

# PAR-DECO-25: Sign runtime (follow-up of PAR-DECO-03)


All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-03 shipped the blocks (`dev.tfp.datagen.sign.SignBlocks`, `configuration/sign/<wood>.yml`), the models of `SignModels`, the item icons, names, and the pure `core/sign` value types (`SignText`, `SignGeometry`).

Scope note: this item shipped the placement rotation (standing and ceiling hanging signs, `attached`) as a paper `sign` module. The editor and packet layer, text displays, wax and dye, better carriers and the wall hanging survival rule moved to PAR-DECO-28 (large, and not checkable without a client).

Pending in-game check (container cannot run client).

Done: core `SignPlacement`, paper `SignModule` and `SignPlacementListener` (service line added); tests `SignPlacementTest` (4), `SignPlacementMockBukkitTest` (4); ledger rows.
