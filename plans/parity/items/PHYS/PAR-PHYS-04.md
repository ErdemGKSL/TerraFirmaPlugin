---
id: PAR-PHYS-04
title: "Support beams: blocks, connections, collapse prevention"
area: PHYS
status: open
wave: 22
deps: [PAR-INFRA-10, PAR-WORLD-10]
size: M
aliases: ["M3-PHYS-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/HorizontalSupportBlock.java"
  - "src/generated/resources/data/tfc/tfc/support"
files_owned:
  - "core/src/main/java/dev/tfp/core/beam/**"
  - "core/src/test/java/dev/tfp/core/beam/**"
  - "core/src/test/resources/dev/tfp/core/beam/**"
  - "paper/src/main/java/dev/tfp/paper/beam/**"
  - "paper/src/test/java/dev/tfp/paper/beam/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/beam/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/beam/**"
  - "content/generated/content/craftengine/tfp/configuration/beam/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/support/oak 8"
  - "build two poles and a span; screenshot; break a pole"
plan:
  - "Per-wood vertical/horizontal beams with connection states from BeamRules via the neighbour runner (provisional state on place); drops when unsupported; support data feeds the collapse SupportTable."
tests:
  - "core: BeamGoldenTest exists"
  - "paper: place/break chain converges"
done_criteria:
  - "A beam spanning 5 blocks connects; removing a pole drops the span"
---

# PAR-PHYS-04: Support beams: blocks, connections, collapse prevention

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
