---
id: PAR-FAUNA-34
title: "Livestock check hands horse-family mating and birth to the mount module (follow-up of PAR-FAUNA-15)"
area: FAUNA
status: open
wave: 46
deps: [PAR-FAUNA-15]
size: S
aliases: ["follow-up of PAR-FAUNA-15 (2026-11-25): LivestockBehaviour pairs and delivers horses without the tamed and unridden gate and always spawns the mother's species"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCHorse.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCDonkey.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/livestock/LivestockBehaviour.java"
  - "paper/src/test/java/dev/tfp/paper/livestock/**"
  - "paper/src/main/java/dev/tfp/paper/mount/**"
  - "paper/src/test/java/dev/tfp/paper/mount/**"
ui: false
in_game_steps: []
plan:
  - "LivestockBehaviour.mate pairs two ready animals of one species for every livestock species, including horse, donkey and mule, with no tamed or unridden check (MountBreeding.canMate). Skip the horse family there (mount module owns it) and let MountModule fertilize and deliver, so a foal is born as a mule from the genes directly and the replace-after-birth step of MountModule.resolveFoal goes."
tests:
  - "paper: a ridden or untamed horse pair is not fertilized by the livestock check; a horse and donkey birth spawns a mule foal"
done_criteria:
  - "No horse-family pairing or birth happens outside MountModule"
---

# PAR-FAUNA-34: Livestock check hands horse-family mating and birth to the mount module (follow-up of PAR-FAUNA-15)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
