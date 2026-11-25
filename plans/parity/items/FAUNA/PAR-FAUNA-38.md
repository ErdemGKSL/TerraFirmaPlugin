---
id: PAR-FAUNA-38
title: "Pest infestation when a large vessel is opened (follow-up of PAR-FAUNA-32)"
area: FAUNA
status: open
wave: 47
deps: [PAR-FAUNA-32, PAR-POT-06]
size: S
aliases: ["follow-up of PAR-FAUNA-32 (2026-11-25): the large vessel has no screen until PAR-POT-06; VesselScreens is the small vessel's item screen (not a TFC PestContainer)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/container/LargeVesselContainer.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/vessel/LargeVesselScreens.java"
  - "paper/src/test/java/dev/tfp/paper/critter/LargeVessel*"
ui: false
plan:
  - "When PAR-POT-06 adds the large vessel screen, call CritterModule.infest(blockLocation, PestInfestation.openLevel(true, foodStacks), viewer) from its open path, as NestBoxScreens.rollInfestation does (PAR-FAUNA-32): foodStacks counts the c:foods stacks in the nine slots, at most five. LargeVesselContainer.canBeInfested is false while the vessel is sealed: no roll then. Adjust files_owned to the class PAR-POT-06 creates."
tests:
  - "paper: MockBukkit test that opening an unsealed large vessel rolls with the food count and a sealed one does not"
done_criteria:
  - "Opening an unsealed large vessel with food rolls an infestation at its block"
---

# PAR-FAUNA-38: Pest infestation when a large vessel is opened

Follow `plans/parity/PROTOCOL.md`.
