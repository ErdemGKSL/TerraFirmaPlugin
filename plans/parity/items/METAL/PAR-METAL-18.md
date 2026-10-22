---
id: PAR-METAL-18
title: "Bells (bronze, brass)"
area: METAL
status: open
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
tests:
  - "datagen counts"
done_criteria:
  - "Ringing a brass bell"
---

# PAR-METAL-18: Bells (bronze, brass)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
