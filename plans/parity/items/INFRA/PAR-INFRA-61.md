---
id: PAR-INFRA-61
title: "Static info tooltip lines in-game verification: damage type, resistances, fuel and rock lore (the check PAR-INFRA-46 closed without)"
area: INFRA
status: done
wave: 20
deps: [PAR-INFRA-46, PAR-TOOL-03]
size: S
aliases: ["parity audit 2026-11-17: PAR-INFRA-46 is done with ui: true and the criterion 'screenshots pass review', but its Done line says 'In-game check: docs/verification/PAR-INFRA-46/ when run' and that folder does not exist"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/PhysicalDamageType.java"
  - "src/main/java/net/dries007/tfc/util/data/Fuel.java"
  - "src/main/java/net/dries007/tfc/common/Lore.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockDisplayCategory.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "docs/verification/PAR-INFRA-46/**"
  - "docs/verification/PAR-INFRA-61/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/sword/copper; hover at GUI scale 2 and 3: 'Deals Slashing Damage' in TFC's colour, in TFC's line order; screenshot"
  - "ce item give Bot tfp:metal/chestplate/copper; hover: 'Resistances: Slashing x, Piercing y, Crushing z' with TFC's percentages; screenshot"
  - "give Bot minecraft:charcoal 4; ce item give Bot tfp:peat; hover each: 'Burns at <heat colour text> for <time delta>' (charcoal 1350 C for 1800 ticks); screenshot"
  - "ce item give Bot tfp:rock/raw/granite and tfp:rock/bricks/chalk; hover: grey italic rock display category ('Felsic Igneous Intrusive' / 'Sedimentary') directly under the name; screenshot"
plan:
  - "Run PAR-INFRA-46's in-game steps (copied above) on Paper 1.21.11 with the headless client (docs/testing/headless.md), CraftEngine self-host on 127.0.0.1. Opus judges every crop against TFC's text, colour and order (ClientForgeEventHandler tooltip order: lore under the name, damage after size, fuel after heat). Write verdict.md in docs/verification/PAR-INFRA-61/ and copy the crops to docs/verification/PAR-INFRA-46/ so that item's evidence path resolves."
  - "PAR-TOOL-03 is a dep because the metal sword and chestplate must load in game; if PAR-TOOL-03 is still open when this item is picked, use any loaded tfp item carrying tfc:deals_*_damage and an armour with item_damage_resistance data and note the substitution."
  - "A failure is fixed through a follow-up item owning paper/tooltip/info/** (do not reopen PAR-INFRA-46 unless its golden rows are wrong)."
  - "Done: in-game check run 2026-11-25 on Paper 1.21.11 with the headless client (GUI scale 2 and 3): damage type, resistances, fuel (charcoal 1:48, peat 2:30) and rock lore lines show TFC text, colour and position. Evidence and verdict: docs/verification/PAR-INFRA-61/ (copied to docs/verification/PAR-INFRA-46/). No code change."
tests:
  - "none (in-game check only)"
done_criteria:
  - "Each of the four tooltip families shows TFC's text, colour and position at GUI scale 2 and 3"
  - "verdict.md with HEAD commit, versions, steps and pass/fail per criterion in docs/verification/PAR-INFRA-61/"
---

# PAR-INFRA-61: Static info tooltip lines in-game verification

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
