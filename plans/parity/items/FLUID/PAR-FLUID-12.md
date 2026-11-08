---
id: PAR-FLUID-12
title: "Bucket config keys: buckets: section in config.yml, typed config and BucketModule reads the current value"
area: FLUID
status: open
wave: 11
deps: [PAR-FLUID-08]
size: S
aliases: ["follow-up of PAR-FLUID-08 (BucketModule.java and ConfigServiceTest.java were outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "paper/src/main/resources/config.yml"
  - "paper/src/test/java/dev/tfp/paper/config/ConfigServiceTest.java"
  - "paper/src/main/java/dev/tfp/paper/bucket/BucketModule.java"
  - "paper/src/test/java/dev/tfp/paper/bucket/BucketConfigWiringMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "Split from PAR-FLUID-08 (which built the bucket look after the debug fill). Adding a top-level `buckets:` section needs `ConfigServiceTest` (it lists every section of the default config.yml: `shippedDefaultsParseToTheRecordDefaultsWithNothingMissing` fails while the section is missing from config.yml, `ownerFileKeepsCommentsAndGetsMissingSections` fails once it is there until `buckets` is in its expected list) and `BucketModule` (the listener's supplier), neither owned by PAR-FLUID-08."
  - "(1) `TfpConfig`: a sixth component `BucketConfig buckets` (`core/bucket/BucketConfig`, TFC `woodenBucketCanPlaceSources` / `metalBucketCanPlaceSources`, default false), keeping the 4- and 5-argument constructors with `BucketConfig.DEFAULTS`. (2) `ConfigCodec.parse`: `buckets` in the top-level known list; read `buckets.wooden-bucket-can-place-sources` and `buckets.metal-bucket-can-place-sources` with `Reader.bool` (defaulted when absent, warn on non-booleans, warn on unknown keys such as a capacity key); `toMap` writes them. (3) Append to the default `config.yml`: `# Buckets (TFC server config \"buckets\")` with TFC's comments (`If true, a wooden bucket can place source blocks`, `If true, a metal (red and blue steel) bucket can place source blocks`), both false, and a note that capacities stay 1000 mB; add `\"buckets\"` to `ConfigServiceTest`'s expected added sections. (4) `BucketModule.enable`: replace `() -> BucketConfig.DEFAULTS` with `() -> context.config().current().buckets()` so `/tfp reload config` applies it. (5) Ledger: update the divergence row on TFC's bucket config options: only the capacities stay fixed (every reader of `FluidContainerKind.defaultCapacity` would need the value)."
tests:
  - "core: ConfigCodec reads and writes the bucket keys, defaulted when absent, bad values and unknown keys warn, no unknown-key warning for buckets; equals BucketConfig.fromTree on the same tree"
  - "paper: ConfigServiceTest shipped defaults parse with nothing defaulted; an owner file gets the buckets section added"
  - "paper: MockBukkit, a listener built as BucketModule builds it, over a ConfigService whose file sets wooden-bucket-can-place-sources: true, places a water source from a wooden bucket; after editing back to false and reloading it places a flowing block"
done_criteria:
  - "Setting wooden-bucket-can-place-sources: true makes the wooden bucket place a water source"
  - "The default config.yml carries the buckets section with TFC's defaults and comments; ConfigCodec gives no unknown-key warning for it"
---

# PAR-FLUID-12: Bucket config keys: buckets: section in config.yml, typed config and BucketModule reads the current value

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
