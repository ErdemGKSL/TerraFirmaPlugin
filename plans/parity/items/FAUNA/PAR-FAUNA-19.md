---
id: PAR-FAUNA-19
title: "Lobster traps"
area: FAUNA
status: open
wave: 45
deps: [PAR-FAUNA-13]
size: S
aliases: ["M5-FISH-01 (traps)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/EnterLobsterTrapGoal.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/lobstertrap/**"
  - "core/src/test/java/dev/tfp/core/lobstertrap/**"
  - "core/src/test/resources/dev/tfp/core/lobstertrap/**"
  - "paper/src/main/java/dev/tfp/paper/lobstertrap/**"
  - "paper/src/test/java/dev/tfp/paper/lobstertrap/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/lobstertrap/**"
  - "content/generated/content/craftengine/tfp/configuration/lobstertrap/**"
  - "core/src/main/java/dev/tfp/core/fishing/LobsterTrap*"
  - "core/src/test/java/dev/tfp/core/fishing/LobsterTrap*"
ui: true
in_game_steps:
  - "place trap in sea; bait; spawn lobster; wait; screenshot"
plan:
  - "Baited trap underwater; critters path into it and are caught (LobsterTrap)."
tests:
  - "core: LobsterTrapTest exists"
done_criteria:
  - "A lobster is caught"
---

# PAR-FAUNA-19: Lobster traps

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
