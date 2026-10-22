---
id: PAR-SURV-09
title: "Death, respawn and regeneration rules"
area: SURV
status: open
wave: 10
deps: [PAR-SURV-05]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/player/Respawn*"
  - "paper/src/test/java/dev/tfp/paper/player/Respawn*"
ui: false
in_game_steps: []
plan:
  - "On death/respawn: reset thirst and nutrition per keepNutritionAfterDeath; in peaceful apply enablePeacefulDifficultyPassiveRegeneration; naturalRegenerationModifier in HealthRegen."
tests:
  - "paper: MockBukkit respawn resets"
done_criteria:
  - "Respawn shows full thirst and default nutrition"
---

# PAR-SURV-09: Death, respawn and regeneration rules

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
