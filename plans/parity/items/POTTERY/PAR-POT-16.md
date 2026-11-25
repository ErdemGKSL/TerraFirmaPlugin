---
id: PAR-POT-16
title: "Config codec knows placed-items; large vessel config switches, contents tooltip and TFC sounds (follow-up of PAR-POT-06, PAR-POT-13)"
area: POTTERY
status: done
wave: 28
deps: [PAR-POT-06, PAR-POT-13, PAR-POT-15, PAR-INFRA-50]
size: S
aliases: ["follow-up of PAR-POT-13 (placed-items rejected as an unknown key, 6 ConfigServiceTest failures) and PAR-POT-06 (ledger: config switches, sounds, contents tooltip)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java"
  - "src/main/resources/assets/tfc/sounds.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "paper/src/test/java/dev/tfp/paper/config/ConfigServiceTest.java"
  - "paper/src/main/resources/config.yml"
  - "docs/config.md"
  - "paper/src/main/java/dev/tfp/paper/largevessel/**"
  - "paper/src/test/java/dev/tfp/paper/largevessel/**"
ui: false
in_game_steps: []
plan:
  - "ConfigCodec: `placed-items` (PAR-POT-13, read raw by PlacedItemsModule) is a known top-level key; test. ConfigServiceTest's added-sections expectation lists the sections the shipped file now adds."
  - "`automation.large-vessel` (TFC largeVesselEnableAutomation) and `automation.large-vessel-redstone-seal` (largeVesselEnableRedstoneSeal) in TfpConfig.Automation, ConfigCodec, config.yml and docs/config.md; LargeVesselModule reads both at every use (the hopper spec's `enabled` and the device's `redstoneSeal`)."
  - "TFC vessel sounds: `tfp:block.large_vessel.open` / `.close` (PAR-DATA-08's `Sounds`) instead of the barrel stand-ins."
  - "Contents tooltip: `LargeVesselDrops` stamps `tfp:contents = { tfp:v, tfp:view }` on the dropped vessel (the summary PAR-INFRA-50 reads); the sealed-looking item needs an item-model dispatch and is not done (ledger)."
tests:
  - "core: ConfigCodecTest (placed-items known, large vessel switches)"
  - "paper: LargeVesselMockBukkitTest (spec follows the switch, dropped item summary), ConfigServiceTest"
done_criteria:
  - "ConfigServiceTest passes with the shipped config.yml; the two large vessel keys are mapped in docs/config.md and read by the module; a dropped vessel carries the contents summary"
---

Done: PAR-POT-16 `ConfigCodec` accepts `placed-items`; `TfpConfig.Automation` gains `largeVessel` and `largeVesselRedstoneSeal` (`automation.large-vessel`, `automation.large-vessel-redstone-seal`, shipped config and docs), read by `LargeVesselModule`; vessel open and close play TFC's `tfp:block.large_vessel.*`; `LargeVesselDrops` writes the tooltip summary on the dropped vessel. Tests: core `ConfigCodecTest` +2 (and the defaulted count), paper `LargeVesselMockBukkitTest` +2, `ConfigServiceTest` fixed. No UI. Not done: the sealed-looking item (ledger divergence).

# PAR-POT-16: Config codec knows placed-items; large vessel config switches, contents tooltip and sounds

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
