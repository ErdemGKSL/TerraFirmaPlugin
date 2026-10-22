---
id: PAR-INFRA-19
title: "Device generators on the shared spec: Firepit, Grill, Pot and ForgeDevices write through DeviceSpecs + DeviceYaml"
area: INFRA
status: open
wave: 4
deps: [PAR-INFRA-08, PAR-INFRA-13]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Grill.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Pot.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/ForgeDevices.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/GrillTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/PotTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/ForgeDevicesTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-08 added `dev.tfp.datagen.device` (DeviceBlockSpec, DeviceYaml, DeviceTemplates, DeviceSpecs). DeviceSpecParityTest shows `DeviceYaml.writeLiteral(DeviceSpecs.firepitFamily/charcoalForge/facing(...))` equals the files the four generators write today, but the generators still build their YAML maps themselves: they were outside PAR-INFRA-08's files (PAR-INFRA-13 owns them and rewrites their carriers first)."
  - "Replace `Firepit.yaml`/`propertyYaml` and `ForgeDevices.forge`/`bellows`/`entry` with the spec: map `Firepit.Prop` to `DeviceProperty` (or drop `Prop`), build the spec with `DeviceSpecs` and the carriers PAR-INFRA-13's selector chose, write it with `DeviceYaml.writeLiteral`. Keep every TFC source check where it is."
  - "Use TFC's values, not today's output: build each spec with `DeviceBlockSpec.Settings.fromTfc(tfc, id)` and `DeviceLoot.fromTfc(tfc, id)` (firepit/grill/pot: 0.4/0.4, nether wart sounds, pickaxe, light 15 when lit via `firepitFamily(..., 15)`, wood ash plus grill/pot item; bellows: 3.0/3.0, wood sounds, axe, self; charcoal forge: 0.2/0.2, `tfp:block.charcoal.*` sounds, shovel, 7 `minecraft:charcoal`). This replaces `tfp:settings/device` (hardness 1, stone, pickaxe, basedrum) and the self loot ForgeDevices writes, and the missing settings, loot and light of the firepit family. Then remove the two ledger divergence rows about those placeholders (charcoal forge/bellows; firepit/grill/pot) and update `DeviceSpecParityTest`, which pins them."
  - "When a device's behaviour exists (PAR-HEAT-06/08/09, the forge's), its owner switches it to `DeviceYaml.writeTemplated` (`tfp:block/device`) and moves it into `configuration/devices/`; not part of this item."
tests:
  - "tools/datagen: FirepitTest, GrillTest, PotTest, ForgeDevicesTest green with unchanged generated output (or reviewed diff)"
  - "DeviceSpecParityTest turned into a check that the generators call the spec (or removed when the generators are the only writers)"
  - "VisualBudgetTest still green"
done_criteria:
  - "No device generator builds its CE block map by hand; all go through DeviceBlockSpec"
  - "Existing device generators use the shared spec with byte-identical or reviewed output"
  - "The firepit, grill, pot, charcoal forge and bellows entries carry TFC's hardness, resistance, sounds, mineable tool, loot and (firepit family) lit light 15; the ledger divergence rows for their placeholders are removed"
---

# PAR-INFRA-19: Device generators on the shared spec: Firepit, Grill, Pot and ForgeDevices write through DeviceSpecs + DeviceYaml

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
