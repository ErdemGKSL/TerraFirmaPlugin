---
id: PAR-FAUNA-30
title: "Crocodile without the aggressive swim goal, diving in water (follow-up of PAR-FAUNA-12)"
area: FAUNA
status: open
wave: 46
deps: [PAR-FAUNA-10, PAR-FAUNA-12]
size: S
aliases: ["follow-up of PAR-FAUNA-12 (2026-11-25): PredatorModule is outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/ai/predator/AmphibiousPredatorAi.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java"
  - "src/main/java/net/dries007/tfc/common/entities/predator/AmphibiousPredator.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/predator/PredatorModule.java"
  - "paper/src/test/java/dev/tfp/paper/predator/PredatorCrocodile*"
  - "docs/verification/PAR-FAUNA-30/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn crocodile adult next to a 4-deep pond; push it in; wait 100 ticks; screenshot it under water"
plan:
  - "TFC's AmphibiousPredatorAi.initCoreActivity has no AggressiveSwim (PredatorAi's has AggressiveSwim(0.8F)); PredatorModule gives every predator PredatorGoals.Swim, so the crocodile keeps jumping to the surface."
  - "Skip the Swim goal for PredatorSpecies.Kind.AMPHIBIOUS. PAR-FAUNA-12's AmphibianModule already runs TFC's TrueAmphibiousMoveControl water step for the crocodile (AmphibiousMover, per-mob task) and keeps it from drowning; with the jump gone the crocodile dives and swims along its path."
tests:
  - "paper: MockBukkit: the crocodile's goals have no predator_swim; the wolf's still do"
done_criteria:
  - "A crocodile swims under water toward its target instead of bobbing at the surface; screenshot reviewed"
---

# PAR-FAUNA-30: Crocodile without the aggressive swim goal, diving in water

> Claimed: worker session_011osUp8LArwstSDt7X82o4E, 2026-11-25

Follow `plans/parity/PROTOCOL.md`.
