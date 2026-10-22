---
id: PAR-DECO-13
title: "Potted plants and krummholz"
area: DECO
status: open
wave: 49
deps: [PAR-WORLD-12]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KrummholzBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/potted/**"
  - "content/generated/content/craftengine/tfp/configuration/potted/**"
ui: true
in_game_steps:
  - "tfp debug showcase potted"
plan:
  - "Generate potted variants and krummholz (with potted krummholz) blocks."
tests:
  - "datagen counts"
done_criteria:
  - "Showcase screenshot"
---

# PAR-DECO-13: Potted plants and krummholz

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
