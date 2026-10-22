---
id: PAR-PROC-02
title: "Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML"
area: PROC
status: open
wave: 6
deps: []
size: M
aliases: ["M3-GUI-02"]
tfc_refs:
  - "src/generated/resources/data/tfc/recipe/crafting"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crafting/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crafting/**"
  - "content/generated/content/craftengine/tfp/configuration/crafting/**"
ui: true
in_game_steps:
  - "Open a crafting table; craft a TFC firestarter from 2 sticks (or another sampled recipe); screenshot"
plan:
  - "Datagen converts the 1686 shaped + 569 shapeless TFC recipes to CE recipe YAML (tags -> CE tag ingredients; vanilla items kept); report the ones CE cannot express and route them to PAR-PROC-03."
tests:
  - "tools/datagen: count of emitted + routed = 2255"
done_criteria:
  - "CE loads the recipes with 0 issues; a sample of 10 crafts in-game"
---

# PAR-PROC-02: Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
