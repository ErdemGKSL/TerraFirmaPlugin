---
id: PAR-FAUNA-09
title: "Prey AI and wild animals"
area: FAUNA
status: done
wave: 42
deps: [PAR-FAUNA-04, PAR-FAUNA-03]
size: M
aliases: ["M5-WILD-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/prey"
  - "src/main/java/net/dries007/tfc/common/entities/ai/prey"
files_owned:
  - "core/src/main/java/dev/tfp/core/prey/**"
  - "core/src/test/java/dev/tfp/core/prey/**"
  - "core/src/test/resources/dev/tfp/core/prey/**"
  - "paper/src/main/java/dev/tfp/paper/prey/**"
  - "paper/src/test/java/dev/tfp/paper/prey/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn deer adult"
  - "approach; record 5 frames"
plan:
  - "Flee goal driven by PreyFlee (sensing via nearby entity snapshots), graze/idle, ramming prey charge; winged prey flutter; fox/rabbit/armadillo/panda/frog behaviours as TFC."
  - "Done: PAR-FAUNA-09 commit. core/prey: PreyFlee (sighting memory without expiry, Avoidance forms per brain, getPosAway draw, run under 15 / stop beyond 20), RamRules + RamCharge (prepare 20 ticks, charge, damage and knockback, target conditions, cooldowns), PreyIdle, WildPrey (roster, attributes), SmallPrey (avoid goal, armadillo scare and damage). paper/prey: PreyModule with flee, panic, ram, swim, follow-adult, idle, look goals for the 16 prey species; fox/rabbit/panda vanilla-goal removals + land-predator avoid; armadillo scare, retreat, damage, no feeding/brushing; staggered periods, pure decisions on snapshots applied on the animal's thread. Winged prey flutter and take no fall damage through the chicken carrier (no core code needed). Frogs stay with PAR-FAUNA-12, pest feasting with PAR-FAUNA-13, ocelot with PAR-FAUNA-14; raids and scutes moved to PAR-FAUNA-24. Tests: PreyFleeGoldenTest, PreyFleeTest, RamRulesGoldenTest, WildPreyGoldenTest (core), PreyGoalsMockBukkitTest (14). Pending in-game check (container cannot run client)."
tests:
  - "core: PreyFleeGoldenTest exists"
  - "paper: goal transition"
done_criteria:
  - "A deer flees from a player in-game"
---

# PAR-FAUNA-09: Prey AI and wild animals

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
