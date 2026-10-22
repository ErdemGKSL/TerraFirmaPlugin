---
id: PAR-FAUNA-15
title: "Horses, donkeys and mules: riding, breeding, chests"
area: FAUNA
status: open
wave: 44
deps: [PAR-FAUNA-05]
size: M
aliases: ["M5-MNT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/horse"
files_owned:
  - "core/src/main/java/dev/tfp/core/mount/**"
  - "core/src/test/java/dev/tfp/core/mount/**"
  - "core/src/test/resources/dev/tfp/core/mount/**"
  - "paper/src/main/java/dev/tfp/paper/mount/**"
  - "paper/src/test/java/dev/tfp/paper/mount/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn horse adult; tame; saddle; ride; screenshot"
plan:
  - "Vanilla horse/donkey/mule carriers with TFC stats (MountStats), familiarity taming, saddles, chests on donkeys/mules (carried weight -> overburden), mule from horse+donkey."
tests:
  - "core: mount goldens exist"
done_criteria:
  - "Ride a tamed TFC horse; breed a mule"
---

# PAR-FAUNA-15: Horses, donkeys and mules: riding, breeding, chests

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
