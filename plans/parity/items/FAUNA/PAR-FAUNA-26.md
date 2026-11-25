---
id: PAR-FAUNA-26
title: "Animal AI under load: livestock avoid and prey flee sensing within the tick budget; prey moveTo on Folia"
area: FAUNA
status: open
wave: 54
deps: [PAR-FAUNA-08, PAR-FAUNA-09]
size: M
aliases: ["follow-up of PAR-REL-06 (2026-11-25)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/livestock/LivestockAi.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/livestockai/**"
  - "paper/src/test/java/dev/tfp/paper/livestockai/**"
  - "paper/src/main/java/dev/tfp/paper/prey/**"
  - "paper/src/test/java/dev/tfp/paper/prey/**"
  - "core/src/main/java/dev/tfp/core/livestockai/**"
  - "core/src/test/java/dev/tfp/core/livestockai/**"
  - "core/src/main/java/dev/tfp/core/prey/**"
  - "core/src/test/java/dev/tfp/core/prey/**"
ui: false
in_game_steps: []
plan:
  - "Current: the itest husbandry.load_200 (PAR-REL-06, docs/perf/animals-200.md) measures the plugin's goals on 200 animals of 10 species in 3x3 chunks at 13.2 ms per tick mean (p99 32.6, max 52.9) on Paper 1.21.11, against PERF-TICK-01 2 ms and PERF-COMMIT-01 1.5 ms. By goal class (ms/tick): livestockai.LivestockGoals$Avoid 12.55, prey.PreyFleeGoal 1.64, prey.SmallPreyGoals$AvoidPredator 0.50, LivestockGoals$FollowAdult 0.30, LivestockGoals$Tempt 0.14."
  - "Cause to confirm with a profile: Avoid.wants() (every PERIOD = 5 ticks per animal) builds a Sighting for every living entity seen (LivestockSenses.sighting: PDC record read and tag lookups) before PreyFlee.trigger filters them, so a dense herd costs O(n^2) per period; PreyFleeGoal does the same for prey."
  - "Fix while keeping TFC's behaviour (PreyAi.broadcastAvoidTarget, sensing range and memory): filter by avoidance kind and distance before building sightings (cheap entity-type check first, record read only for candidates), cache each animal's own sighting per tick, share one nearby-entity scan per chunk section and tick, and spread the senses of a herd over the period (stagger by entity id). Decide async where the snapshot allows."
  - "Bug found by the same test on Folia 1.21.11 (2026-11-25): PaperPreyBody.moveTo(Mob, Entity, double) calls Pathfinder.moveTo(Entity, double), which Folia 1.21.11-14 does not have; the baby prey's FollowAdult goal throws NoSuchMethodError and Folia removes the entity ('Entity threw exception'). Fix as PaperLivestockBody does: `to instanceof LivingEntity living && mob.getPathfinder().moveTo(living, speed)`; check every Pathfinder/MobGoals call in paper/prey and paper/livestockai against the oldest supported API (1.21.4)."
  - "Done when husbandry.load_200 passes on Paper and Folia 1.21.11 (plugin slice per tick max <= 2 ms, p99 <= 1.5 ms) and the numbers are added to docs/perf/animals-200.md."
tests:
  - "itest husbandry.load_200 (exists)"
  - "paper: MockBukkit goal tests keep passing; a test that a herd of 50 builds at most one sighting per candidate per period"
done_criteria:
  - "husbandry.load_200 passes on Paper and Folia 1.21.11"
  - "No behaviour change against the PAR-FAUNA-08 / PAR-FAUNA-09 goldens"
---

# PAR-FAUNA-26: Animal AI under load: livestock avoid and prey flee sensing within the tick budget; prey moveTo on Folia

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
