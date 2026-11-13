---
id: PAR-SURV-14
title: "Survival config keys in config.yml: keepNutritionAfterDeath, peaceful passive regeneration, naturalRegenerationModifier"
area: SURV
status: open
wave: 11
deps: [PAR-SURV-09]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/**"
  - "core/src/test/java/dev/tfp/core/config/**"
  - "paper/src/main/resources/config.yml"
  - "paper/src/test/java/dev/tfp/paper/config/**"
  - "paper/src/main/java/dev/tfp/paper/health/HealthModule.java"
  - "paper/src/main/java/dev/tfp/paper/player/Respawn*"
  - "paper/src/test/java/dev/tfp/paper/player/Respawn*"
ui: false
in_game_steps: []
plan:
  - "Follow-up of PAR-SURV-09. Add a `survival` section to our config (`TfpConfig`/`ConfigCodec`, default `config.yml`, `ConfigServiceTest`'s key and section lists): `keep-nutrition-after-death` (TFC `keepNutritionAfterDeath`, true), `peaceful-passive-regeneration` (TFC `enablePeacefulDifficultyPassiveRegeneration`, false), `natural-regeneration-modifier` (TFC `naturalRegenerationModifier`, 1, range 0..100). `RespawnModule` reads the first two from the config instead of the JVM properties (`RespawnRules.fromProperties`; keep the properties as overrides or drop them); `HealthModule` builds its `HealthConfig` with the modifier instead of `HealthConfig.DEFAULT` (also the `PlayerNeedsHudService` health display, if it uses the regeneration factor). If PAR-FLUID-12 adds a shared server-options section first, put the keys there. Remove the PAR-SURV-09 config divergence row in the ledger when done."
tests:
  - "core: ConfigCodec reads and writes the survival keys, defaults TFC's, out-of-range modifier warns"
  - "paper: MockBukkit respawn with keep-nutrition-after-death false resets nutrition"
done_criteria:
  - "Setting keep-nutrition-after-death: false and /tfp reload config makes a respawn show default nutrition"
---

# PAR-SURV-14: Survival config keys in config.yml

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
