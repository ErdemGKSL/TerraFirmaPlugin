---
id: PAR-FAUNA-32
title: "Pest infestation of large vessels and nest boxes (follow-up of PAR-FAUNA-13)"
area: FAUNA
status: open
wave: 46
deps: [PAR-FAUNA-13]
size: S
aliases: ["follow-up of PAR-FAUNA-13 (2026-11-25): the vessel and nest box screens are outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/container/LargeVesselContainer.java"
  - "src/main/java/net/dries007/tfc/common/container/NestBoxContainer.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/vessel/VesselScreens.java"
  - "paper/src/main/java/dev/tfp/paper/nestbox/NestBoxScreens.java"
  - "paper/src/test/java/dev/tfp/paper/critter/**"
ui: false
plan:
  - "PAR-FAUNA-13 infests chests on right click (CritterModule.infest(location, level, player), a service in ModuleServices). TFC also infests on PlayerContainerEvent.Open for the large vessel and nest box menus: the level is the food stacks in the container slots only (at most five), 0 when the enableInfestations option is off; LargeVesselContainer.canBeInfested is false while the vessel is sealed."
  - "Call it from the two screens' open paths with PestInfestation.openLevel(enabled, foodStacks)."
tests:
  - "paper: MockBukkit test that opening each screen rolls with the food count"
done_criteria:
  - "Opening a large vessel or nest box with food rolls an infestation at its block"
---

# PAR-FAUNA-32: Pest infestation of large vessels and nest boxes

> Claimed: worker session_011osUp8LArwstSDt7X82o4E, 2026-11-25

Follow `plans/parity/PROTOCOL.md`.
