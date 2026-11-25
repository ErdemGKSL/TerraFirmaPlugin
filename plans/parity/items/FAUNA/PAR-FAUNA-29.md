---
id: PAR-FAUNA-29
title: "Pack predator taming and temptation, predator sleep sound (follow-up of PAR-FAUNA-10)"
area: FAUNA
status: done
wave: 45
deps: [PAR-FAUNA-10, PAR-FAUNA-14]
size: S
aliases: ["follow-up of PAR-FAUNA-10 (2026-11-25): the dog conversion and the ambient sound are outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/ai/predator/PackPredator.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/predator/PackPredatorAi.java"
  - "src/main/java/net/dries007/tfc/common/entities/predator/Predator.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/predator/PackTaming*.java"
  - "core/src/test/java/dev/tfp/core/predator/PackTaming*.java"
  - "paper/src/main/java/dev/tfp/paper/predator/**"
  - "paper/src/test/java/dev/tfp/paper/predator/**"
  - "paper/src/main/java/dev/tfp/paper/animal/sound/AnimalSoundsModule.java"
  - "paper/src/test/java/dev/tfp/paper/animal/sound/**"
  - "docs/verification/PAR-FAUNA-29/**"
ui: true
in_game_steps:
  - "spawn a wild wolf; hold dog food (tfc:dog_food tag, not rotten) near it: it follows the player and does not attack for 400 ticks; feed it once a day for 10 days (tfp time add days 1 between feeds): it becomes a dog of the same gender"
  - "tfp time set noon near a sleeping bear: record the ambient sound (entity.bear.sleep)"
plan:
  - "PAR-FAUNA-10 ports the pack brain without TFC's temptation: PackPredatorAi.initHuntActivity's becomePassiveIf(TEMPTING_PLAYER, 400), FollowTemptation (1.2 adult / 1.5 baby) and the TEMPTATION_SENSOR (isFood: not rotten, tamable, tfc:dog_food)."
  - "PackPredator.mobInteract: a tamable pack predator fed dog food at most once per calendar day (nextFeed = ticks + TICKS_IN_DAY) gains 0.1 familiarity (clamped 0..1, saved); above 0.99 it converts to a dog (PAR-FAUNA-14's species) with its gender, adult unless it was a baby. Needs the dog species from PAR-FAUNA-14 (paper/pet)."
  - "Predator.getAmbientSound: while sleeping (PDC tfp:predator_sleeping, written by PredatorBrain) the ambient sound is entity.<sound>.sleep; AnimalSoundsModule plays the plain ambient for every animal today."
  - "Done: core PackTaming (PackTamingGoldenTest 6); PredatorModule.onFeed (daily feed, familiarity saved, conversion to a dog through AnimalService.spawn), PredatorGoals.Hunt temptation (sensor, passive 400, follow 1.2 / 1.5), AnimalSoundsModule sleep kind (predator and pet flags) with the predator sound id lookup; 4 predator and 2 sound MockBukkit tests; ledger row and 4 divergences. In-game screenshot and sound check pending (container cannot run client): steps in in_game_steps."
tests:
  - "core: PackTamingGoldenTest (feed interval, familiarity step and clamp, conversion threshold)"
  - "paper: MockBukkit: a tempted wolf turns passive for 400 ticks; ten daily feeds convert it; a sleeping predator's ambient is the sleep sound"
done_criteria:
  - "A wild wolf fed daily becomes a dog; a sleeping bear plays its sleep sound; screenshot and sound check reviewed"
---

# PAR-FAUNA-29: Pack predator taming and temptation, predator sleep sound

Follow `plans/parity/PROTOCOL.md`.
