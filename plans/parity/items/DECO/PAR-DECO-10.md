---
id: PAR-DECO-10
title: "Ropes, rope anchors and rope knots"
area: DECO
status: open
wave: 40
deps: []
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/AbstractRopeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/HangingRopeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/RopeAnchorBlock.java"
  - "src/main/java/net/dries007/tfc/common/items/RopeItem.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/RopeKnot.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rope/**"
  - "core/src/test/java/dev/tfp/core/rope/**"
  - "core/src/test/resources/dev/tfp/core/rope/**"
  - "paper/src/main/java/dev/tfp/paper/rope/**"
  - "paper/src/test/java/dev/tfp/paper/rope/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rope/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rope/**"
  - "content/generated/content/craftengine/tfp/configuration/rope/**"
ui: true
in_game_steps:
  - "place rope anchor; use rope down; climb; screenshot"
plan:
  - "Climbable hanging rope (vanilla scaffolding/vines carrier), rope anchors (rock/steel), rope knot leash entity as vanilla leash knot."
tests:
  - "paper: place rope chain"
done_criteria:
  - "Climb a rope down a shaft"
---

# PAR-DECO-10: Ropes, rope anchors and rope knots

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
