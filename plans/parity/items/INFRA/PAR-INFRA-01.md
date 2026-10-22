---
id: PAR-INFRA-01
title: "Feature module SPI: register listeners, services and debug subcommands without editing TfpPlugin"
area: INFRA
status: open
wave: 1
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/TerraFirmaCraft.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/module/**"
  - "core/src/test/java/dev/tfp/core/module/**"
  - "core/src/test/resources/dev/tfp/core/module/**"
  - "paper/src/main/java/dev/tfp/paper/module/**"
  - "paper/src/test/java/dev/tfp/paper/module/**"
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
  - "paper/src/main/java/dev/tfp/paper/boot/TfpCommands.java"
  - "paper/src/main/resources/META-INF/services/**"
ui: false
in_game_steps: []
plan:
  - "Current: every feature is wired by hand in paper/.../TfpPlugin.java (1332 lines) and TfpCommands.java; two parallel items would both edit them."
  - "Add `dev.tfp.paper.module.FeatureModule` (id(), enable(ModuleContext), disable(), debugCommands(DebugTree)) and `ModuleContext` (plugin, Scheduler, IntegrationContext, RegistryService, ConfigService, Messages, PlayerDataStore, AsyncDecisionPipeline, ChunkDataStore)."
  - "Load modules with `ServiceLoader` from `META-INF/services/dev.tfp.paper.module.FeatureModule` (one line per module, append-only file)."
  - "Let modules contribute `tfp debug <sub>` literals through a `DebugTree` builder that TfpCommands merges into the existing tree."
  - "Move one existing feature (food: FoodMergeListener + FoodStampProcessor wiring) onto a module as the worked example; leave the rest where it is."
  - "Document the convention in ARCHITECTURE.md (module section) and in plans/parity/PROTOCOL.md 'Shared hubs'."
tests:
  - "paper: ServiceLoader discovery test with a fake module (enable/disable order, debug literal merged)"
  - "paper: BootstrapTest still green (the /tfp tree is unchanged apart from module literals)"
done_criteria:
  - "A new feature can be wired by adding one class and one line in META-INF/services, without touching TfpPlugin.java or TfpCommands.java"
  - "./gradlew check green"
---

# PAR-INFRA-01: Feature module SPI: register listeners, services and debug subcommands without editing TfpPlugin

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
