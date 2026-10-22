---
id: PAR-INFRA-08
title: "Device datagen: shared CE templates for device blocks (states, loot self, hitbox carriers, entity_renderer)"
area: INFRA
status: done
wave: 2
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices"
  - "src/main/resources/assets/tfc/blockstates"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/**"
  - "content/generated/content/craftengine/tfp/configuration/templates/device*.yml"
ui: false
in_game_steps: []
plan:
  - "Current: Firepit.java, Grill.java, Pot.java and ForgeDevices.java each emit their own YAML; firepit/grill/pot sit outside `configuration/` so CE does not load them."
  - "Extract a `DeviceBlockSpec` (id, properties from TFCBlockStateProperties, appearance strategy: repurposed vanilla state / auto_state pool / entity_renderer, hitbox carrier, luminance per state, loot self, behaviour `tfp:device`)."
  - "Templates `tfp:block/device` and `tfp:block/device_rendered` in templates/device.yml; move firepit/grill/pot into `configuration/devices/` once their behaviour exists (do not load placeholder-only blocks earlier)."
  - "Note: `Firepit.java`, `Grill.java`, `Pot.java` and `ForgeDevices.java` are not in this item's files (PAR-INFRA-13 owns them). The shared spec reproduces their output byte for byte (`DeviceSpecParityTest`); the switch of the four generators to `DeviceSpecs` + `DeviceYaml.literal` is follow-up PAR-INFRA-19."
  - "Done: `tools/datagen/.../device/` DeviceProperty, DeviceAppearance (carrier: explicit state or auto_state pool; model or transparent; entity_renderer elements), DeviceVariant, DeviceBlockSpec (checks: every variant names every property, one variant per server state, appearances declared and used, render items declared, light 0..15), DeviceYaml (literal and templated forms), DeviceTemplates (`configuration/templates/device.yml`, one run line in Datagen), DeviceSpecs (firepit family, charcoal forge, facing device). Tests: DeviceYamlGoldenTest (9), DeviceTemplatesTest (2), DeviceSpecParityTest (1, real checkout); VisualBudgetTest green. Ledger: feature row + one divergence (rendered devices: client occlusion from the carrier)."
  - "Review fix (TFC fidelity): the spec no longer assumes self loot or stone/pickaxe settings. `DeviceLoot` (self, item, item × count, several pools, each `survives_explosion`; `fromTfc` reads TFC's loot table and rejects other shapes such as the crucible's copy_components) replaces `lootSelf`; `DeviceBlockSpec.Settings` carries hardness, resistance, `DeviceSound` (five events; `SoundType.NETHER_WART`/`WOOD`/`STONE`/`METAL`, `TFCSounds.X` -> `tfp:block.x.*`) and the mineable tool tag (`fromTfc`: TFCBlocks.java + generated mineable tags). The templates take `device_sound_*` and `device_tool_tag` and hold no loot (the entry writes it). `firepitFamily` takes TFC's settings, loot and lit light (15); `charcoalForge` and `facing` take TFC's settings and loot. `DeviceSpecParityTest` checks TFC's values (firepit/grill/pot 0.4 nether wart pickaxe; bellows 3.0 wood axe; forge 0.2 charcoal shovel, 7 charcoal) and that states, items and mappings equal today's generator output; the generators' placeholder settings, loot and light are pinned and recorded as ledger divergences (fixed by PAR-INFRA-19)."
  - "Done criterion not met here: the existing generators do not use the shared spec yet. Their files belong to PAR-INFRA-13 (which depends on this item), so per PROTOCOL §3(a) PAR-INFRA-19 carries the criterion word for word; this item claims only the shared spec, templates and tests."
tests:
  - "tools/datagen: spec -> YAML golden for one device per appearance strategy"
  - "VisualBudgetTest still green"
done_criteria:
  - "Existing device generators use the shared spec with byte-identical or reviewed output"
---

# PAR-INFRA-08: Device datagen: shared CE templates for device blocks (states, loot self, hitbox carriers, entity_renderer)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
