---
id: PAR-POT-01
title: "Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln)"
area: POTTERY
status: open
wave: 21
deps: [PAR-INFRA-02, PAR-INFRA-08]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java"
  - "src/main/java/net/dries007/tfc/network/PlaceBlockSpecialPacket.java"
  - "src/main/java/net/dries007/tfc/util/InteractionManager.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/placed/**"
  - "core/src/test/java/dev/tfp/core/placed/**"
  - "core/src/test/resources/dev/tfp/core/placed/**"
  - "paper/src/main/java/dev/tfp/paper/placed/**"
  - "paper/src/test/java/dev/tfp/paper/placed/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/placed/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/placed/**"
  - "content/generated/content/craftengine/tfp/configuration/placed/**"
ui: true
in_game_steps:
  - "hold a vessel; sneak + right-click the ground"
  - "screenshot"
  - "right-click to take back"
plan:
  - "TFC places items with a key binding (V); vanilla has no key: use sneak + right-click on a top face with an item that has no other use (divergence row)."
  - "CE block `tfp:placed_item` with 4 quadrants shown by 4 item displays (entity_renderer, dynamic); size limits maxPlacedItemSize/maxPlacedLargeItemSize; take back by right-click."
tests:
  - "paper: MockBukkit place/take per quadrant, size refused"
done_criteria:
  - "In-game: four items placed and taken back"
---

# PAR-POT-01: Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln)

> see_not_parity: NP-CLIENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
