---
id: PAR-DATA-19
title: "Advancement triggers left from PAR-DATA-03: feature events for TFC's 21 own triggers"
area: DATA
status: open
wave: 40
deps: [PAR-DATA-03]
size: M
aliases: ["follow-up of PAR-DATA-03"]
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
  - "src/main/java/net/dries007/tfc/util/advancements/TFCAdvancements.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/advancement/criteria/**"
  - "paper/src/test/java/dev/tfp/paper/advancement/criteria/**"
  - "docs/advancements/criteria-map.md"
ui: false
in_game_steps: []
plan:
  - "The 21 criteria listed as pending in docs/advancements/criteria-map.md (basin_pour, firepit_created x3, lit x3 (forge, pit kiln, powderkeg), perfectly_forged, chiseled, rock_anvil, table_pour, present_day, max_windmill, hooked_entity x2, fed_animal, full_fertilizer, full_powderkeg, stab_entity, lava_lamp, rotten_compost_kill) need a small event published by the owning feature (anvil forging bonus, firepit/grill/pot/charcoal forge/pit kiln/powderkeg lighting, channel and mold table pours, chisel, rock anvil, windmill, fishing hook, livestock feeding, fertilizer, javelin stab at range, calendar year, lava lamp, compost). Add those events in the feature packages (their owners' files) and fire the criteria from CriteriaListener; move each criterion from CriteriaTable.PENDING to its own route."
tests:
  - "paper: one scripted event per criterion awards it"
done_criteria:
  - "No criterion of TFC's tree is PENDING in CriteriaMap"
---

# PAR-DATA-19: Advancement triggers left from PAR-DATA-03

> Claimed: session_011osUp9Lr5PbNofhx4XEc3S, 2026-11-25

Follow-up created by PAR-DATA-03. Follow `plans/parity/PROTOCOL.md`.
