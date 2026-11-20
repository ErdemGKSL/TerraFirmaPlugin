---
id: PAR-DATA-18
title: "Vanilla rules left from PAR-DATA-10: mob equipment and enchantments, dispenser behaviours, bone meal and milk tests"
area: DATA
status: open
wave: 39
deps: [PAR-DATA-10]
size: M
aliases: ["follow-up of PAR-DATA-10"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/DispenserBehaviors.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/vanilla/**"
  - "core/src/test/java/dev/tfp/core/vanilla/**"
  - "paper/src/main/java/dev/tfp/paper/vanilla/**"
  - "paper/src/test/java/dev/tfp/paper/vanilla/**"
ui: false
in_game_steps: []
plan:
  - "enableVanillaMobsSpawningWithEnchantments / WithVanillaEquipment (TFC onLivingSpawnCheck + skeleton weapons tag); dispenser behaviours (light devices, place fluids, shear) once the devices exist; MockBukkit tests for the bone meal cancel (a BlockFertilizeEvent with a player) and the milk effect restore."
tests:
  - "paper: MockBukkit per rule"
done_criteria:
  - "Every vanilla-changes key of TFC's ServerConfig is implemented or listed as a divergence, each with a test"
---

# PAR-DATA-18: Vanilla rules left from PAR-DATA-10

Follow-up created by PAR-DATA-10. Follow `plans/parity/PROTOCOL.md`.
