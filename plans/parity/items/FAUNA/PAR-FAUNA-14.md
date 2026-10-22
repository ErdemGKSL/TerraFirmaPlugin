---
id: PAR-FAUNA-14
title: "Pets: dogs and cats with commands"
area: FAUNA
status: open
wave: 44
deps: [PAR-FAUNA-05, PAR-INFRA-03]
size: M
aliases: ["M5-PET-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/pet"
  - "src/main/java/net/dries007/tfc/common/entities/ai/pet"
  - "src/main/java/net/dries007/tfc/client/screen/PetCommandScreen.java"
  - "src/main/java/net/dries007/tfc/network/PetCommandPacket.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/pet/**"
  - "core/src/test/java/dev/tfp/core/pet/**"
  - "core/src/test/resources/dev/tfp/core/pet/**"
  - "paper/src/main/java/dev/tfp/paper/pet/**"
  - "paper/src/test/java/dev/tfp/paper/pet/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn dog adult; tame (feed)"
  - "sneak-right-click; choose Sit; screenshot"
plan:
  - "Taming by familiarity; sneak + right-click opens a small command GUI (relax/home/sit/follow/hunt) replacing the packet; follow/sit/home goals."
tests:
  - "core: PetRulesGoldenTest exists"
done_criteria:
  - "A tamed dog sits on command"
---

# PAR-FAUNA-14: Pets: dogs and cats with commands

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
