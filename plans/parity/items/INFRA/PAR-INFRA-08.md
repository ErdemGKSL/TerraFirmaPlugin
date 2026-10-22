---
id: PAR-INFRA-08
title: "Device datagen: shared CE templates for device blocks (states, loot self, hitbox carriers, entity_renderer)"
area: INFRA
status: open
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
tests:
  - "tools/datagen: spec -> YAML golden for one device per appearance strategy"
  - "VisualBudgetTest still green"
done_criteria:
  - "Existing device generators use the shared spec with byte-identical or reviewed output"
---

# PAR-INFRA-08: Device datagen: shared CE templates for device blocks (states, loot self, hitbox carriers, entity_renderer)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
