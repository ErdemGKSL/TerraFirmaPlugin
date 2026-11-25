---
id: PAR-METAL-18
title: "Bells (bronze, brass)"
area: METAL
status: done
wave: 37
deps: [PAR-METAL-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCBellBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/bell/**"
  - "paper/src/test/java/dev/tfp/paper/bell/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bell/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bell/**"
  - "content/generated/content/craftengine/tfp/configuration/bell/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:brass_bell; place; ring"
plan:
  - "Bronze and brass bells as vanilla bell carriers with TFC textures and pitch."
  - "Done: tfp:bronze_bell/tfp:brass_bell CE blocks (16 states, vanilla BellBlock outlines as hitbox-only carriers, frame + body item displays, TFC settings/loot/item/names), BellModule (right click and projectile ring at TFC's pitch, hammer lower, vanilla placement turn); tests BellRulesTest, BellDevicesMockBukkitTest, BellBlocksTest; ledger rows; Pending in-game check (container cannot run client): the in_game_steps stay in this file, see docs/verification/PAR-METAL-18/verdict.md."
tests:
  - "datagen counts"
done_criteria:
  - "Ringing a brass bell"
---

# PAR-METAL-18: Bells (bronze, brass)


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
