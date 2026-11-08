---
id: PAR-FLUID-08
title: "Bucket config keys in config.yml and the bucket look after every fill"
area: FLUID
status: done
wave: 10
deps: [PAR-FLUID-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/**"
  - "core/src/test/java/dev/tfp/core/config/**"
  - "paper/src/main/resources/config.yml"
  - "paper/src/main/java/dev/tfp/paper/fluid/**"
  - "paper/src/main/java/dev/tfp/paper/itemdata/FluidPdc.java"
  - "paper/src/test/java/dev/tfp/paper/itemdata/**"
ui: false
in_game_steps: []
plan:
  - "Follow-up of PAR-FLUID-02. (1) Add TFC's `buckets` section to our config (`TfpConfig`/`ConfigCodec`, default `config.yml`): `wooden-bucket-can-place-sources` and `metal-bucket-can-place-sources` (TFC `woodenBucketCanPlaceSources`, `metalBucketCanPlaceSources`, default false); parse with `core/bucket/BucketConfig.fromTree` or an equivalent and pass the current value to `paper/bucket/BucketModule` (today it uses `BucketConfig.DEFAULTS`; `ConfigCodec` would warn 'unknown key buckets'). TFC's `woodenBucketCapacity`/`metalBucketCapacity` stay TFC's 1000 unless every reader of `FluidContainerKind.defaultCapacity` takes the config value. (2) The tank buckets show their fluid through `custom_model_data` (flag 0, colour 0) written by `paper/bucket/BucketVisuals.sync`; fills outside the bucket listener (`/tfp debug fluid`, later barrels and other block tanks) do not call it yet. Call `BucketVisuals.sync` after the debug fill, or move the sync into `FluidPdc.write` so every writer keeps the look."
  - "Split (2026-11-08): part (1) needs `paper/bucket/BucketModule.java` (the listener's supplier) and `paper/src/test/.../config/ConfigServiceTest.java`, neither in files_owned: that test both requires every key of the shipped config.yml to be present (nothing defaulted) and lists the top-level sections an owner file gets added, so a `buckets:` section cannot be added in either half without editing it. Part (1) and done criterion 1 moved whole to PAR-FLUID-12 (TfpConfig, ConfigCodec, config.yml, ConfigServiceTest, BucketModule). Built here, part (2): `FluidModule.fill` runs `BucketVisuals.sync` on the filled stack for the three tank buckets. Not folded into `FluidPdc.write`: it also serves jugs, bottles and molds, which would gain or lose custom_model_data they do not use."
  - "Done: PAR-FLUID-08 commit; paper `itemdata/DebugFillBucketLookMockBukkitTest` (3: the look step runs on the filled stack of each tank bucket after the fill; not for jugs, bottles or refused fills; the default step writes flag 0 = true and colour 0 = the fluid's TFC tint); `:core:test :paper:test :tools:datagen:test` pass. Criterion 1 and the config keys: PAR-FLUID-12."
tests:
  - "core: ConfigCodec reads and writes the bucket keys, unknown-key warning gone"
  - "paper: debug fill of a wooden bucket sets custom_model_data flag 0 and the fluid colour (server-side check; MockBukkit meta copies drop custom_model_data)"
done_criteria:
  - "Setting wooden-bucket-can-place-sources: true makes the wooden bucket place a water source"
  - "A bucket filled with /tfp debug fluid shows the fluid layer"
---

# PAR-FLUID-08: Bucket config keys in config.yml and the bucket look after every fill

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
