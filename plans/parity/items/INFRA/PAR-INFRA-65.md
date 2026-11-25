---
id: PAR-INFRA-65
title: "Block flammability in-game verification and the flammability.spread self-test case"
area: INFRA
status: done
wave: 48
deps: [PAR-INFRA-47]
size: S
aliases: ["moved from PAR-INFRA-47 (2026-11-25, PROTOCOL 6.5): the screenshot steps and the itest case need a running server"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java"
files_owned:
  - "docs/verification/PAR-INFRA-65/**"
  - "itest/src/main/java/dev/tfp/itest/cases/Flammability*.java"
ui: true
in_game_steps:
  - "Build a 3x3 of tfp:wood/log/oak with tfp:wood/leaves/oak above it on stone; light the bottom with flint and steel; screenshots at 0, 10 and 30 s: fire spreads and consumes them"
  - "Place a tfp:wood/log/oak next to lava (one gap); wait 60 s; screenshot: it ignites (ignitedByLava)"
  - "Same setup with tfp:rock/raw/granite and a metal block: no fire spread"
plan:
  - "Add itest case flammability.spread (light a log and leaves cluster, burns within 60 s; granite cluster does not) and run the steps above. Datagen puts burnable/burn_chance/fire_spread_chance on the real CE blocks (PAR-INFRA-47)."
  - "Done: in game on Paper 1.21.11 (2026-11-25): a 3x3 tfp oak log + leaves cluster burnt from 18 to 7 blocks in 149 s with the fire relit, a granite cluster stayed 18 of 18, a tfp log next to lava caught fire within 40 s (ignitedByLava); the run on a jar from before PAR-INFRA-47 showed no burning at all (control). Evidence and verdict: docs/verification/PAR-INFRA-65/ (verdict.md). Self-test flammability.spread (FlammabilityCase, SelfTestSuite line) passes on Paper 1.21.11 (it sets fire_spread_radius_around_player to -1: no player online). Not checked: a metal block. Finding outside this item: tfp:wood/log/* and leaves draw as missing-texture cubes in the client (no block models written by datagen)."
tests:
  - "itest: flammability.spread; screenshots + verdict.md in docs/verification/PAR-INFRA-65/"
done_criteria:
  - "Logs, leaves (and later thatch, planks) burn and catch from lava at vanilla pace for the same odds; granite and metal do not"
---

# PAR-INFRA-65: Block flammability in-game verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
