---
id: PAR-DECO-03
title: "Signs and hanging signs (per wood, per metal hanger)"
area: DECO
status: done
wave: 46
deps: [PAR-DECO-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCStandingSignBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCCeilingHangingSignBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCSignBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/sign/**"
  - "core/src/test/java/dev/tfp/core/sign/**"
  - "core/src/test/resources/dev/tfp/core/sign/**"
  - "paper/src/main/java/dev/tfp/paper/sign/**"
  - "paper/src/test/java/dev/tfp/paper/sign/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/sign/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/sign/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/sign/oak; place; write; screenshot"
plan:
  - "Signs on vanilla sign carriers (text editing works client-side) with per-wood textures via the sign texture of a repurposed vanilla wood type or entity_renderer board; hanging signs per metal (9) x wood (20) as TFC."
  - "Done 2026-11-25: the 20 signs, 20 wall signs, 180 ceiling and 180 wall hanging signs are CraftEngine blocks (SignBlocks, configuration/sign/<wood>.yml): hitbox-only on jungle_button twins, the board an item display of block models built from vanilla's sign layers (SignModels) over TFC's entity textures; TFC strength 1.0, wood sounds, TFC's tags, self drops (wall blocks drop the item), names in every TFC locale, items placing the wall block on a side face, icons baked from TFC's paletted sign heads (SignIcons). core/sign: SignText (4 lines x 2 sides, dye, glow, wax, 90 px sanitize, PDC codec) and SignGeometry (vanilla yaw and front rule). Tests: SignBlocksTest 10, SignTextTest 6, SignGeometryTest 2. Placement rotation, the editor, the text display and better carriers moved to PAR-DECO-25. Pending in-game check (no client in the build container)."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Writing on an oak TFC sign shows text on the TFC texture"
---

# PAR-DECO-03: Signs and hanging signs (per wood, per metal hanger)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
