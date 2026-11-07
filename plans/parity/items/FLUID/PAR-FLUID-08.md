---
id: PAR-FLUID-08
title: "Bucket config keys in config.yml and the bucket look after every fill"
area: FLUID
status: open
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
tests:
  - "core: ConfigCodec reads and writes the bucket keys, unknown-key warning gone"
  - "paper: debug fill of a wooden bucket sets custom_model_data flag 0 and the fluid colour (server-side check; MockBukkit meta copies drop custom_model_data)"
done_criteria:
  - "Setting wooden-bucket-can-place-sources: true makes the wooden bucket place a water source"
  - "A bucket filled with /tfp debug fluid shows the fluid layer"
---

# PAR-FLUID-08: Bucket config keys in config.yml and the bucket look after every fill

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
