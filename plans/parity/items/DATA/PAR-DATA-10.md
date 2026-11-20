---
id: PAR-DATA-10
title: "Dispenser behaviours, bonemeal, vanilla rule overrides"
area: DATA
status: done
wave: 38
deps: [PAR-INFRA-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/DispenserBehaviors.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/mixin/MilkBucketItemMixin.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/vanilla/**"
  - "core/src/test/java/dev/tfp/core/vanilla/**"
  - "core/src/test/resources/dev/tfp/core/vanilla/**"
  - "paper/src/main/java/dev/tfp/paper/vanilla/**"
  - "paper/src/test/java/dev/tfp/paper/vanilla/**"
ui: false
in_game_steps: []
plan:
  - "Dispensers: light devices, place fluids, shear as TFC (BlockDispenseEvent); bonemeal disabled unless enableVanillaBonemeal; vanilla monsters/golems/skeleton horses/chicken jockeys/egg throwing/milk clearing per config; extend the existing SuppressionListener."
  - "Done 2026-11-19: core VanillaRules (TFC defaults, tfp.vanilla.* JVM properties, TFC's monster spawn decision) and paper VanillaRulesModule: bone meal, egg throwing, skeleton trap horses, golems, natural monster spawns (underground only, tfc:monster_spawns_on), milk effect restore. Tests: VanillaRulesTest (3), VanillaRulesModuleMockBukkitTest (3: golems/horses, monsters, eggs). Not done, moved to PAR-DATA-18: mob equipment/enchantments, dispenser behaviours, bone meal and milk tests. config.yml keys are added with PAR-DATA-18 (docs/config.md)."
tests:
  - "paper: MockBukkit per rule"
done_criteria:
  - "Each config key verified by a test"
---

# PAR-DATA-10: Dispenser behaviours, bonemeal, vanilla rule overrides

> see_not_parity: NP-VIS-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
