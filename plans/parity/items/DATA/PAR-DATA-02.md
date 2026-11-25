---
id: PAR-DATA-02
title: "Advancements: triggers, awards and presentation"
area: DATA
status: done
wave: 34
deps: [PAR-INFRA-01]
size: M
aliases: ["M6-ADV-01"]
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
  - "src/main/java/net/dries007/tfc/util/advancements/TFCAdvancements.java"
  - "src/main/java/net/dries007/tfc/util/advancements/GenericTrigger.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/advancement/**"
  - "core/src/test/java/dev/tfp/core/advancement/**"
  - "core/src/test/resources/dev/tfp/core/advancement/**"
  - "paper/src/main/java/dev/tfp/paper/advancement/**"
  - "paper/src/test/java/dev/tfp/paper/advancement/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/advancement/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/advancement/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Advancements.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/AdvancementsTest.java"
ui: true
in_game_steps:
  - "pick up a loose rock"
  - "screenshot toast; open advancements screen"
plan:
  - "Ship the advancements as datapack advancements with `minecraft:impossible` criteria granted by our trigger API (`Advancements.fire(player, criterionId)`), keeping TFC icons (CE items) and translations; vanilla toast shows them."
  - "Pending in-game check (container cannot run client)"
  - "Done: datagen `Advancements.writeDatapack` emits the 115 advancements into the bootstrap datapack (`tfp_seasonal_biomes/data/tfp/advancement/**`; impossible criteria, roots keep minecraft:tick; CE item_model icons, 20 unported items fall back to paper; 1.21.5+ background ids; translate+English fallback) and `craftengine/tfp/configuration/advancements.yml` (CE lang, 13 locales). Paper `advancement/{Advancements,AdvancementGrants,ItemCriteriaListener,AdvancementModule}`: `fire(player, \"<id>#<criterion>\")` on the entity scheduler, find_rock on PlayerInventorySlotChangeEvent with #tfp:rock_knapping; core `advancement/{CriterionRef,ItemCriteria}`. Tests: core AdvancementCoreTest 4, paper AdvancementsMockBukkitTest 6, datagen AdvancementsTest 12. Ledger feature row + 4 divergences. Toast screenshot pending (container cannot run client)."
tests:
  - "tools/datagen: tree emitted as datapack"
  - "paper: fire grants"
done_criteria:
  - "Picking up a loose rock grants the first story advancement (toast screenshot)"
---

# PAR-DATA-02: Advancements: triggers, awards and presentation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
