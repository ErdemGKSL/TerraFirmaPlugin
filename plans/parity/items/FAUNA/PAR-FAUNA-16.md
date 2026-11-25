---
id: PAR-FAUNA-16
title: "Camels (dromedary, bactrian)"
area: FAUNA
status: done
wave: 45
deps: [PAR-FAUNA-15]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/camel"
files_owned:
  - "core/src/main/java/dev/tfp/core/camel/**"
  - "core/src/test/java/dev/tfp/core/camel/**"
  - "core/src/test/resources/dev/tfp/core/camel/**"
  - "paper/src/main/java/dev/tfp/paper/camel/**"
  - "paper/src/test/java/dev/tfp/paper/camel/**"
  - "core/src/main/java/dev/tfp/core/mount/**"
  - "core/src/test/java/dev/tfp/core/mount/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn dromedary_camel adult; ride"
plan:
  - "Camel carriers with TFC breeding (own kind only) and dromedary attributes."
  - "Done: PAR-FAUNA-16 commit: paper/camel CamelModule (TFC attributes for both camels, old age, thrown rider), shared MountSupport; core MountBreedingGoldenTest (10) exists, CamelModuleMockBukkitTest 3; in-game check pending (container cannot run client); gaps in follow-up PAR-FAUNA-34."
tests:
  - "core: MountBreedingGoldenTest exists"
done_criteria:
  - "Ride a camel"
---

# PAR-FAUNA-16: Camels (dromedary, bactrian)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
