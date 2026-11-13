---
id: PAR-SURV-09
title: "Death, respawn and regeneration rules"
area: SURV
status: done
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
  - "Done: PAR-SURV-09 commit. paper/player RespawnRules (pure: TFC's fresh PlayerInfo after a death with the nutrition copied per keepNutritionAfterDeath; the extra-peaceful step: heal 1 every 20 ticks, +1 hunger and +5 thirst every 10 ticks) and RespawnModule (feature module `respawn`: PlayerRespawnEvent DEATH at MONITOR resets the data and removes the tfp:thirst PDC effect; End exits keep everything; the peaceful throttle only when enabled, survival/adventure only). naturalRegenerationModifier is already the factor in core HealthRegen (PAR-SURV-05, 1). Settings: TFC defaults, overridable by -Dtfp.keepNutritionAfterDeath / -Dtfp.enablePeacefulDifficultyPassiveRegeneration; config keys are follow-up PAR-SURV-14 (config.yml, TfpConfig and HealthModule are not in files_owned). Tests: RespawnRulesTest (4), RespawnModuleMockBukkitTest (5); :core:test :paper:test :tools:datagen:test pass. Divergences in the ledger (config, peaceful drain, game modes, End exit)."
tests:
  - "paper: MockBukkit respawn resets"
done_criteria:
  - "Respawn shows full thirst and default nutrition"
---

# PAR-SURV-09: Death, respawn and regeneration rules

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
