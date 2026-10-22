---
id: PAR-FAUNA-11
title: "Aquatic animals: fish, squid, jellyfish, dolphins, orcas, manatees; mob buckets"
area: FAUNA
status: open
wave: 42
deps: [PAR-FAUNA-04, PAR-FAUNA-03]
size: M
aliases: ["M5-WILD-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/aquatic"
  - "src/main/java/net/dries007/tfc/common/entities/ai/TFCFishMoveControl.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/aquatic/**"
  - "core/src/test/java/dev/tfp/core/aquatic/**"
  - "core/src/test/resources/dev/tfp/core/aquatic/**"
  - "paper/src/main/java/dev/tfp/paper/aquatic/**"
  - "paper/src/test/java/dev/tfp/paper/aquatic/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn bluegill adult (in a river)"
  - "use a bucket; screenshot"
plan:
  - "Water carriers (cod/salmon/squid/dolphin) with fluid gates (AquaticSpecies), size variants (SquidSize), octopoteuthis glow, mob buckets per fluid."
tests:
  - "core: aquatic goldens exist"
done_criteria:
  - "Freshwater fish in rivers, cod in oceans; bucketing works"
---

# PAR-FAUNA-11: Aquatic animals: fish, squid, jellyfish, dolphins, orcas, manatees; mob buckets

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
