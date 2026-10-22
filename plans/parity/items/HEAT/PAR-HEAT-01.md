---
id: PAR-HEAT-01
title: "Item heat glow: hot items glow by heat level on vanilla clients"
area: HEAT
status: open
wave: 10
deps: []
size: M
aliases: ["M3-HEAT-01 (glow)", "gaps: UI defect 5 heat glow"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/heat/Heat.java"
  - "src/main/java/net/dries007/tfc/client/ClientEventHandler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/heat/HeatGlow*"
  - "paper/src/test/java/dev/tfp/paper/heat/HeatGlow*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/heat/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/heat/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 900"
  - "screenshot hand and inventory slot"
plan:
  - "Datagen wraps every heatable item's 1.21.4 item model definition in a `range_dispatch` on `custom_model_data` floats[0] with one tinted/emissive variant per heat level (craftengine-visuals-pack skill)."
  - "Outgoing item packets (the PAR-INFRA-05 pipeline or a sibling decoration) set custom_model_data floats[0] = level index from the lazy temperature; stripped on creative return."
  - "Level at send time only (TFC recolours every frame): divergence row."
tests:
  - "tools/datagen: every heatable item model has the dispatch"
  - "paper: NBT-level float written and stripped"
done_criteria:
  - "Screenshot: copper ingot at 900 C glows bright red in hand and in inventory"
---

# PAR-HEAT-01: Item heat glow: hot items glow by heat level on vanilla clients

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
